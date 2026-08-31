package rew.lightgames.sudoku2

import java.util.Collections
import java.util.concurrent.atomic.AtomicLong

/** Stable adapter for the legacy string extra emitted by [LevelSelect]. */
object GameplayDifficultyAdapter {
    const val INTENT_EXTRA = "difficulty"
    const val EASY_VALUE = "easy"
    const val MEDIUM_VALUE = "medium"
    const val HARD_VALUE = "hard"

    fun fromExternalValue(value: String?): GameplayDifficultyMapping = when (value) {
        EASY_VALUE -> GameplayDifficultyMapping.Valid(SudokuDifficulty.EASY)
        MEDIUM_VALUE -> GameplayDifficultyMapping.Valid(SudokuDifficulty.MEDIUM)
        HARD_VALUE -> GameplayDifficultyMapping.Valid(SudokuDifficulty.HARD)
        else -> GameplayDifficultyMapping.Invalid(value)
    }

    fun toExternalValue(difficulty: SudokuDifficulty): String = when (difficulty) {
        SudokuDifficulty.EASY -> EASY_VALUE
        SudokuDifficulty.MEDIUM -> MEDIUM_VALUE
        SudokuDifficulty.HARD -> HARD_VALUE
        SudokuDifficulty.UNSUPPORTED -> throw IllegalArgumentException(
            "UNSUPPORTED has no gameplay difficulty value"
        )
    }
}

sealed interface GameplayDifficultyMapping {
    data class Valid(val difficulty: SudokuDifficulty) : GameplayDifficultyMapping
    data class Invalid(val value: String?) : GameplayDifficultyMapping
}

enum class GameplayLaunchMode {
    NEW_GAME,
    RESTORE_PERSISTED_GAME,
    RECREATED_WITHOUT_ACTIVE_GAME
}

/** Keeps process recreation from mistaking an older preference entry for the current game. */
object GameplayLaunchPolicy {
    fun mode(
        explicitResume: Boolean,
        activityRecreation: Boolean,
        savedActiveGame: Boolean
    ): GameplayLaunchMode = when {
        explicitResume -> GameplayLaunchMode.RESTORE_PERSISTED_GAME
        !activityRecreation -> GameplayLaunchMode.NEW_GAME
        savedActiveGame -> GameplayLaunchMode.RESTORE_PERSISTED_GAME
        else -> GameplayLaunchMode.RECREATED_WITHOUT_ACTIVE_GAME
    }
}

fun interface GameplaySeedSource {
    fun nextSeed(): Long
}

/** Local variation for ordinary new games. This seed is not a security or daily-puzzle token. */
class ProductionGameplaySeedSource internal constructor(
    private val clockMillis: () -> Long,
    initialCounter: Long
) : GameplaySeedSource {
    constructor() : this(System::currentTimeMillis, System.nanoTime())

    private val counter = AtomicLong(initialCounter)

    override fun nextSeed(): Long {
        var value = clockMillis() xor counter.getAndIncrement()
        value = (value xor (value ushr 30)) * MIX_MULTIPLIER_1
        value = (value xor (value ushr 27)) * MIX_MULTIPLIER_2
        return value xor (value ushr 31)
    }

    private companion object {
        const val MIX_MULTIPLIER_1 = -4658895280553007687L
        const val MIX_MULTIPLIER_2 = -7723592293110705685L
    }
}

enum class GameplayPuzzleSource {
    GENERATED,
    FALLBACK,
    RESUMED
}

enum class GameplayPuzzleFailureReason {
    INVALID_DIFFICULTY,
    RESUME_UNAVAILABLE,
    PUZZLE_LOAD_FAILED,
    FALLBACK_MISSING,
    FALLBACK_INVALID
}

sealed interface PuzzleLoadResult {
    data class Ready(
        val board: SudokuBoard,
        val requestedDifficulty: SudokuDifficulty,
        val actualRating: DifficultyRating,
        val source: GameplayPuzzleSource,
        val seed: Long
    ) : PuzzleLoadResult

    data class Failure(
        val requestedDifficulty: SudokuDifficulty,
        val seed: Long,
        val reason: GameplayPuzzleFailureReason,
        val targetFailureReason: TargetGenerationFailureReason?
    ) : PuzzleLoadResult
}

fun interface GameplayPuzzleLoader {
    fun createPuzzle(difficulty: SudokuDifficulty, seed: Long): PuzzleLoadResult
}

fun interface TargetPuzzleGeneration {
    fun generate(seed: Long, difficulty: SudokuDifficulty): TargetGenerationResult
}

/** Runtime fallback record with defensive array access. */
class GameplayFallbackPuzzle(
    val declaredDifficulty: SudokuDifficulty,
    puzzle: IntArray
) {
    private val puzzleState = puzzle.clone()

    val puzzle: IntArray
        get() = puzzleState.clone()

    init {
        require(declaredDifficulty != SudokuDifficulty.UNSUPPORTED) {
            "Fallback difficulty must be playable"
        }
        require(puzzleState.size == BOARD_CELL_COUNT) { "Fallback puzzle must contain 81 cells" }
        require(puzzleState.all { it in 0..9 }) { "Fallback puzzle contains an invalid value" }
    }

    override fun equals(other: Any?): Boolean =
        other is GameplayFallbackPuzzle &&
            declaredDifficulty == other.declaredDifficulty &&
            puzzleState.contentEquals(other.puzzleState)

    override fun hashCode(): Int =
        31 * declaredDifficulty.hashCode() + puzzleState.contentHashCode()

    private companion object {
        const val BOARD_CELL_COUNT = 81
    }
}

