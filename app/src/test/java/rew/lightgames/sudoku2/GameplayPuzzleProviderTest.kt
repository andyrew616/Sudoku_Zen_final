package rew.lightgames.sudoku2

import com.google.gson.Gson
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GameplayPuzzleProviderTest {
    private val engine = SudokuPuzzleEngine(0L)
    private val solver = SudokuLogicalSolver()
    private val grader = SudokuDifficultyGrader()

    @Test
    fun legacyDifficultyValues_mapExactly() {
        assertEquals(
            GameplayDifficultyMapping.Valid(SudokuDifficulty.EASY),
            GameplayDifficultyAdapter.fromExternalValue("easy")
        )
        assertEquals(
            GameplayDifficultyMapping.Valid(SudokuDifficulty.MEDIUM),
            GameplayDifficultyAdapter.fromExternalValue("medium")
        )
        assertEquals(
            GameplayDifficultyMapping.Valid(SudokuDifficulty.HARD),
            GameplayDifficultyAdapter.fromExternalValue("hard")
        )
        assertEquals("easy", GameplayDifficultyAdapter.toExternalValue(SudokuDifficulty.EASY))
        assertEquals("medium", GameplayDifficultyAdapter.toExternalValue(SudokuDifficulty.MEDIUM))
        assertEquals("hard", GameplayDifficultyAdapter.toExternalValue(SudokuDifficulty.HARD))
    }

    @Test
    fun unknownDifficultyValues_areRejectedWithoutDowngrade() {
        listOf(null, "", "Easy", "expert", "unsupported").forEach { value ->
            assertEquals(
                GameplayDifficultyMapping.Invalid(value),
                GameplayDifficultyAdapter.fromExternalValue(value)
            )
        }
    }

    @Test
    fun launchPolicy_neverRestoresAnOlderBoardForBoardlessRecreation() {
        assertEquals(
            GameplayLaunchMode.NEW_GAME,
            GameplayLaunchPolicy.mode(false, false, false)
        )
        assertEquals(
            GameplayLaunchMode.RESTORE_PERSISTED_GAME,
            GameplayLaunchPolicy.mode(true, false, false)
        )
        assertEquals(
            GameplayLaunchMode.RESTORE_PERSISTED_GAME,
            GameplayLaunchPolicy.mode(false, true, true)
        )
        assertEquals(
            GameplayLaunchMode.RECREATED_WITHOUT_ACTIVE_GAME,
            GameplayLaunchPolicy.mode(false, true, false)
        )
    }

    @Test
    fun targetedPuzzleAdapter_mapsAllIndicesGivensBlanksAndSolution() {
        val targeted = targeted(7L, SudokuDifficulty.EASY)
        val puzzle = targeted.puzzle
        val solution = targeted.solution
        val board = GameplaySudokuBoardAdapter.fromTargetedPuzzle(targeted)

        for (index in 0 until 81) {
            val row = index / 9
            val column = index % 9
            val cell = board.getCell(row, column)
            assertEquals("number at $index", puzzle[index], cell.number)
            assertEquals("original at $index", puzzle[index], cell.original_number)
            assertEquals("editable at $index", puzzle[index] == 0, cell.isEditable)
            assertEquals("solution at $index", solution[index], board.solution[row][column])
        }
        assertTrue(puzzle.any { it == 0 })
        assertTrue(puzzle.any { it != 0 })
    }

    @Test
    fun targetedPuzzleAdapter_copiesEngineArrays() {
        val targeted = targeted(7L, SudokuDifficulty.EASY)
        val board = GameplaySudokuBoardAdapter.fromTargetedPuzzle(targeted)
        val expectedFirstSolution = targeted.solution[0]
        val exposedSolution = targeted.solution
        exposedSolution[0] = (exposedSolution[0] % 9) + 1

        assertEquals(expectedFirstSolution, board.solution[0][0])
    }

    @Test
    fun fallbackCatalog_containsTenIndependentlyValidPuzzlesPerGrade() {
        val fallbacks = fallbackCatalog().allPuzzles()
        assertEquals(30, fallbacks.size)

        playableDifficulties.forEach { difficulty ->
            val matching = fallbacks.filter { it.declaredDifficulty == difficulty }
            assertEquals("fallback count for $difficulty", 10, matching.size)
            assertEquals("distinct fallbacks for $difficulty", 10, matching.toSet().size)
            matching.forEachIndexed { index, fallback ->
                val puzzle = fallback.puzzle
                assertEquals(81, puzzle.size)
                assertTrue(puzzle.all { it in 0..9 })
                assertEquals("unique $difficulty #$index", 1, engine.countSolutions(puzzle, 2))
                assertNotNull("solution $difficulty #$index", engine.solve(puzzle))
                val logicalResult = solver.solve(puzzle)
                assertEquals(LogicalSolveStatus.SOLVED, logicalResult.status)
                assertEquals(
                    "grade $difficulty #$index",
                    difficulty,
                    grader.grade(puzzle, logicalResult).difficulty
                )
            }
        }
    }

    @Test
    fun targetFailure_usesOnlyAnExactGradeFallback() {
        playableDifficulties.forEach { difficulty ->
            val provider = GameplayPuzzleProvider(
                fallbackSource = fallbackCatalog(),
                targetGeneration = TargetPuzzleGeneration { seed, requested ->
                    targetFailure(seed, requested)
                }
            )

            val result = provider.createPuzzle(difficulty, 42L)

            assertTrue(result is PuzzleLoadResult.Ready)
            result as PuzzleLoadResult.Ready
            assertEquals(difficulty, result.requestedDifficulty)
            assertEquals(difficulty, result.actualRating.difficulty)
            assertEquals(GameplayPuzzleSource.FALLBACK, result.source)
            assertBoardGrade(result.board, difficulty)
        }
    }

    @Test
    fun crossGradeFallback_isRejected() {
        val easy = fallbackCatalog().select(SudokuDifficulty.EASY, 0L)!!
        val provider = GameplayPuzzleProvider(
            fallbackSource = GameplayFallbackSource { _, _ ->
                GameplayFallbackPuzzle(SudokuDifficulty.EASY, easy.puzzle)
            },
            targetGeneration = TargetPuzzleGeneration { seed, requested ->
                targetFailure(seed, requested)
            }
        )

        val result = provider.createPuzzle(SudokuDifficulty.HARD, 42L)

        assertTrue(result is PuzzleLoadResult.Failure)
        result as PuzzleLoadResult.Failure
        assertEquals(GameplayPuzzleFailureReason.FALLBACK_INVALID, result.reason)
        assertEquals(SudokuDifficulty.HARD, result.requestedDifficulty)
    }

    @Test
    fun malformedOrWrongGradeFallback_isNeverDelivered() {
        val invalidPuzzle = IntArray(81).apply {
            this[0] = 1
            this[1] = 1
        }
        val malformed = GameplayFallbackPuzzle(SudokuDifficulty.HARD, invalidPuzzle)
        val provider = GameplayPuzzleProvider(
            fallbackSource = GameplayFallbackSource { _, _ -> malformed },
            targetGeneration = TargetPuzzleGeneration { seed, requested ->
                targetFailure(seed, requested)
            }
        )

        val result = provider.createPuzzle(SudokuDifficulty.HARD, 9L)

        assertTrue(result is PuzzleLoadResult.Failure)
        assertEquals(
            GameplayPuzzleFailureReason.FALLBACK_INVALID,
            (result as PuzzleLoadResult.Failure).reason
        )
    }

    @Test
    fun successfulTarget_isPrimaryAndIndependentlyExact() {
        val provider = GameplayPuzzleProvider(
            fallbackSource = GameplayFallbackSource { _, _ -> error("Fallback not expected") }
        )

        val result = provider.createPuzzle(SudokuDifficulty.MEDIUM, 0L)

        assertTrue(result is PuzzleLoadResult.Ready)
        result as PuzzleLoadResult.Ready
        assertEquals(GameplayPuzzleSource.GENERATED, result.source)
        assertEquals(SudokuDifficulty.MEDIUM, result.actualRating.difficulty)
        assertBoardGrade(result.board, SudokuDifficulty.MEDIUM)
    }

    @Test
    fun injectedTargetWithFalseGradeEvidence_fallsBackInsteadOfBeingDelivered() {
        val easy = targeted(7L, SudokuDifficulty.EASY)
        val hard = targeted(11L, SudokuDifficulty.HARD)
        val falselyLabelled = TargetedPuzzle(
            puzzle = easy.puzzle,
            solution = easy.solution,
            requestedDifficulty = SudokuDifficulty.HARD,
            rating = hard.rating,
            baseSeed = 1L,
            attemptSeed = 2L,
            attemptIndex = 0,
            cluesRestored = 0,
            engineVersion = PuzzleEngineVersions.CURRENT
        )
        val provider = GameplayPuzzleProvider(
            fallbackSource = fallbackCatalog(),
            targetGeneration = TargetPuzzleGeneration {
                    _, _ -> TargetGenerationResult.Success(falselyLabelled)
            }
        )

        val result = provider.createPuzzle(SudokuDifficulty.HARD, 99L)

        assertTrue(result is PuzzleLoadResult.Ready)
        result as PuzzleLoadResult.Ready
        assertEquals(GameplayPuzzleSource.FALLBACK, result.source)
        assertEquals(SudokuDifficulty.HARD, result.actualRating.difficulty)
        assertBoardGrade(result.board, SudokuDifficulty.HARD)
    }

    @Test
    fun fallbackSelection_isRepeatableAndVariesAcrossSeeds() {
        val catalog = fallbackCatalog()
        playableDifficulties.forEach { difficulty ->
            val sameFirst = catalog.select(difficulty, 123L)
            val sameSecond = catalog.select(difficulty, 123L)
            assertEquals(sameFirst, sameSecond)

            val variants = (0L until 100L)
                .map { catalog.select(difficulty, it)!! }
                .toSet()
            assertTrue("expected fallback variation for $difficulty", variants.size > 1)
        }
    }

    @Test
    fun productionSeedSource_changesEvenWhenClockDoesNot() {
        val seeds = ProductionGameplaySeedSource(clockMillis = { 1234L }, initialCounter = 99L)
        val first = seeds.nextSeed()
        val second = seeds.nextSeed()

        assertNotEquals(first, second)
    }

    @Test
    fun unsupportedGameplayRequest_isRejected() {
        val provider = GameplayPuzzleProvider(fallbackCatalog())
        assertThrows(IllegalArgumentException::class.java) {
            provider.createPuzzle(SudokuDifficulty.UNSUPPORTED, 0L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GameplayDifficultyAdapter.toExternalValue(SudokuDifficulty.UNSUPPORTED)
        }
    }

    @Test
    fun prePr9SavedGameJson_stillDeserializes() {
        val solution = SOLUTION
        val cellRows = (0 until 9).joinToString(prefix = "[", postfix = "]") { row ->
            (0 until 9).joinToString(prefix = "[", postfix = "]") { column ->
                val value = if (row == 0 && column == 2) 0 else solution[row][column]
                val editable = value == 0
                """{"isEditable":$editable,"number":$value,"isHint":false,"notes":[],"original_number":$value}"""
            }
        }
        val solutionRows = solution.joinToString(prefix = "[", postfix = "]") { row ->
            row.joinToString(prefix = "[", postfix = "]")
        }
        val legacyJson =
            """{"board":{"cells":$cellRows,"solution":$solutionRows},"hintsUsed":2,"timeElapsed":37}"""

        val restored = Gson().fromJson(legacyJson, SavedGameState::class.java)

        assertEquals(2, restored.hintsUsed)
        assertEquals(37, restored.timeElapsed)
        assertEquals(0, restored.board.getCell(0, 2).number)
        assertTrue(restored.board.getCell(0, 2).isEditable)
        assertArrayEquals(SOLUTION, restored.board.solution)
    }

    @Test
    fun generatedBoard_roundTripsThroughUnchangedSavedGameSchema() {
        val original = GameplaySudokuBoardAdapter.fromTargetedPuzzle(
            targeted(11L, SudokuDifficulty.HARD)
        )
        val blank = firstBlank(original)
        original.setCell(
            blank.first,
            blank.second,
            original.getCell(blank.first, blank.second).copy(
                number = original.solution[blank.first][blank.second],
                notes = arrayListOf(2, 7)
            )
        )
        val json = Gson().toJson(SavedGameState(original, hintsUsed = 1, timeElapsed = 93))
        val restored = Gson().fromJson(json, SavedGameState::class.java)

        assertEquals(1, restored.hintsUsed)
        assertEquals(93, restored.timeElapsed)
        assertArrayEquals(original.solution, restored.board.solution)
        for (row in 0 until 9) {
            for (column in 0 until 9) {
                assertEquals(original.getCell(row, column), restored.board.getCell(row, column))
            }
        }
    }

    private fun fallbackCatalog(): CompactFallbackPuzzleProvider =
        CompactFallbackPuzzleProvider {
            File("src/main/assets/$GRADED_FALLBACK_ASSET").readText()
        }

    private fun targeted(seed: Long, difficulty: SudokuDifficulty): TargetedPuzzle {
        val result = DifficultyTargetGenerator().generate(seed, difficulty)
        assertTrue("expected target success: $result", result is TargetGenerationResult.Success)
        return (result as TargetGenerationResult.Success).targetedPuzzle
    }

    private fun targetFailure(seed: Long, difficulty: SudokuDifficulty): TargetGenerationResult =
        TargetGenerationResult.Failure(
            TargetGenerationFailure(
                requestedDifficulty = difficulty,
                baseSeed = seed,
                attemptsUsed = 1,
                bestObservedRating = null,
                engineVersion = PuzzleEngineVersions.CURRENT,
                reason = TargetGenerationFailureReason.ATTEMPT_BUDGET_EXHAUSTED,
                ratingsEvaluated = 1,
                cluesRestored = 0,
                overshootCount = 0,
                restorationLimitHits = 0
            )
        )

    private fun assertBoardGrade(board: SudokuBoard, expected: SudokuDifficulty) {
        val puzzle = IntArray(81) { index ->
            board.getCell(index / 9, index % 9).original_number
        }
        val solution = IntArray(81) { index -> board.solution[index / 9][index % 9] }
        assertEquals(1, engine.countSolutions(puzzle, 2))
        assertArrayEquals(solution, engine.solve(puzzle))
        val logical = solver.solve(puzzle)
        assertEquals(LogicalSolveStatus.SOLVED, logical.status)
        assertEquals(expected, grader.grade(puzzle, logical).difficulty)
    }

    private fun firstBlank(board: SudokuBoard): Pair<Int, Int> {
        for (row in 0 until 9) {
            for (column in 0 until 9) {
                if (board.getCell(row, column).original_number == 0) return row to column
            }
        }
        error("Expected an editable cell")
    }

    private companion object {
        val playableDifficulties = listOf(
            SudokuDifficulty.EASY,
            SudokuDifficulty.MEDIUM,
            SudokuDifficulty.HARD
        )

        val SOLUTION = arrayOf(
            intArrayOf(5, 3, 4, 6, 7, 8, 9, 1, 2),
            intArrayOf(6, 7, 2, 1, 9, 5, 3, 4, 8),
            intArrayOf(1, 9, 8, 3, 4, 2, 5, 6, 7),
            intArrayOf(8, 5, 9, 7, 6, 1, 4, 2, 3),
            intArrayOf(4, 2, 6, 8, 5, 3, 7, 9, 1),
            intArrayOf(7, 1, 3, 9, 2, 4, 8, 5, 6),
            intArrayOf(9, 6, 1, 5, 3, 7, 2, 8, 4),
            intArrayOf(2, 8, 7, 4, 1, 9, 6, 3, 5),
            intArrayOf(3, 4, 5, 2, 8, 6, 1, 7, 9)
        )
    }
}
