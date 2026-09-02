package rew.lightgames.sudoku2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class SudokuSkyscraperTest {
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

    private fun rowSkyscraper(
        grid: CandidateGrid,
        digit: Int = 5,
        rows: List<Int> = listOf(0, 4),
        alignedColumn: Int = 1,
        towerColumns: List<Int> = listOf(4, 5)
    ) {
        rows.zip(towerColumns).forEach { (row, towerColumn) ->
            restrictDigit(
                grid,
                HouseRef(HouseType.ROW, row),
                digit,
                listOf(CellRef(row, alignedColumn), CellRef(row, towerColumn))
            )
        }
    }

    private fun columnSkyscraper(
        grid: CandidateGrid,
        digit: Int = 6,
        columns: List<Int> = listOf(0, 4),
        alignedRow: Int = 1,
        towerRows: List<Int> = listOf(4, 5)
    ) {
        columns.zip(towerRows).forEach { (column, towerRow) ->
            restrictDigit(
                grid,
                HouseRef(HouseType.COLUMN, column),
                digit,
                listOf(CellRef(alignedRow, column), CellRef(towerRow, column))
            )
        }
    }

    private fun rowXWing(grid: CandidateGrid, digit: Int = 8) {
        for (row in listOf(2, 6)) {
            restrictDigit(
                grid,
                HouseRef(HouseType.ROW, row),
                digit,
                listOf(CellRef(row, 2), CellRef(row, 6))
            )
        }
    }

    private fun xyWing(grid: CandidateGrid) {
        retain(grid, CellRef(1, 1), 1, 2)
        retain(grid, CellRef(1, 5), 1, 3)
        retain(grid, CellRef(5, 1), 2, 3)
    }

    private fun eliminations(step: LogicalStep): List<SolveAction.EliminateCandidates> =
        step.actions.map { it as SolveAction.EliminateCandidates }

    @Test
    fun rowSkyscraper_emitsMultipleMixedGeometryTargetsAndPreservesSupport() {
        val grid = grid()
        rowSkyscraper(grid)
        val before = grid.snapshot()

        val step = solver.findSkyscraper(grid)!!
        val evidence = step.evidence as StepEvidence.Skyscraper
        assertEquals(SudokuTechnique.SKYSCRAPER, step.technique)
        assertEquals(5, evidence.digit)
        assertEquals(HouseType.ROW, evidence.orientation)
        assertEquals(
            listOf(HouseRef(HouseType.ROW, 0), HouseRef(HouseType.ROW, 4)),
            evidence.sourceHouses
        )
        assertEquals(listOf(CellRef(0, 1), CellRef(4, 1)), evidence.alignedCells)
        assertEquals(listOf(CellRef(0, 4), CellRef(4, 5)), evidence.towers)
        assertEquals(
            listOf(CellRef(1, 5), CellRef(2, 5), CellRef(3, 4), CellRef(5, 4)),
            eliminations(step).map { it.cell }
        )
        assertEquals(before, grid.snapshot())

        assertEquals(CandidateGridMutationResult.Success, grid.applyActions(step.actions))
        (evidence.alignedCells + evidence.towers).forEach { assertTrue(5 in grid.candidatesAt(it)) }
        eliminations(step).forEach { assertFalse(5 in grid.candidatesAt(it.cell)) }
    }

    @Test
    fun rowSkyscraper_canEmitExactlyOneElimination() {
        val grid = grid()
        rowSkyscraper(grid)
        listOf(CellRef(1, 5), CellRef(2, 5), CellRef(3, 4)).forEach {
            grid.eliminate(it, DigitSet.of(5))
        }

        assertEquals(listOf(CellRef(5, 4)), eliminations(solver.findSkyscraper(grid)!!).map { it.cell })
    }

    @Test
    fun columnSkyscraper_emitsMultipleMixedGeometryTargetsAndPreservesSupport() {
        val grid = grid()
        columnSkyscraper(grid)

        val step = solver.findSkyscraper(grid)!!
        val evidence = step.evidence as StepEvidence.Skyscraper
        assertEquals(6, evidence.digit)
        assertEquals(HouseType.COLUMN, evidence.orientation)
        assertEquals(
            listOf(HouseRef(HouseType.COLUMN, 0), HouseRef(HouseType.COLUMN, 4)),
            evidence.sourceHouses
        )
        assertEquals(listOf(CellRef(1, 0), CellRef(1, 4)), evidence.alignedCells)
        assertEquals(listOf(CellRef(4, 0), CellRef(5, 4)), evidence.towers)
        assertEquals(
            listOf(CellRef(4, 3), CellRef(4, 5), CellRef(5, 1), CellRef(5, 2)),
            eliminations(step).map { it.cell }
        )

        assertEquals(CandidateGridMutationResult.Success, grid.applyActions(step.actions))
        (evidence.alignedCells + evidence.towers).forEach { assertTrue(6 in grid.candidatesAt(it)) }
    }

    @Test
    fun columnSkyscraper_canEmitExactlyOneElimination() {
        val grid = grid()
        columnSkyscraper(grid)
        listOf(CellRef(4, 3), CellRef(4, 5), CellRef(5, 1)).forEach {
            grid.eliminate(it, DigitSet.of(6))
        }

        assertEquals(listOf(CellRef(5, 2)), eliminations(solver.findSkyscraper(grid)!!).map { it.cell })
    }

    @Test
    fun skyscraper_rejectsSourcesWithMoreThanTwoCandidates() {
        val row = grid()
        restrictDigit(
            row,
            HouseRef(HouseType.ROW, 0),
            5,
            listOf(CellRef(0, 1), CellRef(0, 4), CellRef(0, 8))
        )
        restrictDigit(
            row,
            HouseRef(HouseType.ROW, 4),
            5,
            listOf(CellRef(4, 1), CellRef(4, 7))
        )
        assertNull(solver.findSkyscraper(row))

        val column = grid()
        restrictDigit(
            column,
            HouseRef(HouseType.COLUMN, 0),
            6,
            listOf(CellRef(1, 0), CellRef(4, 0), CellRef(8, 0))
        )
        restrictDigit(
            column,
            HouseRef(HouseType.COLUMN, 4),
            6,
            listOf(CellRef(1, 4), CellRef(7, 4))
        )
        assertNull(solver.findSkyscraper(column))
    }

    @Test
    fun skyscraper_rejectsNoAlignmentAndMalformedGeometry() {
        val grid = grid()
        restrictDigit(
            grid,
            HouseRef(HouseType.ROW, 0),
            5,
            listOf(CellRef(0, 1), CellRef(0, 4))
        )
        restrictDigit(
            grid,
            HouseRef(HouseType.ROW, 4),
            5,
            listOf(CellRef(4, 2), CellRef(4, 7))
        )
        assertNull(solver.findSkyscraper(grid))
    }

    @Test
    fun skyscraper_rejectsXWingWithBothCandidatesAligned() {
        val row = grid()
        for (rowIndex in listOf(0, 4)) {
            restrictDigit(
                row,
                HouseRef(HouseType.ROW, rowIndex),
                5,
                listOf(CellRef(rowIndex, 1), CellRef(rowIndex, 4))
            )
        }
        assertNull(solver.findSkyscraper(row))

        val column = grid()
        for (columnIndex in listOf(0, 4)) {
            restrictDigit(
                column,
                HouseRef(HouseType.COLUMN, columnIndex),
                6,
                listOf(CellRef(1, columnIndex), CellRef(4, columnIndex))
            )
        }
        assertNull(solver.findSkyscraper(column))
    }

    @Test
    fun skyscraper_rejectsTargetSeeingOnlyOneTowerAndNoCommonTarget() {
        val grid = grid()
        rowSkyscraper(grid)
        listOf(CellRef(1, 5), CellRef(2, 5), CellRef(3, 4), CellRef(5, 4)).forEach {
            grid.eliminate(it, DigitSet.of(5))
        }

        assertNull(solver.findSkyscraper(grid))
        assertTrue(5 in grid.candidatesAt(CellRef(6, 4)))
        assertTrue(CellRef(6, 4) in grid.peersOf(CellRef(0, 4)))
        assertFalse(CellRef(6, 4) in grid.peersOf(CellRef(4, 5)))
    }

    @Test
    fun skyscraper_rejectsSolvedSupportCellAndIsPure() {
        val grid = grid()
        rowSkyscraper(grid)
        assertEquals(CandidateGridMutationResult.Success, grid.place(CellRef(0, 1), 5))
        val before = grid.snapshot()

        assertNull(solver.findSkyscraper(grid))
        assertEquals(before, grid.snapshot())
    }

    @Test
    fun skyscraper_usesOrientationDigitBaseAndTowerCanonicalOrder() {
        val orientation = grid()
        columnSkyscraper(orientation, digit = 1)
        rowSkyscraper(orientation, digit = 8)
        assertEquals(
            HouseType.ROW,
            (solver.findSkyscraper(orientation)!!.evidence as StepEvidence.Skyscraper).orientation
        )

        val digit = grid()
        rowSkyscraper(digit, digit = 7, rows = listOf(0, 4))
        rowSkyscraper(
            digit,
            digit = 2,
            rows = listOf(5, 8),
            alignedColumn = 0,
            towerColumns = listOf(3, 4)
        )
        assertEquals(2, (solver.findSkyscraper(digit)!!.evidence as StepEvidence.Skyscraper).digit)

        val base = grid()
        rowSkyscraper(base, digit = 4, rows = listOf(3, 8))
        rowSkyscraper(
            base,
            digit = 4,
            rows = listOf(0, 7),
            alignedColumn = 0,
            towerColumns = listOf(3, 4)
        )
        val evidence = solver.findSkyscraper(base)!!.evidence as StepEvidence.Skyscraper
        assertEquals(listOf(HouseRef(HouseType.ROW, 0), HouseRef(HouseType.ROW, 7)), evidence.sourceHouses)
        assertEquals(evidence.towers.sorted(), evidence.towers)
    }

    @Test
    fun skyscraper_normalizesEvidenceAndRepeatedDiscovery() {
        val sources = mutableListOf(HouseRef(HouseType.ROW, 4), HouseRef(HouseType.ROW, 0))
        val aligned = mutableListOf(CellRef(4, 1), CellRef(0, 1))
        val towers = mutableListOf(CellRef(4, 5), CellRef(0, 4))
        val evidence = StepEvidence.Skyscraper(5, HouseType.ROW, sources, aligned, towers)
        val equal = StepEvidence.Skyscraper(
            5,
            HouseType.ROW,
            sources.reversed(),
            aligned.reversed(),
            towers.reversed()
        )
        sources.clear()
        aligned.clear()
        towers.clear()
        assertEquals(equal, evidence)
        assertEquals(equal.hashCode(), evidence.hashCode())
        assertNotEquals(
            evidence,
            StepEvidence.Skyscraper(
                6,
                HouseType.ROW,
                equal.sourceHouses,
                equal.alignedCells,
                equal.towers
            )
        )
        assertTrue(evidence.toString().contains("orientation=ROW"))

        val grid = grid()
        rowSkyscraper(grid)
        val before = grid.snapshot()
        assertEquals(solver.findSkyscraper(grid), solver.findSkyscraper(grid))
        assertEquals(before, grid.snapshot())
    }

    @Test
    fun skyscraperEvidence_rejectsMalformedAlignmentAndSourceOwnership() {
        assertThrows(IllegalArgumentException::class.java) {
            StepEvidence.Skyscraper(
                5,
                HouseType.ROW,
                listOf(HouseRef(HouseType.ROW, 0), HouseRef(HouseType.ROW, 4)),
                listOf(CellRef(0, 1), CellRef(4, 2)),
                listOf(CellRef(0, 4), CellRef(4, 5))
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            StepEvidence.Skyscraper(
                5,
                HouseType.ROW,
                listOf(HouseRef(HouseType.ROW, 0), HouseRef(HouseType.ROW, 4)),
                listOf(CellRef(0, 1), CellRef(4, 1)),
                listOf(CellRef(0, 4), CellRef(3, 5))
            )
        }
    }

    @Test
    fun everyEarlierTechniqueBeatsSkyscraper() {
        val earlierFixtures = listOf(
            SudokuTechnique.NAKED_SINGLE to grid().also { retain(it, CellRef(8, 8), 9) },
            SudokuTechnique.HIDDEN_SINGLE to grid().also {
                restrictDigit(it, HouseRef(HouseType.ROW, 8), 5, listOf(CellRef(8, 8)))
            },
            SudokuTechnique.LOCKED_CANDIDATES_POINTING to grid().also {
                restrictDigit(
                    it,
                    HouseRef(HouseType.BOX, 6),
                    5,
                    listOf(CellRef(6, 0), CellRef(6, 1))
                )
            },
            SudokuTechnique.LOCKED_CANDIDATES_CLAIMING to grid().also {
                restrictDigit(
                    it,
                    HouseRef(HouseType.ROW, 8),
                    6,
                    listOf(CellRef(8, 6), CellRef(8, 7))
                )
            },
            SudokuTechnique.NAKED_PAIR to grid().also {
                retain(it, CellRef(4, 0), 1, 2)
                retain(it, CellRef(4, 4), 1, 2)
            },
            SudokuTechnique.HIDDEN_PAIR to grid().also {
                val cells = listOf(CellRef(5, 0), CellRef(5, 4))
                restrictDigit(it, HouseRef(HouseType.ROW, 5), 3, cells)
                restrictDigit(it, HouseRef(HouseType.ROW, 5), 4, cells)
            },
            SudokuTechnique.NAKED_TRIPLE to grid().also {
                retain(it, CellRef(6, 0), 1, 2)
                retain(it, CellRef(6, 3), 1, 3)
                retain(it, CellRef(6, 6), 2, 3)
            },
            SudokuTechnique.HIDDEN_TRIPLE to grid().also {
                val cells = listOf(CellRef(7, 0), CellRef(7, 3), CellRef(7, 6))
                for (digit in 6..8) restrictDigit(it, HouseRef(HouseType.ROW, 7), digit, cells)
            }
        )
        earlierFixtures.forEach { (expected, fixture) ->
            rowSkyscraper(fixture, digit = 9)
            assertNotNull(solver.findSkyscraper(fixture))
            assertEquals(expected, solver.nextStep(fixture)!!.technique)
        }
    }

    @Test
    fun xWingAndXYWingBeatSkyscraper() {
        val xWing = grid()
        rowSkyscraper(xWing, digit = 9)
        rowXWing(xWing)
        assertNotNull(solver.findSkyscraper(xWing))
        assertEquals(SudokuTechnique.X_WING, solver.nextStep(xWing)!!.technique)

        val wingGrid = grid()
        rowSkyscraper(wingGrid, digit = 9)
        xyWing(wingGrid)
        assertNotNull(solver.findSkyscraper(wingGrid))
        assertEquals(SudokuTechnique.XY_WING, solver.nextStep(wingGrid)!!.technique)
    }
}