fun interface GameplayFallbackSource {
    fun select(difficulty: SudokuDifficulty, seed: Long): GameplayFallbackPuzzle?
}

/** Parses and selects from the compact, versioned bundled fallback asset. */
class CompactFallbackPuzzleProvider(
    private val contentLoader: () -> String
) : GameplayFallbackSource {
    private val puzzles: List<GameplayFallbackPuzzle> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        parse(contentLoader())
    }

    override fun select(
        difficulty: SudokuDifficulty,
        seed: Long
    ): GameplayFallbackPuzzle? {
        val matching = puzzles.filter { it.declaredDifficulty == difficulty }
        if (matching.isEmpty()) return null
        val mixed = mixSelectionSeed(seed, difficulty)
        val index = java.lang.Long.remainderUnsigned(mixed, matching.size.toLong()).toInt()
        return matching[index]
    }

    internal fun allPuzzles(): List<GameplayFallbackPuzzle> =
        Collections.unmodifiableList(ArrayList(puzzles))

    private fun parse(content: String): List<GameplayFallbackPuzzle> {
        val parsed = content.lineSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith(COMMENT_PREFIX) }
            .mapIndexed { index, line -> parseLine(index + 1, line) }
            .toList()
        require(parsed.isNotEmpty()) { "Fallback catalog must not be empty" }
        return Collections.unmodifiableList(ArrayList(parsed))
    }

    private fun parseLine(lineNumber: Int, line: String): GameplayFallbackPuzzle {
        val fields = line.split(',', limit = 2)
        require(fields.size == 2) { "Fallback line $lineNumber must contain difficulty,puzzle" }
        val difficulty = try {
            SudokuDifficulty.valueOf(fields[0])
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("Fallback line $lineNumber has an unknown difficulty")
        }
        require(difficulty != SudokuDifficulty.UNSUPPORTED) {
            "Fallback line $lineNumber cannot declare UNSUPPORTED"
        }
        val encoded = fields[1]
        require(encoded.length == BOARD_CELL_COUNT && encoded.all { it in '0'..'9' }) {
            "Fallback line $lineNumber must contain exactly 81 digits"
        }
        return GameplayFallbackPuzzle(
            declaredDifficulty = difficulty,
            puzzle = IntArray(BOARD_CELL_COUNT) { encoded[it].digitToInt() }
        )
    }

    private fun mixSelectionSeed(seed: Long, difficulty: SudokuDifficulty): Long {
        var value = seed xor (difficulty.ordinal + 1L) * DIFFICULTY_SALT
        value = (value xor (value ushr 30)) * MIX_MULTIPLIER_1
        value = (value xor (value ushr 27)) * MIX_MULTIPLIER_2
        return value xor (value ushr 31)
    }

    private companion object {
        const val BOARD_CELL_COUNT = 81
        const val COMMENT_PREFIX = "#"
        const val DIFFICULTY_SALT = -7046029254386353131L
        const val MIX_MULTIPLIER_1 = -4658895280553007687L
        const val MIX_MULTIPLIER_2 = -7723592293110705685L
    }
}

/** Converts validated flat engine output into the unchanged gameplay board model. */
object GameplaySudokuBoardAdapter {
    fun fromTargetedPuzzle(targetedPuzzle: TargetedPuzzle): SudokuBoard = fromArrays(
        puzzle = targetedPuzzle.puzzle,
        solution = targetedPuzzle.solution
    )

    internal fun fromArrays(puzzle: IntArray, solution: IntArray): SudokuBoard {
        require(puzzle.size == BOARD_CELL_COUNT) { "Puzzle must contain 81 cells" }
        require(solution.size == BOARD_CELL_COUNT) { "Solution must contain 81 cells" }
        require(solution.all { it in 1..9 }) { "Solution must be complete" }
        require(puzzle.indices.all { puzzle[it] == 0 || puzzle[it] == solution[it] }) {
            "Every given must agree with the solution"
        }

        val solutionGrid = Array(BOARD_SIZE) { row ->
            IntArray(BOARD_SIZE) { column -> solution[row * BOARD_SIZE + column] }
        }
        val cells = Array(BOARD_SIZE) { row ->
            Array(BOARD_SIZE) { column ->
                val clue = puzzle[row * BOARD_SIZE + column]
                Cell(number = clue, original_number = clue)
            }
        }
        return SudokuBoard(cells = cells, solution = solutionGrid)
    }

    private const val BOARD_SIZE = 9
    private const val BOARD_CELL_COUNT = BOARD_SIZE * BOARD_SIZE
}

