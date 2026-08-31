package rew.lightgames.sudoku2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicalHintProviderTest {
    private val provider = LogicalHintProvider()
    private val solution = intArrayOf(
        5, 3, 4, 6, 7, 8, 9, 1, 2,
        6, 7, 2, 1, 9, 5, 3, 4, 8,
        1, 9, 8, 3, 4, 2, 5, 6, 7,
        8, 5, 9, 7, 6, 1, 4, 2, 3,
        4, 2, 6, 8, 5, 3, 7, 9, 1,
        7, 1, 3, 9, 2, 4, 8, 5, 6,
        9, 6, 1, 5, 3, 7, 2, 8, 4,
        2, 8, 7, 4, 1, 9, 6, 3, 5,
        3, 4, 5, 2, 8, 6, 1, 7, 9
    )

    @Test
    fun wrongPlayerValue_isRejectedBeforeAnyLogicalHint() {
        val player = solution.clone().also {
            it[0] = 0
            it[1] = 4
        }

        assertEquals(
            LogicalHintResult.INCORRECT_VALUE_PRESENT,
            provider.hintFor(player, solution, HintDetailLevel.ACTION)
        )
    }

    @Test
    fun solvedPlayerBoard_returnsSolved() {
        assertEquals(
            LogicalHintResult.SOLVED,
            provider.hintFor(solution, solution, HintDetailLevel.ACTION)
        )
    }

    @Test
    fun unsupportedStalledBoard_doesNotRevealFromSolution() {
        assertEquals(
            LogicalHintResult.NO_SUPPORTED_LOGICAL_HINT,
            provider.hintFor(IntArray(81), solution, HintDetailLevel.ACTION)
        )
    }

    @Test
    fun malformedPlayerOrSolution_returnsInvalidState() {
        assertEquals(
            LogicalHintResult.INVALID_PLAYER_STATE,
            provider.hintFor(IntArray(80), solution, HintDetailLevel.TECHNIQUE)
        )
        assertEquals(
            LogicalHintResult.INVALID_PLAYER_STATE,
            provider.hintFor(IntArray(81), IntArray(81), HintDetailLevel.TECHNIQUE)
        )
        val invalidDigit = IntArray(81).also { it[0] = 10 }
        assertEquals(
            LogicalHintResult.INVALID_PLAYER_STATE,
            provider.hintFor(invalidDigit, solution, HintDetailLevel.TECHNIQUE)
        )
    }

    @Test
    fun correctCurrentBoard_matchesSolverNextStepDeterministically() {
        val player = solution.clone().also { it[0] = 0 }
        val expectedGrid = (CandidateGrid.create(player) as CandidateGridCreationResult.Success).grid
        val expected = requireNotNull(SudokuLogicalSolver().nextStep(expectedGrid))

        val first = provider.hintFor(player, solution, HintDetailLevel.ACTION)
        val repeated = provider.hintFor(player.clone(), solution.clone(), HintDetailLevel.ACTION)

        assertTrue(first is LogicalHintResult.Available)
        assertEquals(first, repeated)
        assertEquals(expected.actions, (first as LogicalHintResult.Available).hint.actions)
        assertEquals(expected.technique, first.hint.technique)
        assertEquals(5, (first.hint.actions.single() as SolveAction.PlaceValue).digit)
    }
}
