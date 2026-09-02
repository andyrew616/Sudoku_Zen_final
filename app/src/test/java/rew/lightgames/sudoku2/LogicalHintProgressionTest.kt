package rew.lightgames.sudoku2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicalHintProgressionTest {
    @Test
    fun unchangedBoard_advancesTechniqueEvidenceActionAndThenStaysAction() {
        val progression = LogicalHintProgression()
        val board = IntArray(81)

        assertAdvance(progression.advance(board), HintDetailLevel.TECHNIQUE, true)
        assertAdvance(progression.advance(board.clone()), HintDetailLevel.EVIDENCE, false)
        assertAdvance(progression.advance(board.clone()), HintDetailLevel.ACTION, false)
        assertAdvance(progression.advance(board.clone()), HintDetailLevel.ACTION, false)
    }

    @Test
    fun placedValueChange_startsNewSequence() {
        val progression = LogicalHintProgression()
        val board = IntArray(81)
        progression.advance(board)
        progression.advance(board)

        val changed = board.clone().also { it[40] = 5 }
        assertAdvance(progression.advance(changed), HintDetailLevel.TECHNIQUE, true)
        assertAdvance(progression.advance(changed.clone()), HintDetailLevel.EVIDENCE, false)
    }

    @Test
    fun reset_forgetsPreviousBoard() {
        val progression = LogicalHintProgression()
        val board = IntArray(81)
        progression.advance(board)
        progression.reset()

        assertAdvance(progression.advance(board), HintDetailLevel.TECHNIQUE, true)
    }

    private fun assertAdvance(
        advance: HintProgressionAdvance,
        level: HintDetailLevel,
        startsSequence: Boolean
    ) {
        assertEquals(level, advance.detailLevel)
        if (startsSequence) assertTrue(advance.startsSequence)
        else assertFalse(advance.startsSequence)
    }
}