/** Generated target first; independently revalidated same-grade fallback second. */
class GameplayPuzzleProvider(
    private val fallbackSource: GameplayFallbackSource,
    private val targetGeneration: TargetPuzzleGeneration = defaultTargetGeneration()
) : GameplayPuzzleLoader {
    private val validationEngine = SudokuPuzzleEngine(VALIDATION_SEED)
    private val logicalSolver = SudokuLogicalSolver()
    private val difficultyGrader = SudokuDifficultyGrader()

    override fun createPuzzle(
        difficulty: SudokuDifficulty,
        seed: Long
    ): PuzzleLoadResult {
        require(difficulty != SudokuDifficulty.UNSUPPORTED) {
            "UNSUPPORTED cannot be requested for gameplay"
        }

        val targetResult = try {
            targetGeneration.generate(seed, difficulty)
        } catch (_: RuntimeException) {
            null
        }
        if (targetResult is TargetGenerationResult.Success) {
            val targeted = targetResult.targetedPuzzle
            validateGeneratedTarget(targeted, difficulty, seed)?.let { return it }
        }

        val targetFailureReason = (targetResult as? TargetGenerationResult.Failure)
            ?.failure
            ?.reason
        val fallback = try {
            fallbackSource.select(difficulty, seed)
        } catch (_: RuntimeException) {
            null
        } ?: return PuzzleLoadResult.Failure(
            requestedDifficulty = difficulty,
            seed = seed,
            reason = GameplayPuzzleFailureReason.FALLBACK_MISSING,
            targetFailureReason = targetFailureReason
        )

        if (fallback.declaredDifficulty != difficulty) {
            return PuzzleLoadResult.Failure(
                requestedDifficulty = difficulty,
                seed = seed,
                reason = GameplayPuzzleFailureReason.FALLBACK_INVALID,
                targetFailureReason = targetFailureReason
            )
        }
        val puzzle = fallback.puzzle
        val solution = validationEngine.solve(puzzle)
        if (solution == null || validationEngine.countSolutions(puzzle, 2) != 1) {
            return invalidFallback(difficulty, seed, targetFailureReason)
        }
        val logicalResult = logicalSolver.solve(puzzle)
        val rating = try {
            difficultyGrader.grade(puzzle, logicalResult)
        } catch (_: IllegalArgumentException) {
            return invalidFallback(difficulty, seed, targetFailureReason)
        }
        if (
            logicalResult.status != LogicalSolveStatus.SOLVED ||
            rating.difficulty != difficulty
        ) {
            return invalidFallback(difficulty, seed, targetFailureReason)
        }

        return PuzzleLoadResult.Ready(
            board = GameplaySudokuBoardAdapter.fromArrays(puzzle, solution),
            requestedDifficulty = difficulty,
            actualRating = rating,
            source = GameplayPuzzleSource.FALLBACK,
            seed = seed
        )
    }

    private fun validateGeneratedTarget(
        targeted: TargetedPuzzle,
        requestedDifficulty: SudokuDifficulty,
        seed: Long
    ): PuzzleLoadResult.Ready? {
        if (
            targeted.requestedDifficulty != requestedDifficulty ||
            targeted.rating.difficulty != requestedDifficulty ||
            targeted.engineVersion != PuzzleEngineVersions.CURRENT
        ) {
            return null
        }
        val puzzle = targeted.puzzle
        val solution = targeted.solution
        val solved = validationEngine.solve(puzzle) ?: return null
        if (!solved.contentEquals(solution) || validationEngine.countSolutions(puzzle, 2) != 1) {
            return null
        }
        val logicalResult = logicalSolver.solve(puzzle)
        if (logicalResult.status != LogicalSolveStatus.SOLVED) return null
        val rating = try {
            difficultyGrader.grade(puzzle, logicalResult)
        } catch (_: IllegalArgumentException) {
            return null
        }
        if (rating != targeted.rating || rating.difficulty != requestedDifficulty) return null

        val board = try {
            GameplaySudokuBoardAdapter.fromArrays(puzzle, solution)
        } catch (_: IllegalArgumentException) {
            return null
        }
        return PuzzleLoadResult.Ready(
            board = board,
            requestedDifficulty = requestedDifficulty,
            actualRating = rating,
            source = GameplayPuzzleSource.GENERATED,
            seed = seed
        )
    }

    private fun invalidFallback(
        difficulty: SudokuDifficulty,
        seed: Long,
        targetFailureReason: TargetGenerationFailureReason?
    ): PuzzleLoadResult.Failure = PuzzleLoadResult.Failure(
        requestedDifficulty = difficulty,
        seed = seed,
        reason = GameplayPuzzleFailureReason.FALLBACK_INVALID,
        targetFailureReason = targetFailureReason
    )

    private companion object {
        const val VALIDATION_SEED = 0L

        fun defaultTargetGeneration(): TargetPuzzleGeneration {
            val generator = DifficultyTargetGenerator()
            return TargetPuzzleGeneration(generator::generate)
        }
    }
}

internal const val GRADED_FALLBACK_ASSET = "graded_fallbacks_v1.csv"
