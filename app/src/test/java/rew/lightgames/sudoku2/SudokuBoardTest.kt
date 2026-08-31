package rew.lightgames.sudoku2

import org.junit.Assert.*
import org.junit.Test

class SudokuBoardTest {

    private fun createSolution(): Array<IntArray> = arrayOf(
        intArrayOf(5, 3, 4, 6, 7, 8, 9, 1, 2),
        intArrayOf(6, 7, 2, 1, 9, 5, 3, 4, 8),
        intArrayOf(1, 9, 8, 3, 4, 2, 5, 6, 7),
        intArrayOf(8, 5, 9, 7, 6, 1, 4, 2, 3),
        intArrayOf(4, 2, 6, 8, 5, 3, 7, 9, 1),
        intArrayOf(7, 1, 3, 9, 2, 4, 8, 5, 6),
        intArrayOf(9, 6, 1, 5, 3, 7, 2, 8, 4),
        intArrayOf(2, 8, 7, 4, 1, 9, 6, 3, 5),
        intArrayOf(3, 4, 5, 2, 8, 6, 1, 7, 9)
    )

    private fun createBoardWithGivens(): SudokuBoard {
        val solution = createSolution()
        val givens = arrayOf(
            intArrayOf(5, 3, 0, 0, 7, 0, 0, 0, 0),
            intArrayOf(6, 0, 0, 1, 9, 5, 0, 0, 0),
            intArrayOf(0, 9, 8, 0, 0, 0, 0, 6, 0),
            intArrayOf(8, 0, 0, 0, 6, 0, 0, 0, 3),
            intArrayOf(4, 0, 0, 8, 0, 3, 0, 0, 1),
            intArrayOf(7, 0, 0, 0, 2, 0, 0, 0, 6),
            intArrayOf(0, 6, 0, 0, 0, 0, 2, 8, 0),
            intArrayOf(0, 0, 0, 4, 1, 9, 0, 0, 5),
            intArrayOf(0, 0, 0, 0, 8, 0, 0, 7, 9)
        )
        val cells = Array(9) { row ->
            Array(9) { col ->
                Cell(
                    number = givens[row][col],
                    original_number = givens[row][col]
                )
            }
        }
        return SudokuBoard(cells, solution)
    }

    @Test
    fun board_correct_when_all_cells_match_solution() {
        val solution = createSolution()
        val cells = Array(9) { row ->
            Array(9) { col ->
                Cell(number = solution[row][col], original_number = solution[row][col])
            }
        }
        val board = SudokuBoard(cells, solution)
        assertTrue("Board should be correct when all cells match solution", board.isBoardCorrect())
    }

    @Test
    fun board_incorrect_when_cell_differs_from_solution() {
        val board = createBoardWithGivens()
        board.setCell(0, 2, Cell(number = 1, original_number = 0))
        assertFalse("Board should be incorrect with wrong value", board.isBoardCorrect())
    }

    @Test
    fun board_incorrect_when_empty_cells_remain() {
        val board = createBoardWithGivens()
        assertFalse("Board with empty cells should not be correct", board.isBoardCorrect())
    }

    @Test
    fun board_becomes_correct_when_all_filled_correctly() {
        val solution = createSolution()
        val board = createBoardWithGivens()
        for (row in 0 until 9) {
            for (col in 0 until 9) {
                board.setCell(row, col, Cell(number = solution[row][col], original_number = 0))
            }
        }
        assertTrue("Board should be correct after filling all cells correctly", board.isBoardCorrect())
    }

    @Test
    fun getCell_returns_correct_cell() {
        val board = createBoardWithGivens()
        val cell = board.getCell(0, 0)
        assertEquals("Cell at (0,0) should have number 5", 5, cell.number)
    }

    @Test
    fun setCell_updates_cell() {
        val board = createBoardWithGivens()
        board.setCell(0, 2, Cell(number = 4, original_number = 0))
        assertEquals("Cell at (0,2) should be updated", 4, board.getCell(0, 2).number)
    }

    @Test
    fun setCell_ignores_negative_indices() {
        val board = createBoardWithGivens()
        val original = board.getCell(0, 0).number
        board.setCell(-1, 0, Cell(number = 9, original_number = 0))
        assertEquals("Board should be unchanged for -1 row", original, board.getCell(0, 0).number)
        board.setCell(0, -1, Cell(number = 9, original_number = 0))
        assertEquals("Board should be unchanged for -1 col", original, board.getCell(0, 0).number)
    }

    @Test
    fun copy_creates_independent_board() {
        val board = createBoardWithGivens()
        val copy = board.copy()
        copy.setCell(0, 2, Cell(number = 4, original_number = 0))
        assertNotEquals("Modified copy should differ from original",
            board.getCell(0, 2).number, copy.getCell(0, 2).number)
    }

    @Test
    fun copy_shares_solution_reference() {
        val board = createBoardWithGivens()
        val copy = board.copy()
        assertSame("Copy should share solution reference", board.solution, copy.solution)
    }

    @Test
    fun equals_same_boards_are_equal() {
        val board1 = createBoardWithGivens()
        val board2 = createBoardWithGivens()
        assertEquals("Identical boards should be equal", board1, board2)
    }

