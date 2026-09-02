package rew.lightgames.sudoku2

import kotlin.math.abs

/** Version of the existing seeded puzzle-generation algorithm. */
object SudokuGeneratorVersion {
    const val CURRENT = 1
}

/** Version of deterministic target-difficulty selection and clue restoration. */
object DifficultyTargetingVersion {
    const val CURRENT = 1
}

/**
 * Complete version identity for a targeted puzzle.
 *
 * These values are independent of the Android application version. The generator version must
 * change for RNG, completed-grid, or clue-removal changes. The targeting version must change for
 * attempt-seed derivation, retry budgets, or clue-restoration-policy changes. Solver and grader
 * version policies are documented by [LogicalSolverVersion] and [DifficultyGraderVersion].
 */
data class PuzzleEngineVersion(
    val generator: Int,
    val logicalSolver: Int,
    val grader: Int,
    val targeting: Int
) {
    init {
        require(generator > 0) { "Generator version must be positive" }
        require(logicalSolver > 0) { "Logical-solver version must be positive" }
        require(grader > 0) { "Grader version must be positive" }
        require(targeting > 0) { "Targeting version must be positive" }
    }
}

object PuzzleEngineVersions {
    val CURRENT = PuzzleEngineVersion(
        generator = SudokuGeneratorVersion.CURRENT,
        logicalSolver = LogicalSolverVersion.CURRENT,
        grader = DifficultyGraderVersion.CURRENT,
        targeting = DifficultyTargetingVersion.CURRENT
    )
}

/** A successfully generated puzzle. Array values are copied on input and access. */
class TargetedPuzzle(
    puzzle: IntArray,
    solution: IntArray,
    val requestedDifficulty: SudokuDifficulty,
    val rating: DifficultyRating,
    val baseSeed: Long,
    val attemptSeed: Long,
    /** Zero-based raw-candidate attempt index. */
    val attemptIndex: Int,
    val cluesRestored: Int,
    val engineVersion: PuzzleEngineVersion
) {
    private val puzzleState = puzzle.clone()
    private val solutionState = solution.clone()

    val puzzle: IntArray
        get() = puzzleState.clone()

    val solution: IntArray
        get() = solutionState.clone()

    init {
        require(puzzleState.size == BOARD_CELL_COUNT) { "Puzzle must contain 81 cells" }
        require(solutionState.size == BOARD_CELL_COUNT) { "Solution must contain 81 cells" }
        require(requestedDifficulty != SudokuDifficulty.UNSUPPORTED) {
            "UNSUPPORTED is not a target difficulty"
        }
        require(rating.difficulty == requestedDifficulty) {
            "Rating must exactly match the requested difficulty"
        }
        require(attemptIndex >= 0) { "Attempt index must not be negative" }
        require(cluesRestored >= 0) { "Restored clue count must not be negative" }
    }

    override fun equals(other: Any?): Boolean =
        other is TargetedPuzzle &&
            puzzleState.contentEquals(other.puzzleState) &&
            solutionState.contentEquals(other.solutionState) &&
            requestedDifficulty == other.requestedDifficulty &&
            rating == other.rating &&
            baseSeed == other.baseSeed &&
            attemptSeed == other.attemptSeed &&
            attemptIndex == other.attemptIndex &&
            cluesRestored == other.cluesRestored &&
            engineVersion == other.engineVersion

    override fun hashCode(): Int {
        var result = puzzleState.contentHashCode()
        result = 31 * result + solutionState.contentHashCode()
        result = 31 * result + requestedDifficulty.hashCode()
        result = 31 * result + rating.hashCode()
        result = 31 * result + baseSeed.hashCode()
        result = 31 * result + attemptSeed.hashCode()
        result = 31 * result + attemptIndex
        result = 31 * result + cluesRestored
        result = 31 * result + engineVersion.hashCode()
        return result
    }

    override fun toString(): String =
        "TargetedPuzzle(requestedDifficulty=$requestedDifficulty, rating=$rating, " +
            "baseSeed=$baseSeed, attemptSeed=$attemptSeed, attemptIndex=$attemptIndex, " +
            "cluesRestored=$cluesRestored, engineVersion=$engineVersion)"

    private companion object {
        const val BOARD_CELL_COUNT = 81
    }
}

