package rew.lightgames.sudoku2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SudokuTwoStringKiteTest {
    private val solver = SudokuLogicalSolver()

    private fun grid(): CandidateGrid =
        (CandidateGrid.create(IntArray(81)) as CandidateGridCreationResult.Success).grid

    private fun retain(grid: CandidateGrid, cell: CellRef, vararg digits: Int) {
        val remove = grid.candidatesAt(cell).remove(DigitSet.of(*digits))
        if (!remove.isEmpty) assertEquals(CandidateGridMutationResult.Success, grid.eliminate(cell, remove))
    }

    private fun restrictDigit(
        grid: CandidateGrid,
        house: HouseRef,
        digit: Int,
        supportingCells: Collection<CellRef>
    ) {
        grid.cellsIn(house).filter { it !in supportingCells }.forEach { cell ->
            if (digit in grid.candidatesAt(cell)) {
                assertEquals(
                    CandidateGridMutationResult.Success,
                    grid.eliminate(cell, DigitSet.of(digit))
                )
            }
        }
    }

    private fun kite(
        grid: CandidateGrid,
        digit: Int = 5,
        row: Int = 1,
        column: Int = 2,
        rowConnectorColumn: Int = 1,
        rowOuterColumn: Int = 7,
        columnConnectorRow: Int = 2,
        columnOuterRow: Int = 7
    ) {
        restrictDigit(
            grid,
            HouseRef(HouseType.ROW, row),
            digit,
            listOf(CellRef(row, rowConnectorColumn), CellRef(row, rowOuterColumn))
        )
        restrictDigit(
            grid,
            HouseRef(HouseType.COLUMN, column),
            digit,
            listOf(CellRef(columnConnectorRow, column), CellRef(columnOuterRow, column))
        )
    }

    private fun multipleTargetKite(grid: CandidateGrid, digit: Int = 5) {
        kite(
            grid,
            digit = digit,
            row = 1,
            column = 2,
            rowConnectorColumn = 1,
            rowOuterColumn = 4,
            columnConnectorRow = 0,
            columnOuterRow = 2
        )
    }

    private fun rowXWing(grid: CandidateGrid, digit: Int = 8) {
        for (row in listOf(3, 6)) {
            restrictDigit(
                grid,
                HouseRef(HouseType.ROW, row),
                digit,
                listOf(CellRef(row, 3), CellRef(row, 6))
            )
        }
    }

    private fun xyWing(grid: CandidateGrid) {
        retain(grid, CellRef(1, 1), 1, 2)
        retain(grid, CellRef(1, 5), 1, 3)
        retain(grid, CellRef(5, 1), 2, 3)
    }

    private fun skyscraper(grid: CandidateGrid, digit: Int = 7) {
        restrictDigit(
            grid,
            HouseRef(HouseType.ROW, 0),
            digit,
            listOf(CellRef(0, 1), CellRef(0, 4))
        )
        restrictDigit(
            grid,
            HouseRef(HouseType.ROW, 4),
            digit,
            listOf(CellRef(4, 1), CellRef(4, 5))
        )
    }

    private fun eliminations(step: LogicalStep): List<SolveAction.EliminateCandidates> =
        step.actions.map { it as SolveAction.EliminateCandidates }

    @Test
    fun twoStringKite_detectsBasicPatternAndPreservesSupport() {
        val grid = grid()
        kite(grid)
        val before = grid.snapshot()

        val step = solver.findTwoStringKite(grid)!!
        val evidence = step.evidence as StepEvidence.TwoStringKite
        assertEquals(SudokuTechnique.TWO_STRING_KITE, step.technique)
        assertEquals(5, evidence.digit)
        assertEquals(HouseRef(HouseType.ROW, 1), evidence.rowHouse)
        assertEquals(HouseRef(HouseType.COLUMN, 2), evidence.columnHouse)
        assertEquals(CellRef(1, 1), evidence.rowConnector)
        assertEquals(CellRef(2, 2), evidence.columnConnector)
        assertEquals(CellRef(1, 7), evidence.rowOuter)
        assertEquals(CellRef(7, 2), evidence.columnOuter)
        assertEquals(listOf(CellRef(7, 7)), eliminations(step).map { it.cell })
        assertTrue(evidence.columnConnector in grid.peersOf(evidence.rowConnector))
        assertEquals(before, grid.snapshot())

        assertEquals(CandidateGridMutationResult.Success, grid.applyActions(step.actions))
        listOf(
            evidence.rowConnector,
            evidence.columnConnector,
            evidence.rowOuter,
            evidence.columnOuter
        ).forEach { assertTrue(5 in grid.candidatesAt(it)) }
        assertFalse(5 in grid.candidatesAt(CellRef(7, 7)))
    }

    @Test
    fun twoStringKite_emitsMultipleCanonicalMixedGeometryTargets() {
        val grid = grid()
        multipleTargetKite(grid)

        val step = solver.findTwoStringKite(grid)!!
        val evidence = step.evidence as StepEvidence.TwoStringKite
        assertEquals(CellRef(1, 1), evidence.rowConnector)
        assertEquals(CellRef(0, 2), evidence.columnConnector)
        assertEquals(CellRef(1, 4), evidence.rowOuter)
        assertEquals(CellRef(2, 2), evidence.columnOuter)
        assertEquals(
            listOf(CellRef(2, 3), CellRef(2, 4), CellRef(2, 5)),
            eliminations(step).map { it.cell }
        )
        eliminations(step).forEach { action ->
            assertTrue(action.cell in grid.peersOf(evidence.rowOuter))
            assertTrue(action.cell in grid.peersOf(evidence.columnOuter))
        }
    }

    @Test
    fun twoStringKite_canEmitExactlyOneElimination() {
        val grid = grid()
        multipleTargetKite(grid)
        grid.eliminate(CellRef(2, 3), DigitSet.of(5))
        grid.eliminate(CellRef(2, 4), DigitSet.of(5))

        assertEquals(
            listOf(CellRef(2, 5)),
            eliminations(solver.findTwoStringKite(grid)!!).map { it.cell }
        )
    }

    @Test
    fun twoStringKite_rejectsRowsAndColumnsWithoutExactlyTwoCandidates() {
        val rowThree = grid()
        restrictDigit(
            rowThree,
            HouseRef(HouseType.ROW, 1),
            5,
            listOf(CellRef(1, 1), CellRef(1, 4), CellRef(1, 7))
        )
        restrictDigit(
            rowThree,
            HouseRef(HouseType.COLUMN, 2),
            5,
            listOf(CellRef(2, 2), CellRef(7, 2))
        )
        assertNull(solver.findTwoStringKite(rowThree))

        val columnThree = grid()
        restrictDigit(
            columnThree,
            HouseRef(HouseType.ROW, 1),
            5,
            listOf(CellRef(1, 1), CellRef(1, 7))
        )
        restrictDigit(
            columnThree,
            HouseRef(HouseType.COLUMN, 2),
            5,
            listOf(CellRef(0, 2), CellRef(2, 2), CellRef(7, 2))
        )
        assertNull(solver.findTwoStringKite(columnThree))

        val tooFew = grid()
        restrictDigit(tooFew, HouseRef(HouseType.ROW, 1), 5, listOf(CellRef(1, 1)))
        restrictDigit(
            tooFew,
            HouseRef(HouseType.COLUMN, 2),
            5,
            listOf(CellRef(2, 2), CellRef(7, 2))
        )
        assertNull(solver.findTwoStringKite(tooFew))
    }

    @Test
    fun twoStringKite_rejectsConnectorsInDifferentBoxes() {
        val grid = grid()
        restrictDigit(
            grid,
            HouseRef(HouseType.ROW, 1),
            5,
            listOf(CellRef(1, 1), CellRef(1, 4))
        )
        restrictDigit(
            grid,
            HouseRef(HouseType.COLUMN, 8),
            5,
            listOf(CellRef(4, 8), CellRef(7, 8))
        )
        assertNull(solver.findTwoStringKite(grid))
    }

    @Test
    fun twoStringKite_rejectsWhenOnlySingleEndpointPeersRemain() {
        val grid = grid()
        kite(grid)
        grid.eliminate(CellRef(7, 7), DigitSet.of(5))

        assertNull(solver.findTwoStringKite(grid))
        assertTrue(5 in grid.candidatesAt(CellRef(0, 7)))
        assertTrue(CellRef(0, 7) in grid.peersOf(CellRef(1, 7)))
        assertFalse(CellRef(0, 7) in grid.peersOf(CellRef(7, 2)))
        assertTrue(5 in grid.candidatesAt(CellRef(7, 0)))
        assertTrue(CellRef(7, 0) in grid.peersOf(CellRef(7, 2)))
        assertFalse(CellRef(7, 0) in grid.peersOf(CellRef(1, 7)))
    }

    @Test
    fun twoStringKite_rejectsMissingAndSolvedTarget() {
        val missing = grid()
        kite(missing)
        missing.eliminate(CellRef(7, 7), DigitSet.of(5))
        assertNull(solver.findTwoStringKite(missing))

        val solved = grid()
        kite(solved)
        assertEquals(CandidateGridMutationResult.Success, solved.place(CellRef(7, 7), 1))
        assertNull(solver.findTwoStringKite(solved))
    }

    @Test
    fun twoStringKite_neverTargetsSupportAndIsPure() {
        val grid = grid()
        multipleTargetKite(grid)
        val before = grid.snapshot()
        val step = solver.findTwoStringKite(grid)!!
        val evidence = step.evidence as StepEvidence.TwoStringKite
        val support = listOf(
            evidence.rowConnector,
            evidence.columnConnector,
            evidence.rowOuter,
            evidence.columnOuter
        )
        assertTrue(step.actions.none { it.cell in support })
        assertEquals(before, grid.snapshot())
    }

    @Test
    fun twoStringKite_usesCanonicalDigitRowAndColumnOrdering() {
        val digit = grid()
        kite(digit, digit = 7)
        kite(
            digit,
            digit = 2,
            row = 4,
            column = 5,
            rowConnectorColumn = 4,
            rowOuterColumn = 8,
            columnConnectorRow = 5,
            columnOuterRow = 8
        )
        assertEquals(2, (solver.findTwoStringKite(digit)!!.evidence as StepEvidence.TwoStringKite).digit)

        val row = grid()
        kite(row, digit = 4, row = 5, column = 0, columnConnectorRow = 4, columnOuterRow = 8)
        kite(
            row,
            digit = 4,
            row = 1,
            column = 2,
            rowConnectorColumn = 1,
            rowOuterColumn = 7,
            columnConnectorRow = 2,
            columnOuterRow = 7
        )
        assertEquals(1, (solver.findTwoStringKite(row)!!.evidence as StepEvidence.TwoStringKite).rowHouse.index)

        val column = grid()
        restrictDigit(
            column,
            HouseRef(HouseType.ROW, 1),
            6,
            listOf(CellRef(1, 1), CellRef(1, 7))
        )
        restrictDigit(
            column,
            HouseRef(HouseType.COLUMN, 8),
            6,
            listOf(CellRef(2, 8), CellRef(7, 8))
        )
        restrictDigit(
            column,
            HouseRef(HouseType.COLUMN, 2),
            6,
            listOf(CellRef(2, 2), CellRef(7, 2))
        )
        assertEquals(
            2,
            (solver.findTwoStringKite(column)!!.evidence as StepEvidence.TwoStringKite)
                .columnHouse.index
        )
    }

    @Test
    fun twoStringKite_normalizesConnectorChoiceAndRepeatedDiscovery() {
        val grid = grid()
        multipleTargetKite(grid)
        val before = grid.snapshot()

        val first = solver.findTwoStringKite(grid)!!
        val repeated = solver.findTwoStringKite(grid)
        val evidence = first.evidence as StepEvidence.TwoStringKite
        assertEquals(CellRef(1, 1), evidence.rowConnector)
        assertEquals(CellRef(0, 2), evidence.columnConnector)
        assertEquals(first, repeated)
        assertEquals(before, grid.snapshot())
        assertEquals(first.actions.sortedWith(SOLVE_ACTION_COMPARATOR), first.actions)

        assertEquals(CandidateGridMutationResult.Success, grid.applyActions(first.actions))
        assertNull(
            "The same four supports must not be rediscovered through an alternate connector",
            solver.findTwoStringKite(grid)
        )
    }

    @Test
    fun twoStringKiteEvidence_isStructuralAndRejectsMalformedGeometry() {
        val evidence = StepEvidence.TwoStringKite(
            5,
            HouseRef(HouseType.ROW, 1),
            HouseRef(HouseType.COLUMN, 2),
            CellRef(1, 1),
            CellRef(2, 2),
            CellRef(1, 7),
            CellRef(7, 2)
        )
        val equal = evidence.copy()
        assertEquals(equal, evidence)
        assertEquals(equal.hashCode(), evidence.hashCode())
        assertTrue(evidence.toString().contains("digit=5"))
        assertNotEquals(evidence, evidence.copy(digit = 6))

        assertThrows(IllegalArgumentException::class.java) {
            evidence.copy(columnConnector = CellRef(4, 2))
        }
        assertThrows(IllegalArgumentException::class.java) {
            evidence.copy(rowOuter = CellRef(2, 7))
        }
    }

    @Test
    fun mediumXWingXYWingAndSkyscraperBeatTwoStringKite() {
        val medium = grid()
        kite(medium, digit = 9)
        retain(medium, CellRef(4, 0), 1, 2)
        retain(medium, CellRef(4, 4), 1, 2)
        assertNotNull(solver.findTwoStringKite(medium))
        assertEquals(SudokuTechnique.NAKED_PAIR, solver.nextStep(medium)!!.technique)

        val xWing = grid()
        kite(xWing, digit = 9)
        rowXWing(xWing)
        assertNotNull(solver.findTwoStringKite(xWing))
        assertEquals(SudokuTechnique.X_WING, solver.nextStep(xWing)!!.technique)

        val wing = grid()
        kite(
            wing,
            digit = 9,
            row = 6,
            column = 8,
            rowConnectorColumn = 7,
            rowOuterColumn = 3,
            columnConnectorRow = 7,
            columnOuterRow = 2
        )
        xyWing(wing)
        assertNotNull(solver.findTwoStringKite(wing))
        assertEquals(SudokuTechnique.XY_WING, solver.nextStep(wing)!!.technique)

        val skyscraperGrid = grid()
        kite(skyscraperGrid, digit = 9)
        skyscraper(skyscraperGrid)
        assertNotNull(solver.findTwoStringKite(skyscraperGrid))
        assertEquals(SudokuTechnique.SKYSCRAPER, solver.nextStep(skyscraperGrid)!!.technique)
    }
}
