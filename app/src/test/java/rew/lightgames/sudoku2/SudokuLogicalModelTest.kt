package rew.lightgames.sudoku2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SudokuLogicalModelTest {
    @Test
    fun cellRef_validates_bounds_and_maps_index() {
        assertEquals(0, CellRef(0, 0).index)
        assertEquals(40, CellRef(4, 4).index)
        assertEquals(80, CellRef(8, 8).index)
        assertEquals(CellRef(7, 3), CellRef.fromIndex(66))

        assertThrows(IllegalArgumentException::class.java) { CellRef(-1, 0) }
        assertThrows(IllegalArgumentException::class.java) { CellRef(9, 0) }
        assertThrows(IllegalArgumentException::class.java) { CellRef(0, -1) }
        assertThrows(IllegalArgumentException::class.java) { CellRef(0, 9) }
        assertThrows(IllegalArgumentException::class.java) { CellRef.fromIndex(81) }
    }

    @Test
    fun cellRef_ordering_is_row_major() {
        val cells = listOf(CellRef(8, 8), CellRef(0, 2), CellRef(0, 1), CellRef(4, 0))
        assertEquals(
            listOf(CellRef(0, 1), CellRef(0, 2), CellRef(4, 0), CellRef(8, 8)),
            cells.sorted()
        )
    }

    @Test
    fun houseRef_validates_index() {
        HouseType.entries.forEach { type ->
            assertEquals(0, HouseRef(type, 0).index)
            assertEquals(8, HouseRef(type, 8).index)
        }
        assertThrows(IllegalArgumentException::class.java) { HouseRef(HouseType.ROW, -1) }
        assertThrows(IllegalArgumentException::class.java) { HouseRef(HouseType.BOX, 9) }
    }

    @Test
    fun houseRef_ordering_is_type_then_index() {
        val houses = listOf(
            HouseRef(HouseType.BOX, 0),
            HouseRef(HouseType.COLUMN, 1),
            HouseRef(HouseType.ROW, 8),
            HouseRef(HouseType.ROW, 2),
            HouseRef(HouseType.COLUMN, 0)
        )
        assertEquals(
            listOf(
                HouseRef(HouseType.ROW, 2),
                HouseRef(HouseType.ROW, 8),
                HouseRef(HouseType.COLUMN, 0),
                HouseRef(HouseType.COLUMN, 1),
                HouseRef(HouseType.BOX, 0)
            ),
            houses.sorted()
        )
    }

    @Test
    fun digitSet_empty_and_allDigits_are_canonical() {
        assertTrue(DigitSet.EMPTY.isEmpty)
        assertEquals(0, DigitSet.EMPTY.size)
        assertEquals(emptyList<Int>(), DigitSet.EMPTY.digitsAscending())

        assertFalse(DigitSet.ALL_DIGITS.isEmpty)
        assertEquals(9, DigitSet.ALL_DIGITS.size)
        assertEquals(9, DigitSet.ALL_DIGITS.count)
        assertEquals((1..9).toList(), DigitSet.ALL_DIGITS.digitsAscending())
    }

    @Test
    fun digitSet_contains_add_remove_and_ordering_are_immutable() {
        val original = DigitSet.of(9, 2, 5)
        val added = original.add(1)
        val removed = added.remove(5)

        assertEquals(listOf(2, 5, 9), original.digitsAscending())
        assertEquals(listOf(1, 2, 5, 9), added.digitsAscending())
        assertEquals(listOf(1, 2, 9), removed.digitsAscending())
        assertTrue(5 in original)
        assertFalse(1 in original)
    }

    @Test
    fun digitSet_rejects_invalid_digits() {
        assertThrows(IllegalArgumentException::class.java) { DigitSet.of(0) }
        assertThrows(IllegalArgumentException::class.java) { DigitSet.of(10) }
        assertThrows(IllegalArgumentException::class.java) { DigitSet.EMPTY.add(-1) }
        assertThrows(IllegalArgumentException::class.java) { DigitSet.ALL_DIGITS.remove(12) }
        assertThrows(IllegalArgumentException::class.java) { 0 in DigitSet.ALL_DIGITS }
    }

    @Test
    fun digitSet_equality_and_hashing_depend_only_on_digits() {
        val first = DigitSet.of(1, 4, 7)
        val second = DigitSet.EMPTY.add(7).add(1).add(4)
        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertNotEquals(first, DigitSet.of(1, 4))
        assertEquals("{1, 4, 7}", first.toString())
    }

    @Test
    fun solveActions_validate_digits_and_nonEmpty_eliminations() {
        val cell = CellRef(0, 0)
        SolveAction.PlaceValue(cell, 1)
        SolveAction.EliminateCandidates(cell, DigitSet.of(2))

        assertThrows(IllegalArgumentException::class.java) {
            SolveAction.PlaceValue(cell, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SolveAction.EliminateCandidates(cell, DigitSet.EMPTY)
        }
    }

    @Test
    fun logicalStep_requires_actions_rejects_conflicts_and_canonicalizes_order() {
        val evidence = StepEvidence.Subset(
            HouseRef(HouseType.ROW, 0),
            DigitSet.of(1, 2),
            listOf(CellRef(0, 4), CellRef(0, 1))
        )
        assertThrows(IllegalArgumentException::class.java) {
            LogicalStep(SudokuTechnique.NAKED_PAIR, emptyList(), evidence)
        }

        val duplicate = SolveAction.EliminateCandidates(CellRef(0, 0), DigitSet.of(1))
        assertThrows(IllegalArgumentException::class.java) {
            LogicalStep(SudokuTechnique.NAKED_PAIR, listOf(duplicate, duplicate), evidence)
        }
        assertThrows(IllegalArgumentException::class.java) {
            LogicalStep(
                SudokuTechnique.HIDDEN_SINGLE,
                listOf(
                    SolveAction.PlaceValue(CellRef(0, 0), 1),
                    SolveAction.EliminateCandidates(CellRef(0, 0), DigitSet.of(2))
                ),
                evidence
            )
        }

        val step = LogicalStep(
            SudokuTechnique.NAKED_PAIR,
            listOf(
                SolveAction.EliminateCandidates(CellRef(2, 0), DigitSet.of(1)),
                SolveAction.EliminateCandidates(CellRef(0, 8), DigitSet.of(2))
            ),
            evidence
        )
        assertEquals(listOf(CellRef(0, 8), CellRef(2, 0)), step.actions.map { it.cell })
        assertEquals(listOf(CellRef(0, 1), CellRef(0, 4)), evidence.cells)
        assertThrows(UnsupportedOperationException::class.java) {
            (step.actions as MutableList).clear()
        }
    }

    @Test
    fun evidenceCollections_areCanonicalCopiesAndCannotAliasInputs() {
        val inputCells = mutableListOf(CellRef(0, 4), CellRef(0, 1), CellRef(0, 4))
        val evidence = StepEvidence.Subset(
            HouseRef(HouseType.ROW, 0),
            DigitSet.of(1, 2),
            inputCells
        )

        inputCells.clear()
        assertEquals(listOf(CellRef(0, 1), CellRef(0, 4)), evidence.cells)
        assertThrows(UnsupportedOperationException::class.java) {
            (evidence.cells as MutableList).clear()
        }
    }

    @Test
    fun singleEvidenceAndLogicalStepsHaveStructuralEquality() {
        val firstEvidence = StepEvidence.Single(
            CellRef(2, 3),
            7,
            DigitSet.of(2, 7),
            HouseRef(HouseType.ROW, 2)
        )
        val secondEvidence = StepEvidence.Single(
            CellRef(2, 3),
            7,
            DigitSet.of(7, 2),
            HouseRef(HouseType.ROW, 2)
        )
        val first = LogicalStep(
            SudokuTechnique.HIDDEN_SINGLE,
            listOf(SolveAction.PlaceValue(CellRef(2, 3), 7)),
            firstEvidence
        )
        val second = LogicalStep(
            SudokuTechnique.HIDDEN_SINGLE,
            listOf(SolveAction.PlaceValue(CellRef(2, 3), 7)),
            secondEvidence
        )

        assertEquals(firstEvidence, secondEvidence)
        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertEquals(first.toString(), second.toString())
    }

    @Test
    fun singleEvidenceValidatesCandidatesHouseAndMatchingAction() {
        assertThrows(IllegalArgumentException::class.java) {
            StepEvidence.Single(CellRef(0, 0), 1, DigitSet.EMPTY)
        }
        assertThrows(IllegalArgumentException::class.java) {
            StepEvidence.Single(CellRef(0, 0), 1, DigitSet.of(2))
        }
        assertThrows(IllegalArgumentException::class.java) {
            StepEvidence.Single(
                CellRef(0, 0),
                1,
                DigitSet.of(1, 2),
                HouseRef(HouseType.ROW, 1)
            )
        }

        val evidence = StepEvidence.Single(CellRef(0, 0), 1, DigitSet.of(1))
        assertThrows(IllegalArgumentException::class.java) {
            LogicalStep(
                SudokuTechnique.NAKED_SINGLE,
                listOf(SolveAction.PlaceValue(CellRef(0, 1), 1)),
                evidence
            )
        }
    }

    @Test
    fun lockedCandidatesEvidence_hasCanonicalStructuralValueSemantics() {
        val mutableCells = mutableListOf(CellRef(0, 1), CellRef(0, 0))
        val first = StepEvidence.LockedCandidates(
            5,
            HouseRef(HouseType.BOX, 0),
            HouseRef(HouseType.ROW, 0),
            mutableCells
        )
        mutableCells.clear()
        val second = StepEvidence.LockedCandidates(
            5,
            HouseRef(HouseType.BOX, 0),
            HouseRef(HouseType.ROW, 0),
            listOf(CellRef(0, 0), CellRef(0, 1))
        )

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertEquals(first.toString(), second.toString())
        assertEquals(listOf(CellRef(0, 0), CellRef(0, 1)), first.sourceCells)
    }

    @Test
    fun subsetEvidence_hasCanonicalStructuralValueSemanticsAndCardinalityValidation() {
        val first = StepEvidence.Subset(
            HouseRef(HouseType.ROW, 0),
            DigitSet.of(1, 2),
            listOf(CellRef(0, 2), CellRef(0, 1)),
            hidden = true
        )
        val second = StepEvidence.Subset(
            HouseRef(HouseType.ROW, 0),
            DigitSet.of(2, 1),
            listOf(CellRef(0, 1), CellRef(0, 2)),
            hidden = true
        )

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertEquals(first.toString(), second.toString())
        assertThrows(IllegalArgumentException::class.java) {
            StepEvidence.Subset(
                HouseRef(HouseType.ROW, 0),
                DigitSet.of(1, 2, 3),
                listOf(CellRef(0, 0), CellRef(0, 1)),
                hidden = false
            )
        }
    }
}