enum class TargetGenerationFailureReason {
    UNSUPPORTED_REQUEST,
    ATTEMPT_BUDGET_EXHAUSTED,
    INTERNAL_GENERATION_DEFECT,
    INTERNAL_LOGICAL_DEFECT,
    INTERNAL_VALIDATION_DEFECT
}

/** Deterministic diagnostics for an explicit target-generation failure. */
data class TargetGenerationFailure(
    val requestedDifficulty: SudokuDifficulty,
    val baseSeed: Long,
    val attemptsUsed: Int,
    val bestObservedRating: DifficultyRating?,
    val engineVersion: PuzzleEngineVersion,
    val reason: TargetGenerationFailureReason,
    val ratingsEvaluated: Int,
    val cluesRestored: Int,
    val overshootCount: Int,
    val restorationLimitHits: Int
) {
    init {
        require(attemptsUsed >= 0) { "Attempts used must not be negative" }
        require(ratingsEvaluated >= 0) { "Ratings evaluated must not be negative" }
        require(cluesRestored >= 0) { "Restored clue count must not be negative" }
        require(overshootCount >= 0) { "Overshoot count must not be negative" }
        require(restorationLimitHits >= 0) {
            "Restoration-limit hit count must not be negative"
        }
    }
}

sealed interface TargetGenerationResult {
    data class Success(val targetedPuzzle: TargetedPuzzle) : TargetGenerationResult
    data class Failure(val failure: TargetGenerationFailure) : TargetGenerationResult
}

/** Pure entry point for deterministic, bounded target-difficulty generation. */
class DifficultyTargetGenerator {
    private val policy: TargetGenerationPolicy
    private val puzzleFactory: (Long) -> GeneratedPuzzle

    constructor() {
        policy = TargetGenerationPolicy.V1
        puzzleFactory = { attemptSeed -> SudokuPuzzleEngine(attemptSeed).generate() }
    }

    internal constructor(
        policy: TargetGenerationPolicy,
        puzzleFactory: (Long) -> GeneratedPuzzle = { attemptSeed ->
            SudokuPuzzleEngine(attemptSeed).generate()
        }
    ) {
        this.policy = policy
        this.puzzleFactory = puzzleFactory
    }

    fun generate(baseSeed: Long, difficulty: SudokuDifficulty): TargetGenerationResult =
        generate(baseSeed, difficulty, NO_OBSERVER)

