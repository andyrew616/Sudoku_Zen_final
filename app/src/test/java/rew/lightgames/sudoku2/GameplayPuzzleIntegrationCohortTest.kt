package rew.lightgames.sudoku2

import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameplayPuzzleIntegrationCohortTest {
    @Test
    fun threeThousandNewGames_deliverOnlyUniqueExactGradeBoards() {
        val difficulties = listOf(
            SudokuDifficulty.EASY,
            SudokuDifficulty.MEDIUM,
            SudokuDifficulty.HARD
        )
        val catalog = CompactFallbackPuzzleProvider {
            File("src/main/assets/$GRADED_FALLBACK_ASSET").readText()
        }
        val provider = GameplayPuzzleProvider(catalog)
        val requests = difficulties.flatMap { difficulty ->
            (0L until 1_000L).map { seed -> difficulty to seed }
        }
        val executor = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors().coerceAtMost(8).coerceAtLeast(2)
        )
        val results = try {
            executor.invokeAll(requests.map { (difficulty, seed) ->
                Callable { audit(provider.createPuzzle(difficulty, seed), difficulty) }
            }).map { it.get() }
        } finally {
            executor.shutdownNow()
        }

        difficulties.forEach { difficulty ->
            val matching = results.filter { it.difficulty == difficulty }
            val generated = matching.count { it.source == GameplayPuzzleSource.GENERATED }
            val fallback = matching.count { it.source == GameplayPuzzleSource.FALLBACK }
            println(
                "PR9 INTEGRATION $difficulty requests=${matching.size} " +
                    "generated=$generated fallback=$fallback failures=0"
            )
            assertEquals(1_000, matching.size)
            assertEquals(1_000, generated + fallback)
        }

        assertEquals(3_000, results.size)
        assertEquals(0, results.count { !it.correctGrade })
        assertEquals(0, results.count { !it.unique })
        assertEquals(0, results.count { !it.solutionMatches })
        assertEquals(0, results.count { it.unsupported })
    }

    private fun audit(
        result: PuzzleLoadResult,
        requestedDifficulty: SudokuDifficulty
    ): IntegrationAudit {
        assertTrue("provider failed for $requestedDifficulty: $result", result is PuzzleLoadResult.Ready)
        result as PuzzleLoadResult.Ready
        val puzzle = IntArray(81) { index ->
            result.board.getCell(index / 9, index % 9).original_number
        }
        val storedSolution = IntArray(81) { index ->
            result.board.solution[index / 9][index % 9]
        }
        val engine = SudokuPuzzleEngine(0L)
        val solved = engine.solve(puzzle)
        val logical = SudokuLogicalSolver().solve(puzzle)
        val rating = SudokuDifficultyGrader().grade(puzzle, logical)
        val correctGrade =
            result.requestedDifficulty == requestedDifficulty &&
                result.actualRating.difficulty == requestedDifficulty &&
                rating.difficulty == requestedDifficulty
        val unique = engine.countSolutions(puzzle, 2) == 1
        val solutionMatches = solved != null && solved.contentEquals(storedSolution)

        if (solved != null) assertArrayEquals(storedSolution, solved)
        assertFalse(result.source == GameplayPuzzleSource.RESUMED)

        return IntegrationAudit(
            difficulty = requestedDifficulty,
            source = result.source,
            correctGrade = correctGrade,
            unique = unique,
            solutionMatches = solutionMatches,
            unsupported = logical.status != LogicalSolveStatus.SOLVED ||
                rating.difficulty == SudokuDifficulty.UNSUPPORTED
        )
    }

    private data class IntegrationAudit(
        val difficulty: SudokuDifficulty,
        val source: GameplayPuzzleSource,
        val correctGrade: Boolean,
        val unique: Boolean,
        val solutionMatches: Boolean,
        val unsupported: Boolean
    )
}
