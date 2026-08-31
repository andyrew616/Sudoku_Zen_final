package rew.lightgames.sudoku2

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateGridTest {
    private val standardPuzzle = intArrayOf(
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

    private fun create(board: IntArray = IntArray(81)): CandidateGrid {
        val result = CandidateGrid.create(board)
        assertTrue("Expected success, got $result", result is CandidateGridCreationResult.Success)
        return (result as CandidateGridCreationResult.Success).grid
    }

    private fun assertFailure(
        board: IntArray,
        predicate: (CandidateGridValidationError) -> Boolean
    ) {
        val result = CandidateGrid.create(board)
        assertTrue("Expected failure, got $result", result is CandidateGridCreationResult.Failure)
        assertTrue(predicate((result as CandidateGridCreationResult.Failure).error))
    }

    @Test
    fun emptyBoard_initializesEveryCellWithAllDigits() {
        val grid = create()
        for (index in 0..80) {
            assertEquals(0, grid.valueAt(CellRef.fromIndex(index)))
            assertEquals(DigitSet.ALL_DIGITS, grid.candidatesAt(CellRef.fromIndex(index)))
        }
    }

    @Test
    fun normalGivens_areStoredAndProduceCorrectCandidates() {
        val grid = create(standardPuzzle)
        assertEquals(5, grid.valueAt(CellRef(0, 0)))
        assertEquals(DigitSet.EMPTY, grid.candidatesAt(CellRef(0, 0)))
        assertEquals(DigitSet.of(1, 2, 4), grid.candidatesAt(CellRef(0, 2)))
        assertEquals(DigitSet.of(2, 4, 6, 8), grid.candidatesAt(CellRef(0, 5)))
    }

    @Test
    fun creation_rejectsWrongLengthAndInvalidValues() {
        assertFailure(IntArray(80)) { it == CandidateGridValidationError.WrongBoardSize(80) }
        val high = IntArray(81).also { it[40] = 10 }
        assertFailure(high) { it == CandidateGridValidationError.InvalidValue(40, 10) }
        val low = IntArray(81).also { it[2] = -1 }
        assertFailure(low) { it == CandidateGridValidationError.InvalidValue(2, -1) }
    }

    @Test
    fun creation_rejectsDuplicateRowsColumnsAndBoxes() {
        val row = IntArray(81).also { it[0] = 4; it[8] = 4 }
        assertFailure(row) {
            it == CandidateGridValidationError.DuplicateGiven(HouseRef(HouseType.ROW, 0), 4)
        }

        val column = IntArray(81).also { it[0] = 6; it[72] = 6 }
        assertFailure(column) {
            it == CandidateGridValidationError.DuplicateGiven(HouseRef(HouseType.COLUMN, 0), 6)
        }

        val box = IntArray(81).also { it[0] = 7; it[10] = 7 }
        assertFailure(box) {
            it == CandidateGridValidationError.DuplicateGiven(HouseRef(HouseType.BOX, 0), 7)
        }
    }

    @Test
    fun creation_rejectsEmptyCellWithNoLegalCandidates() {
        val board = IntArray(81)
        for (column in 0..7) board[column] = column + 1
        board[17] = 9
        assertFailure(board) { it == CandidateGridValidationError.NoCandidates(CellRef(0, 8)) }
    }

    @Test
    fun creation_doesNotMutateOrRetainInputArray() {
        val input = standardPuzzle.clone()
        val original = input.clone()
        val grid = create(input)
        assertArrayEquals(original, input)

        input[0] = 0
        assertEquals(5, grid.valueAt(CellRef(0, 0)))
    }

    @Test
    fun placement_storesValueClearsCellAndUpdatesAllPeersOnly() {
        val grid = create()
        val placed = CellRef(0, 0)
        assertEquals(CandidateGridMutationResult.Success, grid.place(placed, 5))
        assertEquals(5, grid.valueAt(placed))
        assertEquals(DigitSet.EMPTY, grid.candidatesAt(placed))

        listOf(CellRef(0, 8), CellRef(8, 0), CellRef(1, 1)).forEach { peer ->
            assertFalse(5 in grid.candidatesAt(peer))
        }
        assertEquals(DigitSet.ALL_DIGITS, grid.candidatesAt(CellRef(4, 4)))
    }

    @Test
    fun placement_rejectsPlacedCellAndNonCandidateWithoutMutation() {
        val grid = create(standardPuzzle)
        val before = grid.snapshot()
        val occupiedResult = grid.place(CellRef(0, 0), 5)
        assertTrue(occupiedResult is CandidateGridMutationResult.Failure)
        assertEquals(before, grid.snapshot())

        val nonCandidateResult = grid.place(CellRef(0, 2), 5)
        assertTrue(nonCandidateResult is CandidateGridMutationResult.Failure)
        assertEquals(before, grid.snapshot())
    }

    @Test
    fun placement_rejectsInvalidDigitWithoutMutation() {
        val grid = create()
        val before = grid.snapshot()
        assertEquals(
            CandidateGridMutationResult.Failure(CandidateGridMutationError.InvalidDigit(0)),
            grid.place(CellRef(0, 0), 0)
        )
        assertEquals(before, grid.snapshot())
    }

    @Test
    fun placement_rejectsDuplicateDigitInPeerWithoutMutation() {
        val grid = create()
        assertEquals(CandidateGridMutationResult.Success, grid.place(CellRef(0, 0), 5))
        val before = grid.snapshot()

        assertEquals(
            CandidateGridMutationResult.Failure(
                CandidateGridMutationError.DigitNotCandidate(CellRef(0, 8), 5)
            ),
            grid.place(CellRef(0, 8), 5)
        )
        assertEquals(before, grid.snapshot())
    }

    @Test
    fun placement_thatWouldEmptyPeer_isAtomic() {
        val grid = create()
        assertEquals(
            CandidateGridMutationResult.Success,
            grid.eliminate(CellRef(0, 1), DigitSet.of(2, 3, 4, 5, 6, 7, 8, 9))
        )
        val before = grid.snapshot()

        val result = grid.place(CellRef(0, 0), 1)

        assertTrue(result is CandidateGridMutationResult.Failure)
        assertEquals(before, grid.snapshot())
    }

    @Test
    fun elimination_removesOneOrManyPresentCandidates() {
        val grid = create()
        assertEquals(CandidateGridMutationResult.Success, grid.eliminate(CellRef(4, 4), DigitSet.of(2)))
        assertFalse(2 in grid.candidatesAt(CellRef(4, 4)))

        assertEquals(
            CandidateGridMutationResult.Success,
            grid.eliminate(CellRef(4, 4), DigitSet.of(3, 5, 7))
        )
        assertEquals(DigitSet.of(1, 4, 6, 8, 9), grid.candidatesAt(CellRef(4, 4)))
    }

    @Test
    fun elimination_ofMixedPresentAndAbsentDigits_removesPresentDigits() {
        val grid = create()
        grid.eliminate(CellRef(2, 2), DigitSet.of(1))
        assertEquals(
            CandidateGridMutationResult.Success,
            grid.eliminate(CellRef(2, 2), DigitSet.of(1, 2))
        )
        assertFalse(2 in grid.candidatesAt(CellRef(2, 2)))
    }

    @Test
    fun elimination_rejectsNoOpPlacedCellAndEmptyingCellAtomically() {
        val grid = create()
        grid.eliminate(CellRef(0, 0), DigitSet.of(1))
        val afterFirst = grid.snapshot()
        assertTrue(grid.eliminate(CellRef(0, 0), DigitSet.of(1)) is CandidateGridMutationResult.Failure)
        assertEquals(afterFirst, grid.snapshot())

        val placedGrid = create(standardPuzzle)
        val placedBefore = placedGrid.snapshot()
        assertTrue(
            placedGrid.eliminate(CellRef(0, 0), DigitSet.of(1)) is CandidateGridMutationResult.Failure
        )
        assertEquals(placedBefore, placedGrid.snapshot())

        val candidates = grid.candidatesAt(CellRef(0, 0))
        val beforeEmptyAttempt = grid.snapshot()
        assertTrue(grid.eliminate(CellRef(0, 0), candidates) is CandidateGridMutationResult.Failure)
        assertEquals(beforeEmptyAttempt, grid.snapshot())
    }

    @Test
    fun elimination_rejectsEmptyRequestWithoutMutation() {
        val grid = create()
        val before = grid.snapshot()
        assertEquals(
            CandidateGridMutationResult.Failure(CandidateGridMutationError.EmptyElimination),
            grid.eliminate(CellRef(0, 0), DigitSet.EMPTY)
        )
        assertEquals(before, grid.snapshot())
    }

    @Test
    fun logicalElimination_persistsAcrossLaterUnrelatedPlacement() {
        val grid = create()
        val target = CellRef(4, 4)
        assertEquals(CandidateGridMutationResult.Success, grid.eliminate(target, DigitSet.of(1)))
        assertFalse(1 in grid.candidatesAt(target))

        assertEquals(CandidateGridMutationResult.Success, grid.place(CellRef(0, 0), 2))

        assertFalse(1 in grid.candidatesAt(target))
        assertEquals(0, grid.valueAt(target))
    }

    @Test
    fun snapshotsAreIndependentAndDoNotLeakArrays() {
        val grid = create()
        val oldSnapshot = grid.snapshot()
        val leakedValues = oldSnapshot.values
        val leakedCandidates = oldSnapshot.candidateMasks
        leakedValues[0] = 9
        leakedCandidates[1] = 0

        assertEquals(0, grid.valueAt(CellRef(0, 0)))
        assertEquals(DigitSet.ALL_DIGITS, grid.candidatesAt(CellRef(0, 1)))
        assertEquals(0, oldSnapshot.valueAt(CellRef(0, 0)))
        assertEquals(DigitSet.ALL_DIGITS, oldSnapshot.candidatesAt(CellRef(0, 1)))

        grid.place(CellRef(8, 8), 9)
        assertEquals(0, oldSnapshot.valueAt(CellRef(8, 8)))
        assertNotEquals(oldSnapshot, grid.snapshot())
    }

    @Test
    fun snapshotsHaveDeterministicStructuralEqualityHashAndRepresentation() {
        val first = create(standardPuzzle).snapshot()
        val second = create(standardPuzzle.clone()).snapshot()
        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertEquals(first.toString(), second.toString())
    }

    @Test
    fun topology_housesContainNineCanonicalCellsAndBoxMappingIsCorrect() {
        val grid = create()
        HouseType.entries.forEach { type ->
            for (index in 0..8) {
                val cells = grid.cellsIn(HouseRef(type, index))
                assertEquals(9, cells.size)
                assertEquals(cells.sorted(), cells)
                assertEquals(9, cells.distinct().size)
            }
        }
        assertEquals(
            listOf(
                CellRef(3, 6), CellRef(3, 7), CellRef(3, 8),
                CellRef(4, 6), CellRef(4, 7), CellRef(4, 8),
                CellRef(5, 6), CellRef(5, 7), CellRef(5, 8)
            ),
            grid.cellsIn(HouseRef(HouseType.BOX, 5))
        )
    }

    @Test
    fun topology_eachCellHasTwentyUniqueCanonicalPeersAndThreeCanonicalHouses() {
        val grid = create()
        for (index in 0..80) {
            val cell = CellRef.fromIndex(index)
            val peers = grid.peersOf(cell)
            assertEquals(20, peers.size)
            assertEquals(20, peers.distinct().size)
            assertEquals(peers.sorted(), peers)
            assertFalse(peers.contains(cell))

            val houses = grid.housesFor(cell)
            assertEquals(3, houses.size)
            assertEquals(houses.sorted(), houses)
            assertTrue(houses.all { grid.cellsIn(it).contains(cell) })
        }
    }

    @Test
    fun topology_wholeGridIterationIsImmutableAndRowMajor() {
        val cells = create().cellsRowMajor()
        assertEquals((0..80).toList(), cells.map { it.index })
        try {
            (cells as MutableList).removeAt(0)
            throw AssertionError("Expected immutable cell list")
        } catch (_: UnsupportedOperationException) {
            // Expected: callers cannot alter shared topology.
        }
    }

    @Test
    fun topology_housePeerAndContainingHouseViewsAreImmutable() {
        val grid = create()
        val cell = CellRef(4, 4)
        listOf(
            grid.cellsIn(HouseRef(HouseType.ROW, 4)),
            grid.peersOf(cell)
        ).forEach { cells ->
            try {
                (cells as MutableList).clear()
                throw AssertionError("Expected immutable cell list")
            } catch (_: UnsupportedOperationException) {
                // Expected.
            }
        }
        try {
            (grid.housesFor(cell) as MutableList).clear()
            throw AssertionError("Expected immutable house list")
        } catch (_: UnsupportedOperationException) {
            // Expected.
        }
    }

    @Test
    fun candidatePositions_areFilteredAndRowMajor() {
        val grid = create()
        grid.eliminate(CellRef(0, 7), DigitSet.of(4))
        grid.eliminate(CellRef(0, 2), DigitSet.of(4))

        assertEquals(
            listOf(CellRef(0, 0), CellRef(0, 1), CellRef(0, 3), CellRef(0, 4), CellRef(0, 5), CellRef(0, 6), CellRef(0, 8)),
            grid.candidatePositions(HouseRef(HouseType.ROW, 0), 4)
        )
    }

    @Test
    fun generatedPuzzleCrossCheck_100DeterministicSeeds() {
        for (seed in 1L..100L) {
            val generated = SudokuPuzzleEngine(seed).generate()
            val inputBefore = generated.puzzle.clone()
            val grid = create(generated.puzzle)
            assertArrayEquals("Seed $seed input mutated", inputBefore, generated.puzzle)

            for (index in 0..80) {
                val cell = CellRef.fromIndex(index)
                val value = generated.puzzle[index]
                assertEquals("Seed $seed value mismatch at $index", value, grid.valueAt(cell))
                if (value != 0) {
                    assertEquals(DigitSet.EMPTY, grid.candidatesAt(cell))
                    continue
                }

                val expected = (1..9).filter { digit ->
                    SudokuPuzzleEngine().canPlace(generated.puzzle, cell.row, cell.column, digit)
                }
                assertEquals(
                    "Seed $seed candidates mismatch at $index",
                    expected,
                    grid.candidatesAt(cell).digitsAscending()
                )
            }
        }
    }
}