    internal fun generate(
        baseSeed: Long,
        difficulty: SudokuDifficulty,
        observer: (TargetGenerationObservation) -> Unit
    ): TargetGenerationResult {
        val version = PuzzleEngineVersions.CURRENT
        if (difficulty == SudokuDifficulty.UNSUPPORTED) {
            return failure(
                requestedDifficulty = difficulty,
                baseSeed = baseSeed,
                attemptsUsed = 0,
                bestObservedRating = null,
                reason = TargetGenerationFailureReason.UNSUPPORTED_REQUEST,
                counters = GenerationCounters(),
                version = version
            )
        }

        val solver = SudokuLogicalSolver()
        val grader = SudokuDifficultyGrader()
        val validationEngine = SudokuPuzzleEngine(VALIDATION_ENGINE_SEED)
        val counters = GenerationCounters()
        var bestObservedRating: DifficultyRating? = null
        val attemptBudget = policy.attemptBudget(difficulty)

        for (attemptIndex in 0 until attemptBudget) {
            val attemptSeed = TargetingSeedDerivationV1.attemptSeed(
                baseSeed = baseSeed,
                difficulty = difficulty,
                engineVersion = version,
                attemptIndex = attemptIndex
            )
            val generated = try {
                puzzleFactory(attemptSeed)
            } catch (_: RuntimeException) {
                return failure(
                    requestedDifficulty = difficulty,
                    baseSeed = baseSeed,
                    attemptsUsed = attemptIndex + 1,
                    bestObservedRating = bestObservedRating,
                    reason = TargetGenerationFailureReason.INTERNAL_GENERATION_DEFECT,
                    counters = counters,
                    version = version
                )
            }
            val puzzle = generated.puzzle.clone()
            val solution = generated.solution.clone()
            if (
                generated.seed != attemptSeed ||
                !isValidGeneratedPuzzle(puzzle, solution, validationEngine)
            ) {
                return failure(
                    requestedDifficulty = difficulty,
                    baseSeed = baseSeed,
                    attemptsUsed = attemptIndex + 1,
                    bestObservedRating = bestObservedRating,
                    reason = TargetGenerationFailureReason.INTERNAL_GENERATION_DEFECT,
                    counters = counters,
                    version = version
                )
            }

            val rawEvaluation = evaluate(puzzle, solver, grader)
            counters.ratingsEvaluated++
            bestObservedRating = betterRating(
                current = bestObservedRating,
                candidate = rawEvaluation.rating,
                requestedDifficulty = difficulty
            )
            observer(
                TargetGenerationObservation(
                    phase = TargetGenerationPhase.RAW,
                    attemptIndex = attemptIndex,
                    attemptSeed = attemptSeed,
                    cluesRestored = 0,
                    logicalStatus = rawEvaluation.status,
                    rating = rawEvaluation.rating
                )
            )
            if (rawEvaluation.status == LogicalSolveStatus.INVALID) {
                return failure(
                    requestedDifficulty = difficulty,
                    baseSeed = baseSeed,
                    attemptsUsed = attemptIndex + 1,
                    bestObservedRating = bestObservedRating,
                    reason = TargetGenerationFailureReason.INTERNAL_LOGICAL_DEFECT,
                    counters = counters,
                    version = version
                )
            }

            when (compareDifficulty(rawEvaluation.rating.difficulty, difficulty)) {
                0 -> return verifiedSuccess(
                    puzzle = puzzle,
                    solution = solution,
                    requestedDifficulty = difficulty,
                    expectedRating = rawEvaluation.rating,
                    baseSeed = baseSeed,
                    attemptSeed = attemptSeed,
                    attemptIndex = attemptIndex,
                    cluesRestored = 0,
                    attemptsUsed = attemptIndex + 1,
                    bestObservedRating = bestObservedRating,
                    counters = counters,
                    version = version
                )
                -1 -> continue
            }

            val restorationOrder = puzzle.indices.filter { puzzle[it] == 0 }.toIntArray()
            TargetingSeedDerivationV1.shuffleForRestoration(restorationOrder, attemptSeed)
            val restorationsAllowed = minOf(policy.maxClueRestorations, restorationOrder.size)
            var stillAboveTarget = true

            for (restorationOffset in 0 until restorationsAllowed) {
                val cell = restorationOrder[restorationOffset]
                puzzle[cell] = solution[cell]
                counters.cluesRestored++
                val cluesRestoredThisAttempt = restorationOffset + 1
                val restoredEvaluation = evaluate(puzzle, solver, grader)
                counters.ratingsEvaluated++
                bestObservedRating = betterRating(
                    current = bestObservedRating,
                    candidate = restoredEvaluation.rating,
                    requestedDifficulty = difficulty
                )
                observer(
                    TargetGenerationObservation(
                        phase = TargetGenerationPhase.RESTORATION,
                        attemptIndex = attemptIndex,
                        attemptSeed = attemptSeed,
                        cluesRestored = cluesRestoredThisAttempt,
                        logicalStatus = restoredEvaluation.status,
                        rating = restoredEvaluation.rating
                    )
                )
                if (restoredEvaluation.status == LogicalSolveStatus.INVALID) {
                    return failure(
                        requestedDifficulty = difficulty,
                        baseSeed = baseSeed,
                        attemptsUsed = attemptIndex + 1,
                        bestObservedRating = bestObservedRating,
                        reason = TargetGenerationFailureReason.INTERNAL_LOGICAL_DEFECT,
                        counters = counters,
                        version = version
                    )
                }

                when (compareDifficulty(restoredEvaluation.rating.difficulty, difficulty)) {
                    0 -> return verifiedSuccess(
                        puzzle = puzzle,
                        solution = solution,
                        requestedDifficulty = difficulty,
                        expectedRating = restoredEvaluation.rating,
                        baseSeed = baseSeed,
                        attemptSeed = attemptSeed,
                        attemptIndex = attemptIndex,
                        cluesRestored = cluesRestoredThisAttempt,
                        attemptsUsed = attemptIndex + 1,
                        bestObservedRating = bestObservedRating,
                        counters = counters,
                        version = version
                    )
                    -1 -> {
                        counters.overshootCount++
                        stillAboveTarget = false
                        break
                    }
                }
            }
            if (
                stillAboveTarget &&
                restorationsAllowed == policy.maxClueRestorations &&
                restorationOrder.size > policy.maxClueRestorations
            ) {
                counters.restorationLimitHits++
            }
        }

        return failure(
            requestedDifficulty = difficulty,
            baseSeed = baseSeed,
            attemptsUsed = attemptBudget,
            bestObservedRating = bestObservedRating,
            reason = TargetGenerationFailureReason.ATTEMPT_BUDGET_EXHAUSTED,
            counters = counters,
            version = version
        )
    }

