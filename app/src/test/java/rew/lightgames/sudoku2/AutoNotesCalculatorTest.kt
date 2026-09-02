package rew.lightgames.sudoku2

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoNotesCalculatorTest {
    @Test
    fun emptyBoard_offersEveryDigitInEveryEditableCell() {
        val result = available(IntArray(81), BooleanArray(81) { true })

        for (row in 0 until 9) {
            for (column in 0 until 9) {
                assertEquals((1..9).toList(), result.candidatesAt(row, column))
            }
        }
    }

    @Test
    fun placedValuesExcludeCandidatesFromRowColumnAndBox() {
        val values = IntArray(81).apply {
            this[index(0, 4)] = 1
            this[index(4, 0)] = 2
            this[index(1, 1)] = 3
        }

        val result = available(values)

        assertEquals((4..9).toList(), result.candidatesAt(0, 0))
        assertFalse(result.candidatesAt(0, 8).contains(1))
        assertTrue(result.candidatesAt(0, 8).contains(2))
        assertFalse(result.candidatesAt(8, 0).contains(2))
        assertTrue(result.candidatesAt(8, 0).contains(1))
        assertFalse(result.candidatesAt(2, 2).contains(3))
    }

    @Test
    fun filledAndGivenCellsNeverReceiveAutoNotes() {
        val values = IntArray(81).apply { this[index(0, 0)] = 5 }
        val editable = BooleanArray(81) { true }.apply {
            this[index(0, 0)] = false
            this[index(0, 1)] = false
        }

        val result = available(values, editable)

        assertTrue(result.candidatesAt(0, 0).isEmpty())
        assertTrue(result.candidatesAt(0, 1).isEmpty())
    }

    @Test
    fun placingDigitRemovesItFromPeersButLeavesUnrelatedCellUnchanged() {
        val editable = BooleanArray(81) { true }
        val before = available(IntArray(81), editable)
        val values = IntArray(81).apply { this[index(0, 0)] = 5 }

        val after = available(values, editable)

        assertFalse(after.candidatesAt(0, 8).contains(5))
        assertFalse(after.candidatesAt(8, 0).contains(5))
        assertFalse(after.candidatesAt(1, 1).contains(5))
        assertEquals(before.candidatesAt(4, 4), after.candidatesAt(4, 4))
    }

    @Test
    fun deletingDigitRestoresEveryNewlyLegalPeerCandidate() {
        val editable = BooleanArray(81) { true }
        val placed = IntArray(81).apply { this[index(0, 0)] = 7 }
        val afterPlacement = available(placed, editable)

        placed[index(0, 0)] = 0
        val afterDeletion = available(placed, editable)

        assertFalse(afterPlacement.candidatesAt(0, 8).contains(7))
        assertTrue(afterDeletion.candidatesAt(0, 8).contains(7))
        assertTrue(afterDeletion.candidatesAt(8, 0).contains(7))
        assertTrue(afterDeletion.candidatesAt(1, 1).contains(7))
    }

    @Test
    fun multiplePlacedValuesProduceCanonicalCombinedCandidates() {
        val values = intArrayOf(
            5, 3, 0, 0, 7, 0, 0, 0, 0,
            6, 0, 0, 1, 9, 5, 0, 0, 0,
            0, 9, 8, 0, 0, 0, 0, 6, 0,
            8, 0, 0, 0, 6, 0, 0, 0, 3,
            4, 0, 0, 8, 0, 3, 0, 0, 1,
            7, 0, 0, 0, 2, 0, 0, 0, 6,
            0, 6, 0, 0, 0, 0, 2, 8, 0,
            0, 0, 0, 4, 1, 9, 0, 0, 5,
            0, 0, 0, 0, 8, 0, 0, 7, 9
        )

        val result = available(values)

        assertEquals(listOf(1, 2, 4), result.candidatesAt(0, 2))
        assertEquals(listOf(2, 6), result.candidatesAt(0, 3))
        assertEquals(listOf(1, 2, 3, 4, 5), result.candidatesAt(8, 2))
    }

    @Test
    fun nonConflictingWrongValueStillConstrainsCurrentBoardWithoutSolutionInput() {
        val values = IntArray(81).apply { this[index(0, 0)] = 2 }

        val result = available(values)

        assertFalse(result.candidatesAt(0, 8).contains(2))
        assertFalse(result.candidatesAt(8, 0).contains(2))
        assertFalse(result.candidatesAt(1, 1).contains(2))
        assertTrue(result.candidatesAt(4, 4).contains(2))
    }

    @Test
    fun logicalCandidateEliminationNeverLeaksIntoAutoNotes() {
        val values = IntArray(81)
        val logicalGrid = (CandidateGrid.create(values) as CandidateGridCreationResult.Success).grid
        val cell = CellRef(4, 4)

        assertEquals(
            CandidateGridMutationResult.Success,
            logicalGrid.eliminate(cell, DigitSet.of(5))
        )
        assertFalse(5 in logicalGrid.candidatesAt(cell))

        val autoNotes = available(values)
        assertTrue(5 in autoNotes.candidatesAt(cell.row, cell.column))
    }

    @Test
    fun duplicatePlacedValuesFailSafely() {
        val values = IntArray(81).apply {
            this[index(0, 0)] = 4
            this[index(0, 8)] = 4
        }

        assertSame(
            AutoNotesResult.InvalidBoard,
            AutoNotesCalculator.compute(values, BooleanArray(81) { true })
        )
    }

    @Test
    fun emptyCellWithNoLegalCandidateFailsSafely() {
        val values = IntArray(81).apply {
            for (column in 1..8) this[index(0, column)] = column
            this[index(1, 0)] = 9
        }

        assertSame(
            AutoNotesResult.InvalidBoard,
            AutoNotesCalculator.compute(values, BooleanArray(81) { true })
        )
    }

    @Test
    fun invalidShapeOrDigitFailsSafely() {
        assertSame(
            AutoNotesResult.InvalidBoard,
            AutoNotesCalculator.compute(IntArray(80), BooleanArray(81))
        )
        assertSame(
            AutoNotesResult.InvalidBoard,
            AutoNotesCalculator.compute(
                IntArray(81).apply { this[0] = 10 },
                BooleanArray(81) { true }
            )
        )
    }

    @Test
    fun computationDoesNotMutateInputState() {
        val values = IntArray(81).apply {
            this[index(0, 0)] = 5
            this[index(4, 4)] = 7
        }
        val editable = BooleanArray(81) { it % 2 == 0 }
        val valuesBefore = values.copyOf()
        val editableBefore = editable.copyOf()

        AutoNotesCalculator.compute(values, editable)

        assertArrayEquals(valuesBefore, values)
        assertArrayEquals(editableBefore, editable)
    }

    private fun available(
        values: IntArray,
        editable: BooleanArray = BooleanArray(81) { true }
    ): AutoNotesResult.Available {
        val result = AutoNotesCalculator.compute(values, editable)
        assertTrue("Expected candidates but got $result", result is AutoNotesResult.Available)
        return result as AutoNotesResult.Available
    }

    private fun index(row: Int, column: Int): Int = row * 9 + column
}
