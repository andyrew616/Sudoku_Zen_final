package rew.lightgames.sudoku2

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class SerializationTest {

    private val gson = Gson()

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

    @Test
    fun cell_round_trip() {
        val cell = Cell(number = 5, original_number = 5, isHint = true)
        val json = gson.toJson(cell)
        val restored = gson.fromJson(json, Cell::class.java)
        assertEquals("Number should survive round-trip", cell.number, restored.number)
        assertEquals("original_number should survive round-trip", cell.original_number, restored.original_number)
        assertEquals("isHint should survive round-trip", cell.isHint, restored.isHint)
    }

    @Test
    fun cell_with_notes_round_trip() {
        val cell = Cell(number = 0, original_number = 0)
        cell.addNote(1)
        cell.addNote(5)
        cell.addNote(9)
        val json = gson.toJson(cell)
        val restored = gson.fromJson(json, Cell::class.java)
        assertEquals("Notes should survive round-trip", 3, restored.notes.size)
        assertTrue("Note 1 should survive", restored.notes.contains(1))
        assertTrue("Note 5 should survive", restored.notes.contains(5))
        assertTrue("Note 9 should survive", restored.notes.contains(9))
    }

    @Test
    fun editable_cell_round_trip() {
        val cell = Cell(number = 3, original_number = 0)
        val json = gson.toJson(cell)
        val restored = gson.fromJson(json, Cell::class.java)
        assertTrue("Editable cell should remain editable after round-trip", restored.isEditable)
    }

    @Test
    fun sudokuBoard_round_trip() {
        val solution = createSolution()
        val cells = Array(9) { row ->
            Array(9) { col ->
                Cell(number = solution[row][col], original_number = solution[row][col])
            }
        }
        val board = SudokuBoard(cells, solution)
        val json = gson.toJson(board)
        val restored = gson.fromJson(json, SudokuBoard::class.java)
        for (row in 0 until 9) {
            for (col in 0 until 9) {
                assertEquals(
                    "Cell ($row,$col) number should survive round-trip",
                    board.getCell(row, col).number,
                    restored.getCell(row, col).number
                )
            }
        }
    }

    @Test
    fun sudokuBoard_with_empty_cells_round_trip() {
        val solution = createSolution()
        val cells = Array(9) { row ->
            Array(9) { col ->
                if (row == 0 && col == 2) Cell(number = 0, original_number = 0)
                else Cell(number = solution[row][col], original_number = solution[row][col])
            }
        }
        val board = SudokuBoard(cells, solution)
        val json = gson.toJson(board)
        val restored = gson.fromJson(json, SudokuBoard::class.java)
        assertEquals("Empty cell should remain 0", 0, restored.getCell(0, 2).number)
        assertEquals("Filled cell should survive", 5, restored.getCell(0, 0).number)
    }

    @Test
    fun savedGameState_round_trip() {
        val solution = createSolution()
        val cells = Array(9) { row ->
            Array(9) { col ->
                Cell(number = solution[row][col], original_number = solution[row][col])
            }
        }
        val board = SudokuBoard(cells, solution)
        val state = SavedGameState(board = board, hintsUsed = 3, timeElapsed = 120)
        val json = gson.toJson(state)
        val restored = gson.fromJson(json, SavedGameState::class.java)
        assertEquals("hintsUsed should survive round-trip", 3, restored.hintsUsed)
        assertEquals("timeElapsed should survive round-trip", 120, restored.timeElapsed)
        for (row in 0 until 9) {
            for (col in 0 until 9) {
                assertEquals(
                    "Board cell ($row,$col) should survive round-trip",
                    board.getCell(row, col).number,
                    restored.board.getCell(row, col).number
                )
            }
        }
    }

    @Test
    fun savedGameState_with_notes_round_trip() {
        val solution = createSolution()
        val cells = Array(9) { row ->
            Array(9) { col ->
                Cell(number = 0, original_number = 0)
            }
        }
        cells[0][2].addNote(1)
        cells[0][2].addNote(5)
        val board = SudokuBoard(cells, solution)
        val state = SavedGameState(board = board, hintsUsed = 0, timeElapsed = 0)
        val json = gson.toJson(state)
        val restored = gson.fromJson(json, SavedGameState::class.java)
        assertEquals("Notes should survive in SavedGameState round-trip", 2, restored.board.getCell(0, 2).notes.size)
    }

    @Test
    fun savedGameState_preserves_solution() {
        val solution = createSolution()
        val board = SudokuBoard(solution = solution)
        val state = SavedGameState(board = board, hintsUsed = 1, timeElapsed = 60)
        val json = gson.toJson(state)
        val restored = gson.fromJson(json, SavedGameState::class.java)
        assertArrayEquals("Solution should survive round-trip", solution, restored.board.solution)
    }
}