    private fun evaluate(
        puzzle: IntArray,
        solver: SudokuLogicalSolver,
        grader: SudokuDifficultyGrader
    ): EvaluatedPuzzle {
        val solveResult = solver.solve(puzzle)
        return EvaluatedPuzzle(
            status = solveResult.status,
            rating = grader.grade(puzzle, solveResult)
        )
    }

    private fun verifiedSuccess(
        puzzle: IntArray,
        solution: IntArray,
        requestedDifficulty: SudokuDifficulty,
        expectedRating: DifficultyRating,
        baseSeed: Long,
        attemptSeed: Long,
        attemptIndex: Int,
        cluesRestored: Int,
        attemptsUsed: Int,
        bestObservedRating: DifficultyRating?,
        counters: GenerationCounters,
        version: PuzzleEngineVersion
    ): TargetGenerationResult {
        val validationEngine = SudokuPuzzleEngine(VALIDATION_ENGINE_SEED)
        val validationSolver = SudokuLogicalSolver()
        val validationGrader = SudokuDifficultyGrader()
        val solved = validationEngine.solve(puzzle)
        val logicalResult = validationSolver.solve(puzzle)
        val verifiedRating = try {
            validationGrader.grade(puzzle, logicalResult)
        } catch (_: IllegalArgumentException) {
            null
        }
        val isValid =
            puzzle.size == BOARD_CELL_COUNT &&
                solution.size == BOARD_CELL_COUNT &&
                puzzle.indices.all { puzzle[it] == 0 || puzzle[it] == solution[it] } &&
                validationEngine.countSolutions(puzzle, 2) == 1 &&
                solved != null && solved.contentEquals(solution) &&
                logicalResult.status == LogicalSolveStatus.SOLVED &&
                verifiedRating == expectedRating &&
                verifiedRating?.difficulty == requestedDifficulty

        if (!isValid) {
            return failure(
                requestedDifficulty = requestedDifficulty,
                baseSeed = baseSeed,
                attemptsUsed = attemptsUsed,
                bestObservedRating = bestObservedRating,
                reason = TargetGenerationFailureReason.INTERNAL_VALIDATION_DEFECT,
                counters = counters,
                version = version
            )
        }

        return TargetGenerationResult.Success(
            TargetedPuzzle(
                puzzle = puzzle,
                solution = solution,
                requestedDifficulty = requestedDifficulty,
                rating = requireNotNull(verifiedRating),
                baseSeed = baseSeed,
                attemptSeed = attemptSeed,
                attemptIndex = attemptIndex,
                cluesRestored = cluesRestored,
                engineVersion = version
            )
        )
    }

