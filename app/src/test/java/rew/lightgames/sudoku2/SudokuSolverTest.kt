package rew.lightgames.sudoku2

import org.junit.Assert.*
import org.junit.Test

class SudokuSolverTest {

    private fun isValidPlacement(puzzle: Array<IntArray>, row: Int, col: Int, num: Int): Boolean {
        for (i in 0 until 9) {
            if (puzzle[row][i] == num) return false
        }
        for (i in 0 until 9) {
            if (puzzle[i][col] == num) return false
        }
        val boxRow = row - row % 3
        val boxCol = col - col % 3
        for (i in boxRow until boxRow + 3) {
            for (j in boxCol until boxCol + 3) {
                if (puzzle[i][j] == num) return false
            }
        }
        return true
    }

    private fun solve(puzzle: Array<IntArray>): Array<IntArray>? {
        val grid = puzzle.map { it.clone() }.toTypedArray()
        if (solveInPlace(grid)) return grid
        return null
    }

    private fun solveInPlace(puzzle: Array<IntArray>): Boolean {
        for (row in 0 until 9) {
            for (col in 0 until 9) {
                if (puzzle[row][col] == 0) {
                    for (num in 1..9) {
                        if (isValidPlacement(puzzle, row, col, num)) {
                            puzzle[row][col] = num
                            if (solveInPlace(puzzle)) return true
                            puzzle[row][col] = 0
                        }
                    }
                    return false
                }
            }
        }
        return true
    }

    private fun isSolutionValid(solution: Array<IntArray>): Boolean {
        for (row in 0 until 9) {
            if (solution[row].toSet() != (1..9).toSet()) return false
        }
        for (col in 0 until 9) {
            if ((0 until 9).map { solution[it][col] }.toSet() != (1..9).toSet()) return false
        }
        for (boxRow in 0 until 3) {
            for (boxCol in 0 until 3) {
                val boxSet = mutableSetOf<Int>()
                for (i in boxRow * 3 until boxRow * 3 + 3) {
                    for (j in boxCol * 3 until boxCol * 3 + 3) {
                        boxSet.add(solution[i][j])
                    }
                }
                if (boxSet != (1..9).toSet()) return false
            }
        }
        return true
    }

    private fun validCompleteBoard(): Array<IntArray> = arrayOf(
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
    fun solver_completes_puzzle_with_few_blanks() {
        val puzzle = validCompleteBoard()
        puzzle[8][7] = 0
        puzzle[8][8] = 0
        val solution = solve(puzzle)
        assertNotNull("Solver should find a solution", solution)
        assertTrue("Solution should be valid", isSolutionValid(solution!!))
        assertEquals(7, solution[8][7])
        assertEquals(9, solution[8][8])
    }

    @Test
    fun solver_completes_puzzle_with_one_blank() {
        val puzzle = validCompleteBoard()
        puzzle[0][0] = 0
        val solution = solve(puzzle)
        assertNotNull("Solver should find a solution", solution)
        assertTrue("Solution should be valid", isSolutionValid(solution!!))
        assertEquals(5, solution[0][0])
    }

    @Test
    fun solver_does_not_mutate_input() {
        val original = validCompleteBoard()
        original[8][7] = 0
        original[8][8] = 0
        val snapshot = original.map { it.clone() }.toTypedArray()
        solve(original)
        for (i in 0 until 9) {
            assertArrayEquals("Row $i should be unchanged", snapshot[i], original[i])
        }
    }

    @Test
    fun solver_preserves_givens() {
        val puzzle = validCompleteBoard()
        puzzle[8][7] = 0
        puzzle[8][8] = 0
        val solution = solve(puzzle)
        assertNotNull(solution)
        for (i in 0 until 9) {
            for (j in 0 until 9) {
                if (puzzle[i][j] != 0) {
                    assertEquals(
                        "Given at ($i,$j) should be preserved",
                        puzzle[i][j], solution!![i][j]
                    )
                }
            }
        }
    }

    @Test
    fun solver_returns_null_for_unsolvable_puzzle() {
        val unsolvable = arrayOf(
            intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8),
            intArrayOf(9, 0, 3, 0, 0, 0, 0, 0, 0),
            intArrayOf(4, 5, 6, 0, 0, 0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0)
        )
        val solution = solve(unsolvable)
        assertNull("Unsolvable puzzle should return null", solution)
    }

    @Test
    fun solver_handles_already_complete_board() {
        val complete = validCompleteBoard()
        val solution = solve(complete)
        assertNotNull("Complete board should be returned as-is", solution)
        for (i in 0 until 9) {
            assertArrayEquals("Row $i should be unchanged", complete[i], solution!![i])
        }
    }

    @Test
    fun solver_validates_row_constraint() {
        val board = validCompleteBoard()
        board[0][0] = 0
        board[1][0] = 5
        val solution = solve(board)
        assertNull("Puzzle with duplicate in column should be unsolvable", solution)
    }

    @Test
    fun solver_validates_column_constraint() {
        val board = validCompleteBoard()
        board[0][0] = 0
        board[0][1] = 5
        val solution = solve(board)
        assertNull("Puzzle with duplicate in row should be unsolvable", solution)
    }

    @Test
    fun solver_validates_box_constraint() {
        val board = validCompleteBoard()
        board[0][0] = 0
        board[1][1] = 5
        val solution = solve(board)
        assertNull("Puzzle with duplicate in box should be unsolvable", solution)
    }

    @Test
    fun solver_finds_correct_values_for_multiple_blanks() {
        val puzzle = validCompleteBoard()
        puzzle[0][0] = 0
        puzzle[0][1] = 0
        puzzle[0][2] = 0
        val solution = solve(puzzle)
        assertNotNull(solution)
        assertEquals(5, solution!![0][0])
        assertEquals(3, solution[0][1])
        assertEquals(4, solution[0][2])
    }
}