    @Test
    fun equals_different_boards_not_equal() {
        val board1 = createBoardWithGivens()
        val board2 = createBoardWithGivens()
        board2.setCell(0, 2, Cell(number = 9, original_number = 0))
        assertNotEquals("Different boards should not be equal", board1, board2)
    }

    @Test
    fun clearNotes_removes_all_notes() {
        val board = createBoardWithGivens()
        board.getCell(0, 2).addNote(1)
        board.getCell(0, 2).addNote(2)
        board.clearNotes()
        assertTrue("Notes should be cleared on all cells", board.getCell(0, 2).notes.isEmpty())
    }

    @Test
    fun solution_is_accessible() {
        val board = createBoardWithGivens()
        assertEquals("Solution should be accessible", 5, board.solution[0][0])
        assertEquals("Solution should be accessible", 9, board.solution[8][8])
    }

    @Test
    fun wrong_but_nonconflicting_player_entry_is_not_a_visible_conflict() {
        val board = createBoardWithGivens()
        board.setCell(0, 2, Cell(number = 1, original_number = 0))

        assertNotEquals("Entry should be wrong against the hidden solution", 1, board.solution[0][2])
        assertFalse(board.hasVisibleConflict(0, 2))
    }

    @Test
    fun player_entry_matching_solution_is_not_a_visible_conflict() {
        val board = createBoardWithGivens()
        board.setCell(0, 2, Cell(number = board.solution[0][2], original_number = 0))

        assertFalse(board.hasVisibleConflict(0, 2))
    }

    @Test
    fun row_duplicate_marks_both_player_entries_as_conflicting() {
        val board = createBoardWithGivens()
        board.setCell(0, 2, Cell(number = 4, original_number = 0))
        board.setCell(0, 3, Cell(number = 4, original_number = 0))

        assertTrue(board.hasVisibleConflict(0, 2))
        assertTrue(board.hasVisibleConflict(0, 3))
    }

    @Test
    fun column_duplicate_marks_both_player_entries_as_conflicting() {
        val board = createBoardWithGivens()
        board.setCell(0, 2, Cell(number = 4, original_number = 0))
        board.setCell(3, 2, Cell(number = 4, original_number = 0))

        assertTrue(board.hasVisibleConflict(0, 2))
        assertTrue(board.hasVisibleConflict(3, 2))
    }

    @Test
    fun box_duplicate_marks_both_player_entries_as_conflicting() {
        val board = createBoardWithGivens()
        board.setCell(0, 2, Cell(number = 4, original_number = 0))
        board.setCell(1, 1, Cell(number = 4, original_number = 0))

        assertTrue(board.hasVisibleConflict(0, 2))
        assertTrue(board.hasVisibleConflict(1, 1))
    }

    @Test
    fun player_entry_conflicting_with_fixed_clue_is_detected() {
        val board = createBoardWithGivens()
        board.setCell(0, 2, Cell(number = 5, original_number = 0))

        assertTrue(board.hasVisibleConflict(0, 2))
        assertTrue("The visible fixed peer participates in the duplicate", board.hasVisibleConflict(0, 0))
        assertFalse("Only the player entry is editable for error styling", board.getCell(0, 0).isEditable)
    }

    @Test
    fun erasing_duplicate_clears_conflict_from_remaining_value() {
        val board = createBoardWithGivens()
        board.setCell(0, 2, Cell(number = 4, original_number = 0))
        board.setCell(0, 3, Cell(number = 4, original_number = 0))
        board.setCell(0, 3, Cell(number = 0, original_number = 0))

        assertFalse(board.hasVisibleConflict(0, 2))
        assertFalse(board.hasVisibleConflict(0, 3))
    }

    @Test
    fun changing_duplicate_value_clears_conflict_from_both_cells() {
        val board = createBoardWithGivens()
        board.setCell(0, 2, Cell(number = 4, original_number = 0))
        board.setCell(0, 3, Cell(number = 4, original_number = 0))
        board.setCell(0, 3, Cell(number = 6, original_number = 0))

        assertFalse(board.hasVisibleConflict(0, 2))
        assertFalse(board.hasVisibleConflict(0, 3))
    }

    @Test
    fun notes_do_not_create_visible_conflicts() {
        val board = createBoardWithGivens()
        board.getCell(0, 2).addNote(5)
        board.getCell(0, 3).addNote(5)

        assertFalse(board.hasVisibleConflict(0, 2))
        assertFalse(board.hasVisibleConflict(0, 3))
    }

    @Test
    fun solution_value_remains_available_for_hints() {
        val board = createBoardWithGivens()

        assertEquals(board.solution[0][2], board.solutionValueAt(0, 2))
    }

    @Test
    fun default_board_has_zero_cells() {
        val solution = createSolution()
        val board = SudokuBoard(solution = solution)
        for (row in 0 until 9) {
            for (col in 0 until 9) {
                assertEquals("Default cell at ($row,$col) should be 0", 0, board.getCell(row, col).number)
            }
        }
    }
}