    private fun isValidGeneratedPuzzle(
        puzzle: IntArray,
        solution: IntArray,
        validationEngine: SudokuPuzzleEngine
    ): Boolean {
        if (puzzle.size != BOARD_CELL_COUNT || solution.size != BOARD_CELL_COUNT) return false
        if (solution.any { it !in 1..9 }) return false
        if (puzzle.indices.any { puzzle[it] != 0 && puzzle[it] != solution[it] }) return false
        try {
            validationEngine.validateBoard(puzzle)
            validationEngine.validateBoard(solution)
        } catch (_: IllegalArgumentException) {
            return false
        }
        if (validationEngine.countSolutions(puzzle, 2) != 1) return false
        return validationEngine.solve(puzzle)?.contentEquals(solution) == true
    }

    private fun betterRating(
        current: DifficultyRating?,
        candidate: DifficultyRating,
        requestedDifficulty: SudokuDifficulty
    ): DifficultyRating {
        if (current == null) return candidate
        val candidateDistance = abs(
            difficultyRank(candidate.difficulty) - difficultyRank(requestedDifficulty)
        )
        val currentDistance = abs(
            difficultyRank(current.difficulty) - difficultyRank(requestedDifficulty)
        )
        return when {
            candidateDistance < currentDistance -> candidate
            candidateDistance > currentDistance -> current
            candidate.totalScore < current.totalScore -> candidate
            else -> current
        }
    }

    private fun compareDifficulty(actual: SudokuDifficulty, requested: SudokuDifficulty): Int =
        difficultyRank(actual).compareTo(difficultyRank(requested))

    private fun difficultyRank(difficulty: SudokuDifficulty): Int = when (difficulty) {
        SudokuDifficulty.EASY -> 0
        SudokuDifficulty.MEDIUM -> 1
        SudokuDifficulty.HARD -> 2
        SudokuDifficulty.UNSUPPORTED -> 3
    }

    private fun failure(
        requestedDifficulty: SudokuDifficulty,
        baseSeed: Long,
        attemptsUsed: Int,
        bestObservedRating: DifficultyRating?,
        reason: TargetGenerationFailureReason,
        counters: GenerationCounters,
        version: PuzzleEngineVersion
    ): TargetGenerationResult.Failure = TargetGenerationResult.Failure(
        TargetGenerationFailure(
            requestedDifficulty = requestedDifficulty,
            baseSeed = baseSeed,
            attemptsUsed = attemptsUsed,
            bestObservedRating = bestObservedRating,
            engineVersion = version,
            reason = reason,
            ratingsEvaluated = counters.ratingsEvaluated,
            cluesRestored = counters.cluesRestored,
            overshootCount = counters.overshootCount,
            restorationLimitHits = counters.restorationLimitHits
        )
    )

    private data class EvaluatedPuzzle(
        val status: LogicalSolveStatus,
        val rating: DifficultyRating
    )

    private class GenerationCounters {
        var ratingsEvaluated: Int = 0
        var cluesRestored: Int = 0
        var overshootCount: Int = 0
        var restorationLimitHits: Int = 0
    }

    private companion object {
        const val BOARD_CELL_COUNT = 81
        const val VALIDATION_ENGINE_SEED = 0L
        val NO_OBSERVER: (TargetGenerationObservation) -> Unit = {}
    }
}

internal data class TargetGenerationPolicy(
    val easyAttemptBudget: Int,
    val mediumAttemptBudget: Int,
    val hardAttemptBudget: Int,
    val maxClueRestorations: Int
) {
    init {
        require(easyAttemptBudget > 0) { "Easy attempt budget must be positive" }
        require(mediumAttemptBudget > 0) { "Medium attempt budget must be positive" }
        require(hardAttemptBudget > 0) { "Hard attempt budget must be positive" }
        require(maxClueRestorations >= 0) { "Restoration budget must not be negative" }
    }

    fun attemptBudget(difficulty: SudokuDifficulty): Int = when (difficulty) {
        SudokuDifficulty.EASY -> easyAttemptBudget
        SudokuDifficulty.MEDIUM -> mediumAttemptBudget
        SudokuDifficulty.HARD -> hardAttemptBudget
        SudokuDifficulty.UNSUPPORTED -> error("UNSUPPORTED has no attempt budget")
    }

    companion object {
        val V1 = TargetGenerationPolicy(
            easyAttemptBudget = DifficultyTargetingV1.EASY_ATTEMPT_BUDGET,
            mediumAttemptBudget = DifficultyTargetingV1.MEDIUM_ATTEMPT_BUDGET,
            hardAttemptBudget = DifficultyTargetingV1.HARD_ATTEMPT_BUDGET,
            maxClueRestorations = DifficultyTargetingV1.MAX_CLUE_RESTORATIONS
        )
    }
}

