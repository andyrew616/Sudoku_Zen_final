package rew.lightgames.sudoku2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SudokuAdvancedTechniquesTest {
    private val solver = SudokuLogicalSolver()

    private fun grid(): CandidateGrid =
        (CandidateGrid.create(IntArray(81)) as CandidateGridCreationResult.Success).grid

    private fun retain(grid: CandidateGrid, cell: CellRef, vararg digits: Int) {
        val allowed = DigitSet.of(*digits)
        val remove = grid.candidatesAt(cell).remove(allowed)
        if (!remove.isEmpty) {
            assertEquals(CandidateGridMutationResult.Success, grid.eliminate(cell, remove))
        }
    }

    private fun restrictDigit(
        grid: CandidateGrid,
        house: HouseRef,
        digit: Int,
        supportingCells: Collection<CellRef>
    ) {
        for (cell in grid.cellsIn(house)) {
            if (cell !in supportingCells && digit in grid.candidatesAt(cell)) {
                assertEquals(
                    CandidateGridMutationResult.Success,
                    grid.eliminate(cell, DigitSet.of(digit))
                )
            }
        }
    }

    private fun eliminations(step: LogicalStep): List<SolveAction.EliminateCandidates> =
        step.actions.map { it as SolveAction.EliminateCandidates }

    @Test
    fun pointing_rowEmitsAllOutsideBoxEliminationsAndPreservesSupport() {
        val grid = grid()
        val box = HouseRef(HouseType.BOX, 0)
        val support = listOf(CellRef(0, 0), CellRef(0, 1))
        restrictDigit(grid, box, 5, support)
        val before = grid.snapshot()

        val step = solver.findPointing(grid)!!
        val evidence = step.evidence as StepEvidence.LockedCandidates
        assertEquals(SudokuTechnique.LOCKED_CANDIDATES_POINTING, step.technique)
        assertEquals(5, evidence.digit)
        assertEquals(box, evidence.sourceHouse)
        assertEquals(HouseRef(HouseType.ROW, 0), evidence.targetHouse)
        assertEquals(support, evidence.sourceCells)
        assertEquals((3..8).map { CellRef(0, it) }, eliminations(step).map { it.cell })
        assertTrue(eliminations(step).all { it.digits == DigitSet.of(5) })
        assertEquals(before, grid.snapshot())

        assertEquals(CandidateGridMutationResult.Success, grid.applyActions(step.actions))
        support.forEach { assertTrue(5 in grid.candidatesAt(it)) }
        eliminations(step).forEach { assertFalse(5 in grid.candidatesAt(it.cell)) }
    }

    @Test
    fun pointing_columnEmitsMultipleCanonicalEliminations() {
        val grid = grid()
        val box = HouseRef(HouseType.BOX, 0)
        val support = listOf(CellRef(0, 1), CellRef(1, 1))
        restrictDigit(grid, box, 4, support)

        val step = solver.findPointing(grid)!!
        val evidence = step.evidence as StepEvidence.LockedCandidates
        assertEquals(HouseRef(HouseType.COLUMN, 1), evidence.targetHouse)
        assertEquals((3..8).map { CellRef(it, 1) }, eliminations(step).map { it.cell })
    }

    @Test
    fun pointing_rejectsSupportSpanningRowsAndColumns() {
        val grid = grid()
        restrictDigit(
            grid,
            HouseRef(HouseType.BOX, 0),
            5,
            listOf(CellRef(0, 0), CellRef(1, 1))
        )
        assertNull(solver.findPointing(grid))
    }

    @Test
    fun pointing_rejectsPatternWithNoOutsideElimination() {
        val grid = grid()
        val box = HouseRef(HouseType.BOX, 0)
        restrictDigit(grid, box, 5, listOf(CellRef(0, 0), CellRef(0, 1)))
        for (column in 3..8) grid.eliminate(CellRef(0, column), DigitSet.of(5))
        assertNull(solver.findPointing(grid))
    }

    @Test
    fun pointing_usesLowestBoxThenDigitAndIsPure() {
        val grid = grid()
        restrictDigit(
            grid,
            HouseRef(HouseType.BOX, 3),
            1,
            listOf(CellRef(3, 0), CellRef(3, 1))
        )
        restrictDigit(
            grid,
            HouseRef(HouseType.BOX, 1),
            8,
            listOf(CellRef(0, 3), CellRef(0, 4))
        )
        restrictDigit(
            grid,
            HouseRef(HouseType.BOX, 1),
            2,
            listOf(CellRef(1, 3), CellRef(1, 4))
        )
        val before = grid.snapshot()

        val evidence = solver.findPointing(grid)!!.evidence as StepEvidence.LockedCandidates
        assertEquals(HouseRef(HouseType.BOX, 1), evidence.sourceHouse)
        assertEquals(2, evidence.digit)
        assertEquals(before, grid.snapshot())
    }

    @Test
    fun claiming_rowEmitsAllOtherBoxEliminations() {
        val grid = grid()
        val row = HouseRef(HouseType.ROW, 0)
        val support = listOf(CellRef(0, 0), CellRef(0, 1))
        restrictDigit(grid, row, 6, support)

        val step = solver.findClaiming(grid)!!
        val evidence = step.evidence as StepEvidence.LockedCandidates
        assertEquals(SudokuTechnique.LOCKED_CANDIDATES_CLAIMING, step.technique)
        assertEquals(row, evidence.sourceHouse)
        assertEquals(HouseRef(HouseType.BOX, 0), evidence.targetHouse)
        assertEquals(support, evidence.sourceCells)
        assertEquals(
            listOf(
                CellRef(1, 0), CellRef(1, 1), CellRef(1, 2),
                CellRef(2, 0), CellRef(2, 1), CellRef(2, 2)
            ),
            eliminations(step).map { it.cell }
        )
    }

    @Test
    fun claiming_columnEmitsCanonicalEliminations() {
        val grid = grid()
        val column = HouseRef(HouseType.COLUMN, 1)
        restrictDigit(grid, column, 7, listOf(CellRef(0, 1), CellRef(1, 1)))

        val evidence = solver.findClaiming(grid)!!.evidence as StepEvidence.LockedCandidates
        assertEquals(column, evidence.sourceHouse)
        assertEquals(HouseRef(HouseType.BOX, 0), evidence.targetHouse)
    }

    @Test
    fun claiming_rejectsPositionsAcrossBoxesAndNoOpPatterns() {
        val spanning = grid()
        restrictDigit(
            spanning,
            HouseRef(HouseType.ROW, 0),
            6,
            listOf(CellRef(0, 0), CellRef(0, 3))
        )
        assertNull(solver.findClaiming(spanning))

        val noOp = grid()
        val row = HouseRef(HouseType.ROW, 0)
        restrictDigit(noOp, row, 6, listOf(CellRef(0, 0), CellRef(0, 1)))
        for (rowIndex in 1..2) {
            for (column in 0..2) noOp.eliminate(CellRef(rowIndex, column), DigitSet.of(6))
        }
        assertNull(solver.findClaiming(noOp))
    }

    @Test
    fun claiming_rowsPrecedeColumnsAndLowerHouseDigitWins() {
        val grid = grid()
        restrictDigit(
            grid,
            HouseRef(HouseType.COLUMN, 0),
            1,
            listOf(CellRef(6, 0), CellRef(7, 0))
        )
        restrictDigit(
            grid,
            HouseRef(HouseType.ROW, 8),
            8,
            listOf(CellRef(8, 6), CellRef(8, 7))
        )
        restrictDigit(
            grid,
            HouseRef(HouseType.ROW, 2),
            3,
            listOf(CellRef(2, 3), CellRef(2, 4))
        )

        val evidence = solver.findClaiming(grid)!!.evidence as StepEvidence.LockedCandidates
        assertEquals(HouseRef(HouseType.ROW, 2), evidence.sourceHouse)
        assertEquals(3, evidence.digit)
    }

    @Test
    fun nakedPair_detectsRowsColumnsAndBoxesWithMultipleEliminations() {
        val fixtures = listOf(
            Triple(HouseRef(HouseType.ROW, 0), CellRef(0, 0), CellRef(0, 1)),
            Triple(HouseRef(HouseType.COLUMN, 0), CellRef(0, 0), CellRef(1, 0)),
            Triple(HouseRef(HouseType.BOX, 0), CellRef(0, 0), CellRef(1, 1))
        )
        for ((house, first, second) in fixtures) {
            val grid = grid()
            retain(grid, first, 1, 2)
            retain(grid, second, 1, 2)
            val step = solver.findNakedSubset(grid, 2, SudokuTechnique.NAKED_PAIR)!!
            val evidence = step.evidence as StepEvidence.Subset
            assertEquals(house, evidence.house)
            assertEquals(listOf(first, second).sorted(), evidence.cells)
            assertEquals(DigitSet.of(1, 2), evidence.digits)
            assertFalse(evidence.hidden)
            assertTrue(step.actions.size >= 6)
        }
    }

    @Test
    fun nakedPair_rejectsNoOpThirdIdenticalCellAndNearMiss() {
        val noOp = grid()
        retain(noOp, CellRef(0, 0), 1, 2)
        retain(noOp, CellRef(0, 4), 1, 2)
        for (column in 0..8) {
            if (column !in listOf(0, 4)) noOp.eliminate(CellRef(0, column), DigitSet.of(1, 2))
        }
        assertNull(solver.findNakedSubset(noOp, 2, SudokuTechnique.NAKED_PAIR))

        val three = grid()
        for (column in listOf(0, 4, 8)) retain(three, CellRef(0, column), 1, 2)
        assertNull(solver.findNakedSubset(three, 2, SudokuTechnique.NAKED_PAIR))

        val nearMiss = grid()
        retain(nearMiss, CellRef(0, 0), 1, 2)
        retain(nearMiss, CellRef(0, 1), 1, 3)
        assertNull(solver.findNakedSubset(nearMiss, 2, SudokuTechnique.NAKED_PAIR))
    }

    @Test
    fun nakedPair_selectsCanonicalHouseAndCells() {
        val grid = grid()
        retain(grid, CellRef(2, 0), 1, 2)
        retain(grid, CellRef(2, 1), 1, 2)
        retain(grid, CellRef(0, 4), 7, 8)
        retain(grid, CellRef(0, 5), 7, 8)

        val evidence = solver.findNakedSubset(
            grid,
            2,
            SudokuTechnique.NAKED_PAIR
        )!!.evidence as StepEvidence.Subset
        assertEquals(HouseRef(HouseType.ROW, 0), evidence.house)
        assertEquals(listOf(CellRef(0, 4), CellRef(0, 5)), evidence.cells)
    }

    @Test
    fun hiddenPair_detectsRowsColumnsAndBoxesAndRemovesExtras() {
        val fixtures = listOf(
            Triple(HouseRef(HouseType.ROW, 0), CellRef(0, 0), CellRef(0, 1)),
            Triple(HouseRef(HouseType.COLUMN, 0), CellRef(0, 0), CellRef(1, 0)),
            Triple(HouseRef(HouseType.BOX, 0), CellRef(0, 0), CellRef(1, 1))
        )
        for ((house, first, second) in fixtures) {
            val grid = grid()
            val cells = listOf(first, second)
            restrictDigit(grid, house, 1, cells)
            restrictDigit(grid, house, 2, cells)
            val step = solver.findHiddenSubset(grid, 2, SudokuTechnique.HIDDEN_PAIR)!!
            val evidence = step.evidence as StepEvidence.Subset
            assertTrue(evidence.hidden)
            assertEquals(house, evidence.house)
            assertEquals(DigitSet.of(1, 2), evidence.digits)
            assertEquals(cells.sorted(), evidence.cells)
            assertEquals(2, step.actions.size)
            assertTrue(eliminations(step).all { it.digits == DigitSet.of(3, 4, 5, 6, 7, 8, 9) })
        }
    }

    @Test
    fun hiddenPair_rejectsNoExtrasAndDigitInThirdCell() {
        val noExtras = grid()
        val house = HouseRef(HouseType.ROW, 0)
        val pairCells = listOf(CellRef(0, 0), CellRef(0, 1))
        restrictDigit(noExtras, house, 1, pairCells)
        restrictDigit(noExtras, house, 2, pairCells)
        pairCells.forEach { retain(noExtras, it, 1, 2) }
        assertNull(solver.findHiddenSubset(noExtras, 2, SudokuTechnique.HIDDEN_PAIR))

        val third = grid()
        restrictDigit(third, house, 1, pairCells)
        restrictDigit(third, house, 2, pairCells + CellRef(0, 2))
        assertNull(solver.findHiddenSubset(third, 2, SudokuTechnique.HIDDEN_PAIR))
    }

    @Test
    fun hiddenPair_selectsLowestDigitCombination() {
        val grid = grid()
        val house = HouseRef(HouseType.ROW, 0)
        restrictDigit(grid, house, 7, listOf(CellRef(0, 0), CellRef(0, 1)))
        restrictDigit(grid, house, 8, listOf(CellRef(0, 0), CellRef(0, 1)))
        restrictDigit(grid, house, 2, listOf(CellRef(0, 3), CellRef(0, 4)))
        restrictDigit(grid, house, 3, listOf(CellRef(0, 3), CellRef(0, 4)))

        val evidence = solver.findHiddenSubset(
            grid,
            2,
            SudokuTechnique.HIDDEN_PAIR
        )!!.evidence as StepEvidence.Subset
        assertEquals(DigitSet.of(2, 3), evidence.digits)
        assertEquals(listOf(CellRef(0, 3), CellRef(0, 4)), evidence.cells)
    }

    @Test
    fun nakedTriple_acceptsTwoCandidateStructureAndThreeCandidateCells() {
        val grid = grid()
        retain(grid, CellRef(0, 0), 1, 2)
        retain(grid, CellRef(0, 1), 1, 3)
        retain(grid, CellRef(0, 2), 2, 3)
        val step = solver.findNakedSubset(grid, 3, SudokuTechnique.NAKED_TRIPLE)!!
        val evidence = step.evidence as StepEvidence.Subset
        assertEquals(DigitSet.of(1, 2, 3), evidence.digits)
        assertEquals(listOf(CellRef(0, 0), CellRef(0, 1), CellRef(0, 2)), evidence.cells)

        val allThree = grid()
        for (column in 0..2) retain(allThree, CellRef(0, column), 1, 2, 3)
        assertTrue(solver.findNakedSubset(allThree, 3, SudokuTechnique.NAKED_TRIPLE) != null)
    }

    @Test
    fun nakedTriple_detectsColumnsAndBoxes() {
        val fixtures = listOf(
            listOf(CellRef(0, 0), CellRef(1, 0), CellRef(2, 0)),
            listOf(CellRef(0, 0), CellRef(0, 1), CellRef(1, 1))
        )
        for (cells in fixtures) {
            val grid = grid()
            retain(grid, cells[0], 1, 2)
            retain(grid, cells[1], 1, 3)
            retain(grid, cells[2], 2, 3)
            assertTrue(solver.findNakedSubset(grid, 3, SudokuTechnique.NAKED_TRIPLE) != null)
        }
    }

    @Test
    fun nakedTriple_rejectsUnionFourFourConfinedCellsAndNoOp() {
        val unionFour = grid()
        retain(unionFour, CellRef(0, 0), 1, 2)
        retain(unionFour, CellRef(0, 1), 1, 3)
        retain(unionFour, CellRef(0, 2), 2, 4)
        assertNull(solver.findNakedSubset(unionFour, 3, SudokuTechnique.NAKED_TRIPLE))

        val fourCells = grid()
        for (column in listOf(0, 3, 6, 8)) retain(fourCells, CellRef(0, column), 1, 2, 3)
        assertNull(solver.findNakedSubset(fourCells, 3, SudokuTechnique.NAKED_TRIPLE))

        val noOp = grid()
        retain(noOp, CellRef(0, 0), 1, 2)
        retain(noOp, CellRef(0, 3), 1, 3)
        retain(noOp, CellRef(0, 6), 2, 3)
        for (column in 0..8) {
            if (column !in listOf(0, 3, 6)) {
                noOp.eliminate(CellRef(0, column), DigitSet.of(1, 2, 3))
            }
        }
        assertNull(solver.findNakedSubset(noOp, 3, SudokuTechnique.NAKED_TRIPLE))
    }

    @Test
    fun nakedTriple_selectsCanonicalCellCombination() {
        val grid = grid()
        retain(grid, CellRef(0, 3), 4, 5)
        retain(grid, CellRef(0, 4), 4, 6)
        retain(grid, CellRef(0, 5), 5, 6)
        retain(grid, CellRef(1, 0), 1, 2)
        retain(grid, CellRef(1, 1), 1, 3)
        retain(grid, CellRef(1, 2), 2, 3)

        val evidence = solver.findNakedSubset(
            grid,
            3,
            SudokuTechnique.NAKED_TRIPLE
        )!!.evidence as StepEvidence.Subset
        assertEquals(HouseRef(HouseType.ROW, 0), evidence.house)
        assertEquals(listOf(CellRef(0, 3), CellRef(0, 4), CellRef(0, 5)), evidence.cells)
    }

    @Test
    fun hiddenTriple_detectsRowsColumnsAndBoxesAndRemovesExtras() {
        val fixtures = listOf(
            HouseRef(HouseType.ROW, 0) to listOf(CellRef(0, 0), CellRef(0, 1), CellRef(0, 2)),
            HouseRef(HouseType.COLUMN, 0) to listOf(CellRef(0, 0), CellRef(1, 0), CellRef(2, 0)),
            HouseRef(HouseType.BOX, 0) to listOf(CellRef(0, 0), CellRef(0, 1), CellRef(1, 1))
        )
        for ((house, cells) in fixtures) {
            val grid = grid()
            for (digit in 1..3) restrictDigit(grid, house, digit, cells)
            val step = solver.findHiddenSubset(grid, 3, SudokuTechnique.HIDDEN_TRIPLE)!!
            val evidence = step.evidence as StepEvidence.Subset
            assertTrue(evidence.hidden)
            assertEquals(house, evidence.house)
            assertEquals(DigitSet.of(1, 2, 3), evidence.digits)
            assertEquals(cells.sorted(), evidence.cells)
            assertEquals(3, step.actions.size)
        }
    }

    @Test
    fun hiddenTriple_rejectsPositionUnionOverThreeAndNoOp() {
        val house = HouseRef(HouseType.ROW, 0)
        val tooMany = grid()
        restrictDigit(tooMany, house, 1, listOf(CellRef(0, 0), CellRef(0, 1)))
        restrictDigit(tooMany, house, 2, listOf(CellRef(0, 1), CellRef(0, 2)))
        restrictDigit(tooMany, house, 3, listOf(CellRef(0, 2), CellRef(0, 3)))
        assertNull(solver.findHiddenSubset(tooMany, 3, SudokuTechnique.HIDDEN_TRIPLE))

        val noOp = grid()
        val cells = listOf(CellRef(0, 0), CellRef(0, 1), CellRef(0, 2))
        for (digit in 1..3) restrictDigit(noOp, house, digit, cells)
        cells.forEach { retain(noOp, it, 1, 2, 3) }
        assertNull(solver.findHiddenSubset(noOp, 3, SudokuTechnique.HIDDEN_TRIPLE))
    }

    @Test
    fun hiddenTriple_selectsLowestDigitCombination() {
        val grid = grid()
        val house = HouseRef(HouseType.ROW, 0)
        val lowCells = listOf(CellRef(0, 0), CellRef(0, 1), CellRef(0, 2))
        val highCells = listOf(CellRef(0, 4), CellRef(0, 5), CellRef(0, 6))
        for (digit in 1..3) restrictDigit(grid, house, digit, lowCells)
        for (digit in 6..8) restrictDigit(grid, house, digit, highCells)

        val evidence = solver.findHiddenSubset(
            grid,
            3,
            SudokuTechnique.HIDDEN_TRIPLE
        )!!.evidence as StepEvidence.Subset
        assertEquals(DigitSet.of(1, 2, 3), evidence.digits)
        assertEquals(lowCells, evidence.cells)
    }

    @Test
    fun singlesRemainAheadOfAllEliminationTechniques() {
        val grid = grid()
        retain(grid, CellRef(8, 8), 9)
        restrictDigit(
            grid,
            HouseRef(HouseType.BOX, 0),
            5,
            listOf(CellRef(0, 0), CellRef(0, 1))
        )

        assertEquals(SudokuTechnique.NAKED_SINGLE, solver.nextStep(grid)!!.technique)
    }

    @Test
    fun nextStep_pointingPrecedesClaimingAndNakedPair() {
        val grid = grid()
        restrictDigit(
            grid,
            HouseRef(HouseType.BOX, 0),
            5,
            listOf(CellRef(0, 0), CellRef(0, 1))
        )
        restrictDigit(
            grid,
            HouseRef(HouseType.ROW, 8),
            6,
            listOf(CellRef(8, 6), CellRef(8, 7))
        )
        retain(grid, CellRef(4, 0), 1, 2)
        retain(grid, CellRef(4, 4), 1, 2)

        assertEquals(
            SudokuTechnique.LOCKED_CANDIDATES_POINTING,
            solver.nextStep(grid)!!.technique
        )
    }

    @Test
    fun nextStep_claimingPrecedesNakedPairAndNakedPairPrecedesHiddenPair() {
        val claimingGrid = grid()
        restrictDigit(
            claimingGrid,
            HouseRef(HouseType.ROW, 8),
            6,
            listOf(CellRef(8, 6), CellRef(8, 7))
        )
        retain(claimingGrid, CellRef(4, 0), 1, 2)
        retain(claimingGrid, CellRef(4, 4), 1, 2)
        assertEquals(
            SudokuTechnique.LOCKED_CANDIDATES_CLAIMING,
            solver.nextStep(claimingGrid)!!.technique
        )

        val pairGrid = grid()
        retain(pairGrid, CellRef(0, 0), 1, 2)
        retain(pairGrid, CellRef(0, 4), 1, 2)
        val hiddenCells = listOf(CellRef(1, 0), CellRef(1, 4))
        restrictDigit(pairGrid, HouseRef(HouseType.ROW, 1), 3, hiddenCells)
        restrictDigit(pairGrid, HouseRef(HouseType.ROW, 1), 4, hiddenCells)
        assertEquals(SudokuTechnique.NAKED_PAIR, solver.nextStep(pairGrid)!!.technique)
    }

    @Test
    fun nextStep_hiddenPairPrecedesNakedTripleWhichPrecedesHiddenTriple() {
        val pairBeforeTriple = grid()
        val hiddenPairCells = listOf(CellRef(0, 0), CellRef(0, 4))
        restrictDigit(pairBeforeTriple, HouseRef(HouseType.ROW, 0), 1, hiddenPairCells)
        restrictDigit(pairBeforeTriple, HouseRef(HouseType.ROW, 0), 2, hiddenPairCells)
        retain(pairBeforeTriple, CellRef(1, 0), 3, 4)
        retain(pairBeforeTriple, CellRef(1, 3), 3, 5)
        retain(pairBeforeTriple, CellRef(1, 6), 4, 5)
        assertEquals(SudokuTechnique.HIDDEN_PAIR, solver.nextStep(pairBeforeTriple)!!.technique)

        val nakedBeforeHiddenTriple = grid()
        retain(nakedBeforeHiddenTriple, CellRef(0, 0), 1, 2)
        retain(nakedBeforeHiddenTriple, CellRef(0, 3), 1, 3)
        retain(nakedBeforeHiddenTriple, CellRef(0, 6), 2, 3)
        val hiddenTripleCells = listOf(CellRef(1, 0), CellRef(1, 3), CellRef(1, 6))
        for (digit in 6..8) {
            restrictDigit(
                nakedBeforeHiddenTriple,
                HouseRef(HouseType.ROW, 1),
                digit,
                hiddenTripleCells
            )
        }
        assertEquals(
            SudokuTechnique.NAKED_TRIPLE,
            solver.nextStep(nakedBeforeHiddenTriple)!!.technique
        )

        val hiddenOnly = grid()
        for (digit in 6..8) {
            restrictDigit(hiddenOnly, HouseRef(HouseType.ROW, 1), digit, hiddenTripleCells)
        }
        assertEquals(SudokuTechnique.HIDDEN_TRIPLE, solver.nextStep(hiddenOnly)!!.technique)
    }
}
