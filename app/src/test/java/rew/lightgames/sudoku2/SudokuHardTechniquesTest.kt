package rew.lightgames.sudoku2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SudokuHardTechniquesTest {
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

    private fun rowXWing(
        grid: CandidateGrid,
        digit: Int = 5,
        rows: List<Int> = listOf(0, 3),
        columns: List<Int> = listOf(1, 4)
    ) {
        rows.forEach { row ->
            restrictDigit(
                grid,
                HouseRef(HouseType.ROW, row),
                digit,
                columns.map { column -> CellRef(row, column) }
            )
        }
    }

    private fun columnXWing(
        grid: CandidateGrid,
        digit: Int = 6,
        columns: List<Int> = listOf(0, 4),
        rows: List<Int> = listOf(2, 7)
    ) {
        columns.forEach { column ->
            restrictDigit(
                grid,
                HouseRef(HouseType.COLUMN, column),
                digit,
                rows.map { row -> CellRef(row, column) }
            )
        }
    }

    private fun standardXYWing(grid: CandidateGrid): List<CellRef> {
        val pivot = CellRef(1, 1)
        val first = CellRef(1, 5)
        val second = CellRef(5, 1)
        retain(grid, pivot, 1, 2)
        retain(grid, first, 1, 3)
        retain(grid, second, 2, 3)
        return listOf(pivot, first, second)
    }

    private fun eliminations(step: LogicalStep): List<SolveAction.EliminateCandidates> =
        step.actions.map { it as SolveAction.EliminateCandidates }

    @Test
    fun xWing_rowBasedEmitsAllTargetsAndPreservesFourSupports() {
        val grid = grid()
        rowXWing(grid)
        val before = grid.snapshot()

        val step = solver.findXWing(grid)!!
        val evidence = step.evidence as StepEvidence.Fish
        assertEquals(SudokuTechnique.X_WING, step.technique)
        assertEquals(5, evidence.digit)
        assertEquals(
            listOf(HouseRef(HouseType.ROW, 0), HouseRef(HouseType.ROW, 3)),
            evidence.baseHouses
        )
        assertEquals(
            listOf(HouseRef(HouseType.COLUMN, 1), HouseRef(HouseType.COLUMN, 4)),
            evidence.coverHouses
        )
        val support = listOf(CellRef(0, 1), CellRef(0, 4), CellRef(3, 1), CellRef(3, 4))
        assertEquals(support, evidence.cells)
        assertEquals(
            (0..8).flatMap { row -> listOf(CellRef(row, 1), CellRef(row, 4)) }
                .filter { it !in support }.sorted(),
            eliminations(step).map { it.cell }
        )
        assertTrue(eliminations(step).all { it.digits == DigitSet.of(5) })
        assertEquals(before, grid.snapshot())

        assertEquals(CandidateGridMutationResult.Success, grid.applyActions(step.actions))
        support.forEach { assertTrue(5 in grid.candidatesAt(it)) }
        eliminations(step).forEach { assertFalse(5 in grid.candidatesAt(it.cell)) }
    }

    @Test
    fun xWing_columnBasedEmitsCanonicalTargetsAndIsDeterministic() {
        val grid = grid()
        columnXWing(grid)
        val before = grid.snapshot()

        val first = solver.findXWing(grid)!!
        val repeated = solver.findXWing(grid)
        val evidence = first.evidence as StepEvidence.Fish
        assertEquals(first, repeated)
        assertEquals(before, grid.snapshot())
        assertEquals(
            listOf(HouseRef(HouseType.COLUMN, 0), HouseRef(HouseType.COLUMN, 4)),
            evidence.baseHouses
        )
        assertEquals(
            listOf(HouseRef(HouseType.ROW, 2), HouseRef(HouseType.ROW, 7)),
            evidence.coverHouses
        )
    }

    @Test
    fun xWing_rejectsThreeCandidatesMismatchedCoversAndSingleBase() {
        val three = grid()
        restrictDigit(
            three,
            HouseRef(HouseType.ROW, 0),
            5,
            listOf(CellRef(0, 1), CellRef(0, 4), CellRef(0, 7))
        )
        restrictDigit(
            three,
            HouseRef(HouseType.ROW, 3),
            5,
            listOf(CellRef(3, 1), CellRef(3, 4))
        )
        assertNull(solver.findXWing(three))

        val mismatch = grid()
        restrictDigit(
            mismatch,
            HouseRef(HouseType.ROW, 0),
            5,
            listOf(CellRef(0, 1), CellRef(0, 4))
        )
        restrictDigit(
            mismatch,
            HouseRef(HouseType.ROW, 3),
            5,
            listOf(CellRef(3, 1), CellRef(3, 7))
        )
        assertNull(solver.findXWing(mismatch))

        val single = grid()
        restrictDigit(
            single,
            HouseRef(HouseType.ROW, 0),
            5,
            listOf(CellRef(0, 1), CellRef(0, 4))
        )
        assertNull(solver.findXWing(single))
    }

    @Test
    fun xWing_rejectsNoOpAndCandidateStateBrokenPattern() {
        val noOp = grid()
        rowXWing(noOp)
        val support = listOf(CellRef(0, 1), CellRef(0, 4), CellRef(3, 1), CellRef(3, 4))
        for (row in 0..8) {
            for (column in listOf(1, 4)) {
                val cell = CellRef(row, column)
                if (cell !in support) noOp.eliminate(cell, DigitSet.of(5))
            }
        }
        assertNull(solver.findXWing(noOp))

        val broken = grid()
        rowXWing(broken)
        broken.eliminate(CellRef(3, 4), DigitSet.of(5))
        assertNull(solver.findXWing(broken))
    }

    @Test
    fun xWing_columnFormRejectsThreeCandidatesMismatchedRowsAndNoOp() {
        val three = grid()
        restrictDigit(
            three,
            HouseRef(HouseType.COLUMN, 0),
            6,
            listOf(CellRef(1, 0), CellRef(4, 0), CellRef(7, 0))
        )
        restrictDigit(
            three,
            HouseRef(HouseType.COLUMN, 4),
            6,
            listOf(CellRef(1, 4), CellRef(4, 4))
        )
        assertNull(solver.findXWing(three))

        val mismatch = grid()
        restrictDigit(
            mismatch,
            HouseRef(HouseType.COLUMN, 0),
            6,
            listOf(CellRef(1, 0), CellRef(4, 0))
        )
        restrictDigit(
            mismatch,
            HouseRef(HouseType.COLUMN, 4),
            6,
            listOf(CellRef(1, 4), CellRef(7, 4))
        )
        assertNull(solver.findXWing(mismatch))

        val noOp = grid()
        columnXWing(noOp)
        val support = listOf(CellRef(2, 0), CellRef(2, 4), CellRef(7, 0), CellRef(7, 4))
        for (row in listOf(2, 7)) {
            for (column in 0..8) {
                val cell = CellRef(row, column)
                if (cell !in support) noOp.eliminate(cell, DigitSet.of(6))
            }
        }
        assertNull(solver.findXWing(noOp))
    }

    @Test
    fun xWing_usesOrientationThenDigitThenBasePairOrdering() {
        val orientation = grid()
        columnXWing(orientation, digit = 1)
        rowXWing(orientation, digit = 8)
        val rowEvidence = solver.findXWing(orientation)!!.evidence as StepEvidence.Fish
        assertEquals(HouseType.ROW, rowEvidence.baseHouses.first().type)
        assertEquals(8, rowEvidence.digit)

        val digit = grid()
        rowXWing(digit, digit = 8, rows = listOf(0, 3), columns = listOf(1, 4))
        rowXWing(digit, digit = 2, rows = listOf(4, 7), columns = listOf(2, 8))
        assertEquals(2, (solver.findXWing(digit)!!.evidence as StepEvidence.Fish).digit)

        val pair = grid()
        rowXWing(pair, digit = 4, rows = listOf(3, 7), columns = listOf(1, 5))
        rowXWing(pair, digit = 4, rows = listOf(0, 8), columns = listOf(2, 6))
        assertEquals(
            listOf(HouseRef(HouseType.ROW, 0), HouseRef(HouseType.ROW, 8)),
            (solver.findXWing(pair)!!.evidence as StepEvidence.Fish).baseHouses
        )
    }

    @Test
    fun xyWing_standardMixedGeometryFindsOnlyCommonPeerTarget() {
        val grid = grid()
        val support = standardXYWing(grid)
        val before = grid.snapshot()

        val step = solver.findXYWing(grid)!!
        val evidence = step.evidence as StepEvidence.XYWing
        assertEquals(SudokuTechnique.XY_WING, step.technique)
        assertEquals(support.first(), evidence.pivot)
        assertEquals(support.drop(1), evidence.pincers)
        assertEquals(DigitSet.of(1, 2), evidence.pivotDigits)
        assertEquals(3, evidence.eliminationDigit)
        assertEquals(listOf(CellRef(5, 5)), eliminations(step).map { it.cell })
        assertEquals(before, grid.snapshot())
    }

    @Test
    fun xyWing_pincersNeedNotSeeEachOtherAndCanHaveMultipleTargets() {
        val nonPeer = grid()
        val support = standardXYWing(nonPeer)
        assertFalse(support[2] in nonPeer.peersOf(support[1]))
        assertEquals(listOf(CellRef(5, 5)), eliminations(solver.findXYWing(nonPeer)!!).map { it.cell })

        val multiple = grid()
        val pivot = CellRef(1, 1)
        val first = CellRef(0, 1)
        val second = CellRef(0, 2)
        retain(multiple, pivot, 1, 2)
        retain(multiple, first, 1, 3)
        retain(multiple, second, 2, 3)
        val targets = eliminations(solver.findXYWing(multiple)!!).map { it.cell }
        assertTrue(targets.size > 1)
        assertTrue(targets.all { it in multiple.peersOf(first) && it in multiple.peersOf(second) })
    }

    @Test
    fun xyWing_rejectsNonBivaluePivotAndPincer() {
        val pivotThree = grid()
        retain(pivotThree, CellRef(1, 1), 1, 2, 4)
        retain(pivotThree, CellRef(1, 5), 1, 3)
        retain(pivotThree, CellRef(5, 1), 2, 3)
        assertNull(solver.findXYWing(pivotThree))

        val pincerThree = grid()
        retain(pincerThree, CellRef(1, 1), 1, 2)
        retain(pincerThree, CellRef(1, 5), 1, 3, 4)
        retain(pincerThree, CellRef(5, 1), 2, 3)
        assertNull(solver.findXYWing(pincerThree))
    }

    @Test
    fun xyWing_rejectsWrongSharedDigitsAndPivotContainingThirdDigit() {
        val samePivotDigit = grid()
        retain(samePivotDigit, CellRef(1, 1), 1, 2)
        retain(samePivotDigit, CellRef(1, 5), 1, 3)
        retain(samePivotDigit, CellRef(5, 1), 1, 3)
        assertNull(solver.findXYWing(samePivotDigit))

        val differentThird = grid()
        retain(differentThird, CellRef(1, 1), 1, 2)
        retain(differentThird, CellRef(1, 5), 1, 3)
        retain(differentThird, CellRef(5, 1), 2, 4)
        assertNull(solver.findXYWing(differentThird))

        val pivotDigitAsThird = grid()
        retain(pivotDigitAsThird, CellRef(1, 1), 1, 2)
        retain(pivotDigitAsThird, CellRef(1, 5), 1, 2)
        retain(pivotDigitAsThird, CellRef(5, 1), 2, 3)
        assertNull(solver.findXYWing(pivotDigitAsThird))
    }

    @Test
    fun xyWing_rejectsMissingPincerPeerAndNoCommonTarget() {
        val missingPeer = grid()
        retain(missingPeer, CellRef(1, 1), 1, 2)
        retain(missingPeer, CellRef(1, 5), 1, 3)
        retain(missingPeer, CellRef(6, 7), 2, 3)
        assertNull(solver.findXYWing(missingPeer))

        val noTarget = grid()
        val support = standardXYWing(noTarget)
        val firstPeers = noTarget.peersOf(support[1])
        val secondPeers = noTarget.peersOf(support[2])
        noTarget.cellsRowMajor()
            .filter { it !in support && it in firstPeers && it in secondPeers }
            .forEach { noTarget.eliminate(it, DigitSet.of(3)) }
        assertNull(solver.findXYWing(noTarget))
        assertTrue(3 in noTarget.candidatesAt(CellRef(0, 5)))
        assertTrue(CellRef(0, 5) in firstPeers)
        assertFalse(CellRef(0, 5) in secondPeers)
    }

    @Test
    fun xyWing_normalizesPincersAndSelectsLowestPivot() {
        val grid = grid()
        val lowPivot = CellRef(0, 0)
        retain(grid, lowPivot, 4, 5)
        retain(grid, CellRef(0, 4), 4, 6)
        retain(grid, CellRef(4, 0), 5, 6)
        standardXYWing(grid)

        val step = solver.findXYWing(grid)!!
        val evidence = step.evidence as StepEvidence.XYWing
        assertEquals(lowPivot, evidence.pivot)
        assertEquals(evidence.pincers.sorted(), evidence.pincers)
        assertEquals(step, solver.findXYWing(grid))
    }

    @Test
    fun hardEvidenceHasDefensiveStructuralEqualityAndStableDiagnostics() {
        val bases = mutableListOf(HouseRef(HouseType.ROW, 3), HouseRef(HouseType.ROW, 0))
        val covers = mutableListOf(HouseRef(HouseType.COLUMN, 4), HouseRef(HouseType.COLUMN, 1))
        val cells = mutableListOf(CellRef(3, 4), CellRef(0, 1), CellRef(3, 1), CellRef(0, 4))
        val fish = StepEvidence.Fish(5, bases, covers, cells)
        val equalFish = StepEvidence.Fish(5, bases.reversed(), covers.reversed(), cells.reversed())
        bases.clear()
        covers.clear()
        cells.clear()
        assertEquals(equalFish, fish)
        assertEquals(equalFish.hashCode(), fish.hashCode())
        assertTrue(fish.toString().contains("digit=5"))

        val pincers = mutableListOf(CellRef(5, 1), CellRef(1, 5))
        val wing = StepEvidence.XYWing(CellRef(1, 1), pincers, DigitSet.of(1, 2), 3)
        val equalWing = StepEvidence.XYWing(
            CellRef(1, 1),
            pincers.reversed(),
            DigitSet.of(1, 2),
            3
        )
        pincers.clear()
        assertEquals(equalWing, wing)
        assertEquals(equalWing.hashCode(), wing.hashCode())
        assertNotEquals(wing, StepEvidence.XYWing(CellRef(1, 1), equalWing.pincers, DigitSet.of(1, 2), 4))
        assertTrue(wing.toString().contains("eliminationDigit=3"))
    }

    @Test
    fun nextStep_preservesEarlierPrecedenceAndXWingBeatsXYWing() {
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
                for (digit in 6..8) {
                    restrictDigit(it, HouseRef(HouseType.ROW, 7), digit, cells)
                }
            }
        )
        for ((expected, fixture) in earlierFixtures) {
            rowXWing(fixture, digit = 9, rows = listOf(0, 3), columns = listOf(1, 4))
            assertEquals(expected, solver.nextStep(fixture)!!.technique)
        }

        val bothHard = grid()
        rowXWing(bothHard, digit = 5, rows = listOf(0, 3), columns = listOf(1, 4))
        retain(bothHard, CellRef(5, 5), 6, 7)
        retain(bothHard, CellRef(5, 8), 6, 8)
        retain(bothHard, CellRef(8, 5), 7, 8)
        assertEquals(SudokuTechnique.X_WING, solver.nextStep(bothHard)!!.technique)
    }
}