/** Central targeting-v1 policy constants. Changes require a targeting-version decision. */
internal object DifficultyTargetingV1 {
    const val EASY_ATTEMPT_BUDGET = 8
    const val MEDIUM_ATTEMPT_BUDGET = 12
    const val HARD_ATTEMPT_BUDGET = 20
    const val MAX_CLUE_RESTORATIONS = 24
}

internal enum class TargetGenerationPhase {
    RAW,
    RESTORATION
}

internal data class TargetGenerationObservation(
    val phase: TargetGenerationPhase,
    val attemptIndex: Int,
    val attemptSeed: Long,
    val cluesRestored: Int,
    val logicalStatus: LogicalSolveStatus,
    val rating: DifficultyRating
)

/** Stable targeting-v1 attempt seeds and restoration ordering. Long overflow wraps modulo 2^64. */
internal object TargetingSeedDerivationV1 {
    private const val DOMAIN_SALT = -7046029254386353131L
    private const val ABSORB_SALT = 7640891576956012809L
    private const val RESTORATION_SALT = 4354685564936845354L
    private const val MIX_MULTIPLIER_1 = -4658895280553007687L
    private const val MIX_MULTIPLIER_2 = -7723592293110705685L

    fun attemptSeed(
        baseSeed: Long,
        difficulty: SudokuDifficulty,
        engineVersion: PuzzleEngineVersion,
        attemptIndex: Int
    ): Long {
        require(difficulty != SudokuDifficulty.UNSUPPORTED) {
            "UNSUPPORTED is not a target difficulty"
        }
        require(attemptIndex >= 0) { "Attempt index must not be negative" }
        var state = mix64(baseSeed xor DOMAIN_SALT)
        state = absorb(state, difficultyCode(difficulty))
        state = absorb(state, engineVersion.targeting.toLong())
        state = absorb(state, engineVersion.generator.toLong())
        state = absorb(state, engineVersion.logicalSolver.toLong())
        state = absorb(state, engineVersion.grader.toLong())
        return absorb(state, attemptIndex.toLong())
    }

    fun shuffleForRestoration(indices: IntArray, attemptSeed: Long) {
        val random = StableTargetingRandom(mix64(attemptSeed xor RESTORATION_SALT))
        for (index in indices.lastIndex downTo 1) {
            val swapIndex = random.nextIndex(index + 1)
            val value = indices[index]
            indices[index] = indices[swapIndex]
            indices[swapIndex] = value
        }
    }

    private fun difficultyCode(difficulty: SudokuDifficulty): Long = when (difficulty) {
        SudokuDifficulty.EASY -> 1L
        SudokuDifficulty.MEDIUM -> 2L
        SudokuDifficulty.HARD -> 3L
        SudokuDifficulty.UNSUPPORTED -> error("UNSUPPORTED is not a target difficulty")
    }

    private fun absorb(state: Long, value: Long): Long =
        mix64(state xor mix64(value + ABSORB_SALT))

    private fun mix64(input: Long): Long {
        var value = input
        value = (value xor (value ushr 30)) * MIX_MULTIPLIER_1
        value = (value xor (value ushr 27)) * MIX_MULTIPLIER_2
        return value xor (value ushr 31)
    }

    private class StableTargetingRandom(initialState: Long) {
        private var state = initialState

        fun nextIndex(bound: Int): Int {
            require(bound > 0) { "Bound must be positive" }
            state += DOMAIN_SALT
            val nonNegative = mix64(state).ushr(1)
            return (nonNegative % bound.toLong()).toInt()
        }
    }
}
