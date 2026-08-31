package rew.lightgames.sudoku2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SudokuDifficultyFixtureTest {
    private val solver = SudokuLogicalSolver()
    private val grader = SudokuDifficultyGrader()

    /** Original fixtures reproduced from this project's deterministic generator. */
    private val fixtures = listOf(
        DifficultyFixture(
            seed = 28L,
            expectedDifficulty = SudokuDifficulty.EASY,
            expectedStatus = LogicalSolveStatus.SOLVED,
            expectedHardest = SudokuTechnique.NAKED_SINGLE,
            expectedScoreRange = 55..60,
            reason = "Completes with Naked Singles only and remains below the Easy ceiling."
        ),
        DifficultyFixture(
            seed = 12L,
            expectedDifficulty = SudokuDifficulty.MEDIUM,
            expectedStatus = LogicalSolveStatus.SOLVED,
            expectedHardest = SudokuTechnique.LOCKED_CANDIDATES_POINTING,
            expectedScoreRange = 70..80,
            reason = "Completes logically but requires Pointing, which imposes the Medium floor."
        ),
        DifficultyFixture(
            seed = 16L,
            expectedDifficulty = SudokuDifficulty.HARD,
            expectedStatus = LogicalSolveStatus.SOLVED,
            expectedHardest = SudokuTechnique.SKYSCRAPER,
            expectedScoreRange = 100..110,
            reason = "Completes logically but requires a Skyscraper, which imposes the Hard floor."
        ),
        DifficultyFixture(
            seed = 1L,
            expectedDifficulty = SudokuDifficulty.UNSUPPORTED,
            expectedStatus = LogicalSolveStatus.STALLED,
            expectedHardest = SudokuTechnique.SKYSCRAPER,
            expectedScoreRange = 70..75,
            reason = "Uses valid v1 steps, then stalls before completion."
        )
    )

    @Test
    fun curatedOriginalFixtures_matchTheirExplainableV1Ratings() {
        fixtures.forEach { fixture ->
            val generated = SudokuPuzzleEngine(fixture.seed).generate()
            val result = solver.solve(generated.puzzle)
            val rating = grader.grade(generated.puzzle, result)

            assertTrue("Fixture reason must remain documented", fixture.reason.isNotBlank())
            assertEquals("Seed ${fixture.seed} status", fixture.expectedStatus, result.status)
            assertEquals(
                "Seed ${fixture.seed}: ${fixture.reason}",
                fixture.expectedDifficulty,
                rating.difficulty
            )
            assertEquals(
                "Seed ${fixture.seed} hardest technique",
                fixture.expectedHardest,
                rating.hardestTechnique
            )
            assertTrue(
                "Seed ${fixture.seed} score ${rating.totalScore} outside " +
                    "${fixture.expectedScoreRange}",
                rating.totalScore in fixture.expectedScoreRange
            )
        }
    }

    private data class DifficultyFixture(
        val seed: Long,
        val expectedDifficulty: SudokuDifficulty,
        val expectedStatus: LogicalSolveStatus,
        val expectedHardest: SudokuTechnique,
        val expectedScoreRange: IntRange,
        val reason: String
    )
}
