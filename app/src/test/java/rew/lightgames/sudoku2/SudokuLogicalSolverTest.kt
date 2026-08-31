package rew.lightgames.sudoku2

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SudokuLogicalSolverTest {
    private val solver = SudokuLogicalSolver()

    private val solvedBoard = intArrayOf(
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

    private val classicSinglesPuzzle = intArrayOf(
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

    private val hiddenRequiredPuzzle = intArrayOf(
        0, 0, 0, 0, 0, 0, 1, 0, 2,
        0, 0, 7, 0, 0, 0, 0, 0, 0,
        1, 2, 0, 0, 5, 0, 8, 9, 0,
        0, 0, 3, 0, 0, 0, 0, 0, 0,
        0, 7, 0, 5, 0, 0, 0, 2, 0,
        0, 6, 0, 0, 4, 1, 0, 0, 7,
        3, 9, 0, 0, 0, 6, 0, 0, 1,
        8, 0, 0, 0, 1, 0, 3, 0, 6,
        0, 0, 0, 0, 0, 0, 0, 0, 0
    )

    private fun grid(board: IntArray = IntArray(81)): CandidateGrid {
        val result = CandidateGrid.create(board)
        assertTrue("Expected valid grid, got $result", result is CandidateGridCreationResult.Success)
        return (result as CandidateGridCreationResult.Success).grid
    }

    private fun single(step: LogicalStep?): Pair<SolveAction.PlaceValue, StepEvidence.Single> {
        assertNotNull(step)
        val action = step!!.actions.single() as SolveAction.PlaceValue
        val evidence = step.evidence as StepEvidence.Single
        return action to evidence
    }

    private fun retainCandidates(grid: CandidateGrid, cell: CellRef, allowed: DigitSet) {
        val remove = grid.candidatesAt(cell).remove(allowed)
        if (!remove.isEmpty) {
            assertEquals(CandidateGridMutationResult.Success, grid.eliminate(cell, remove))
        }
    }

    private fun makeHiddenSingle(
        grid: CandidateGrid,
        house: HouseRef,
        digit: Int,
        target: CellRef
    ) {
        assertTrue(grid.cellsIn(house).contains(target))
        for (cell in grid.cellsIn(house)) {
            if (cell != target && digit in grid.candidatesAt(cell)) {
                assertEquals(
                    CandidateGridMutationResult.Success,
                    grid.eliminate(cell, DigitSet.of(digit))
                )
            }
        }
    }

    @Test
    fun nakedSingle_detectsTargetDigitTechniqueAndExactEvidence() {
        val board = solvedBoard.clone().also { it[0] = 0 }
        val grid = grid(board)
        val before = grid.snapshot()

        val step = solver.nextStep(grid)
        val (action, evidence) = single(step)

        assertEquals(SudokuTechnique.NAKED_SINGLE, step!!.technique)
        assertEquals(CellRef(0, 0), action.cell)
        assertEquals(5, action.digit)
        assertEquals(action.cell, evidence.cell)
        assertEquals(action.digit, evidence.digit)
        assertEquals(DigitSet.of(5), evidence.candidates)
        assertNull(evidence.uniqueIn)
        assertEquals(before, grid.snapshot())
    }

    @Test
    fun nakedSingle_selectsEarliestCellInRowMajorOrder() {
        val board = solvedBoard.clone().also {
            it[0] = 0
            it[80] = 0
        }
        val (action, _) = single(solver.nextStep(grid(board)))
        assertEquals(CellRef(0, 0), action.cell)
        assertEquals(5, action.digit)
    }

    @Test
    fun nakedSingle_beatsSimultaneouslyAvailableHiddenSingle() {
        val grid = grid()
        retainCandidates(grid, CellRef(0, 0), DigitSet.of(1))
        makeHiddenSingle(grid, HouseRef(HouseType.ROW, 1), 2, CellRef(1, 8))

        val step = solver.nextStep(grid)
        val (action, evidence) = single(step)
        assertEquals(SudokuTechnique.NAKED_SINGLE, step!!.technique)
        assertEquals(CellRef(0, 0), action.cell)
        assertNull(evidence.uniqueIn)
    }

    @Test
    fun nakedSingle_ignoresFilledCellsAndTwoCandidateCells() {
        val boardWithGiven = IntArray(81).also { it[0] = 1 }
        assertNull(solver.findNakedSingle(grid(boardWithGiven)))

        val grid = grid()
        retainCandidates(grid, CellRef(0, 0), DigitSet.of(1, 2))
        assertNull(solver.findNakedSingle(grid))
    }

    @Test
    fun hiddenSingle_detectsRowWithCorrectEvidence() {
        val grid = grid()
        val house = HouseRef(HouseType.ROW, 3)
        val target = CellRef(3, 5)
        makeHiddenSingle(grid, house, 7, target)

        val step = solver.findHiddenSingle(grid)
        val (action, evidence) = single(step)
        assertEquals(SudokuTechnique.HIDDEN_SINGLE, step!!.technique)
        assertEquals(target, action.cell)
        assertEquals(7, action.digit)
        assertEquals(house, evidence.uniqueIn)
        assertEquals(DigitSet.ALL_DIGITS, evidence.candidates)
    }

    @Test
    fun hiddenSingle_detectsColumnWithCorrectEvidence() {
        val grid = grid()
        val house = HouseRef(HouseType.COLUMN, 4)
        val target = CellRef(6, 4)
        makeHiddenSingle(grid, house, 3, target)

        val (action, evidence) = single(solver.findHiddenSingle(grid))
        assertEquals(target, action.cell)
        assertEquals(3, action.digit)
        assertEquals(house, evidence.uniqueIn)
    }

    @Test
    fun hiddenSingle_detectsBoxWithCorrectEvidence() {
        val grid = grid()
        val house = HouseRef(HouseType.BOX, 7)
        val target = CellRef(7, 4)
        makeHiddenSingle(grid, house, 6, target)

        val (action, evidence) = single(solver.findHiddenSingle(grid))
        assertEquals(target, action.cell)
        assertEquals(6, action.digit)
        assertEquals(house, evidence.uniqueIn)
    }

    @Test
    fun hiddenSingle_usesRowThenColumnThenBoxPrecedence() {
        val grid = grid()
        makeHiddenSingle(grid, HouseRef(HouseType.BOX, 0), 7, CellRef(2, 2))
        makeHiddenSingle(grid, HouseRef(HouseType.COLUMN, 8), 8, CellRef(7, 8))
        makeHiddenSingle(grid, HouseRef(HouseType.ROW, 8), 9, CellRef(8, 7))

        val (action, evidence) = single(solver.findHiddenSingle(grid))
        assertEquals(CellRef(8, 7), action.cell)
        assertEquals(9, action.digit)
        assertEquals(HouseRef(HouseType.ROW, 8), evidence.uniqueIn)
    }

    @Test
    fun hiddenSingle_usesLowerHouseIndexBeforeHigherIndex() {
        val grid = grid()
        makeHiddenSingle(grid, HouseRef(HouseType.ROW, 6), 8, CellRef(6, 8))
        makeHiddenSingle(grid, HouseRef(HouseType.ROW, 1), 9, CellRef(1, 8))

        val (action, evidence) = single(solver.findHiddenSingle(grid))
        assertEquals(CellRef(1, 8), action.cell)
        assertEquals(HouseRef(HouseType.ROW, 1), evidence.uniqueIn)
    }

    @Test
    fun hiddenSingle_usesLowerDigitWithinSameHouse() {
        val grid = grid()
        val house = HouseRef(HouseType.ROW, 0)
        makeHiddenSingle(grid, house, 7, CellRef(0, 8))
        makeHiddenSingle(grid, house, 2, CellRef(0, 0))

        val (action, evidence) = single(solver.findHiddenSingle(grid))
        assertEquals(CellRef(0, 0), action.cell)
        assertEquals(2, action.digit)
        assertEquals(house, evidence.uniqueIn)
    }

    @Test
    fun hiddenSingle_ignoresAlreadyPlacedDigitAndCandidateOccurringTwice() {
        val board = IntArray(81).also { it[0] = 5 }
        assertNull(solver.findHiddenSingle(grid(board)))

        val grid = grid()
        val house = HouseRef(HouseType.ROW, 0)
        for (cell in grid.cellsIn(house)) {
            if (cell.column !in 1..2) grid.eliminate(cell, DigitSet.of(4))
        }
        assertEquals(2, grid.candidatePositions(house, 4).size)
        assertNull(solver.findHiddenSingle(grid))
    }

    @Test
    fun nextStep_isNonMutatingAndStructurallyDeterministic() {
        val grid = grid(solvedBoard.clone().also { it[40] = 0 })
        val before = grid.snapshot()
        val first = solver.nextStep(grid)
        repeat(20) {
            assertEquals(first, solver.nextStep(grid))
            assertEquals(first.hashCode(), solver.nextStep(grid).hashCode())
        }
        assertEquals(before, grid.snapshot())
    }

    @Test
    fun solve_alreadySolvedBoardReturnsSolvedWithNoStepsAndNoMutation() {
        val input = solvedBoard.clone()
        val result = solver.solve(input)
        assertEquals(LogicalSolveStatus.SOLVED, result.status)
        assertTrue(result.steps.isEmpty())
        assertArrayEquals(solvedBoard, result.finalBoard)
        assertArrayEquals(solvedBoard, input)
    }

    @Test
    fun solve_classicPuzzleCompletesUsingSinglesWithValidEvidence() {
        val result = solver.solve(classicSinglesPuzzle)
        assertEquals(LogicalSolveStatus.SOLVED, result.status)
        assertArrayEquals(solvedBoard, result.finalBoard)
        assertTrue(result.steps.isNotEmpty())
        assertTrue(result.steps.all {
            it.technique == SudokuTechnique.NAKED_SINGLE ||
                it.technique == SudokuTechnique.HIDDEN_SINGLE
        })
        validateTrace(classicSinglesPuzzle, result)
    }

    @Test
    fun solve_oneBlankPuzzleUsesOnlyNakedSingle() {
        val puzzle = solvedBoard.clone().also { it[80] = 0 }
        val result = solver.solve(puzzle)
        assertEquals(LogicalSolveStatus.SOLVED, result.status)
        assertEquals(1, result.steps.size)
        assertTrue(result.steps.all { it.technique == SudokuTechnique.NAKED_SINGLE })
        assertArrayEquals(solvedBoard, result.finalBoard)
        validateTrace(puzzle, result)
    }

    @Test
    fun solve_hiddenRequiredPuzzleUsesAtLeastOneHiddenSingle() {
        val result = solver.solve(hiddenRequiredPuzzle)
        assertEquals(LogicalSolveStatus.SOLVED, result.status)
        assertTrue(result.steps.any { it.technique == SudokuTechnique.HIDDEN_SINGLE })
        validateTrace(hiddenRequiredPuzzle, result)
    }

    @Test
    fun solve_emptyValidBoardStallsWithoutGuessing() {
        val result = solver.solve(IntArray(81))
        assertEquals(LogicalSolveStatus.STALLED, result.status)
        assertTrue(result.steps.isEmpty())
        assertTrue(result.finalBoard.all { it == 0 })
        assertEquals(DigitSet.ALL_DIGITS, result.remainingCandidates.candidatesAt(CellRef(0, 0)))
    }

    @Test
    fun solve_invalidInputsReturnInvalidWithZeroSteps() {
        val invalidBoards = listOf(
            IntArray(80),
            IntArray(81).also { it[0] = -1 },
            IntArray(81).also { it[0] = 10 },
            IntArray(81).also { it[0] = 1; it[1] = 1 },
            IntArray(81).also { it[0] = 2; it[9] = 2 },
            IntArray(81).also { it[0] = 3; it[10] = 3 },
            IntArray(81).also {
                for (column in 0..7) it[column] = column + 1
                it[17] = 9
            }
        )

        for (board in invalidBoards) {
            val original = board.clone()
            val result = solver.solve(board)
            assertEquals(LogicalSolveStatus.INVALID, result.status)
            assertTrue(result.steps.isEmpty())
            assertArrayEquals(original, result.finalBoard)
            assertArrayEquals(original, board)
        }
    }

    @Test
    fun solve_forcedSingleContradictionReturnsInvalidRatherThanStalled() {
        val board = IntArray(81).also {
            for (column in 2..8) it[column] = column + 1
            it[27] = 2
            it[55] = 2
        }
        assertTrue(CandidateGrid.create(board) is CandidateGridCreationResult.Success)

        val result = solver.solve(board)

        assertEquals(LogicalSolveStatus.INVALID, result.status)
        assertTrue(result.steps.isEmpty())
        assertArrayEquals(board, result.finalBoard)
    }

    @Test
    fun solve_resultIsImmutableAndDeterministicAcrossRepeatedRuns() {
        val first = solver.solve(classicSinglesPuzzle)
        val second = solver.solve(classicSinglesPuzzle.clone())
        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertEquals(first.toString(), second.toString())

        val leakedBoard = first.finalBoard
        val leakedMasks = first.remainingCandidates.candidateMasks
        leakedBoard[0] = 0
        leakedMasks[0] = 511
        assertArrayEquals(solvedBoard, first.finalBoard)
        assertEquals(DigitSet.EMPTY, first.remainingCandidates.candidatesAt(CellRef(0, 0)))
        assertThrows(UnsupportedOperationException::class.java) {
            (first.steps as MutableList).clear()
        }
    }

    @Test
    fun oracleCrossCheck_100GeneratedSeedsHasNoInvalidOrIncorrectPlacements() {
        var solved = 0
        var stalled = 0
        var invalid = 0
        var incorrectPlacements = 0

        for (seed in 1L..100L) {
            val generated = SudokuPuzzleEngine(seed).generate()
            val oracle = SudokuPuzzleEngine().solve(generated.puzzle)
            assertNotNull("Seed $seed must have an oracle solution", oracle)
            val result = solver.solve(generated.puzzle)

            when (result.status) {
                LogicalSolveStatus.SOLVED -> {
                    solved++
                    assertArrayEquals("Seed $seed logical solution mismatch", oracle, result.finalBoard)
                }
                LogicalSolveStatus.STALLED -> stalled++
                LogicalSolveStatus.INVALID -> invalid++
            }

            for (step in result.steps) {
                val action = step.actions.single() as SolveAction.PlaceValue
                if (action.digit != oracle!![action.cell.index]) incorrectPlacements++
            }
            validateTrace(generated.puzzle, result)
        }

        println(
            "SINGLES ORACLE REPORT: solved=$solved stalled=$stalled " +
                "invalid=$invalid incorrectPlacements=$incorrectPlacements"
        )
        assertEquals(100, solved + stalled)
        assertEquals(0, invalid)
        assertEquals(0, incorrectPlacements)
    }

    private fun validateTrace(initialBoard: IntArray, result: LogicalSolveResult) {
        val creation = CandidateGrid.create(initialBoard)
        assertTrue(creation is CandidateGridCreationResult.Success)
        val grid = (creation as CandidateGridCreationResult.Success).grid

        for (step in result.steps) {
            val action = step.actions.single() as SolveAction.PlaceValue
            val evidence = step.evidence as StepEvidence.Single
            assertEquals(0, grid.valueAt(action.cell))
            assertTrue(action.digit in grid.candidatesAt(action.cell))
            assertEquals(action.cell, evidence.cell)
            assertEquals(action.digit, evidence.digit)
            assertEquals(grid.candidatesAt(action.cell), evidence.candidates)

            when (step.technique) {
                SudokuTechnique.NAKED_SINGLE -> {
                    assertNull(evidence.uniqueIn)
                    assertEquals(1, evidence.candidates.size)
                }
                SudokuTechnique.HIDDEN_SINGLE -> {
                    val house = evidence.uniqueIn!!
                    assertTrue(grid.cellsIn(house).contains(action.cell))
                    assertEquals(listOf(action.cell), grid.candidatePositions(house, action.digit))
                }
                else -> throw AssertionError("Unsupported technique emitted: ${step.technique}")
            }
            assertEquals(CandidateGridMutationResult.Success, grid.place(action.cell, action.digit))
        }

        assertArrayEquals(result.finalBoard, grid.snapshot().values)
        assertEquals(result.remainingCandidates, grid.snapshot())
        assertFalse(result.steps.any { it.actions.size != 1 })
    }
}
