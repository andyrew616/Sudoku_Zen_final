package rew.lightgames.sudoku2

import java.io.Serializable


class SudokuBoard(private val cells: Array<Array<Cell>> = Array(9) { row ->
    Array(9) { col ->
        Cell(isEditable = false, number = 0, original_number = 0, notes = arrayListOf())
    }
},val solution: Array<IntArray>): Serializable {
    fun getCell(
        row: Int,

        col: Int
    ): Cell {
        return cells[row][col]
    }

    fun setCell(row: Int, col: Int, cell: Cell) {
        if (row == -1 || col == -1) {
            return
        }
        cells[row][col] = cell
    }

    fun copy(): SudokuBoard {
        val newBoard = Array(9) { row ->
            Array(9) { col ->
                cells[row][col].copy()
            }
        }
        return SudokuBoard(newBoard, solution) // Pass the solution when creating a new instance
    }


    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as SudokuBoard

        for (i in 0..8) {
            for (j in 0..8) {
                if (this.getCell(i, j) != other.getCell(i, j)) {
                    return false
                }
            }
        }

        return true
    }

    override fun hashCode(): Int {
        var result = 0
        for (i in 0..8) {
            for (j in 0..8) {
                result = 31 * result + getCell(i, j).hashCode()
            }
        }
        return result
    }

    fun clearNotes() {
        for (i in cells.indices) {
            for (j in cells[i].indices) {
                cells[i][j].clearNotes()
            }
        }
    }

    fun hasVisibleConflict(row: Int, col: Int): Boolean {
        if (row !in cells.indices || col !in cells[row].indices) {
            return false
        }

        val value = cells[row][col].number
        if (value == 0) {
            return false
        }

        if (cells[row].indices.any { otherCol ->
                otherCol != col && cells[row][otherCol].number == value
            }
        ) {
            return true
        }

        if (cells.indices.any { otherRow ->
                otherRow != row && cells[otherRow][col].number == value
            }
        ) {
            return true
        }

        val boxStartRow = row / 3 * 3
        val boxStartCol = col / 3 * 3
        for (boxRow in boxStartRow until boxStartRow + 3) {
            for (boxCol in boxStartCol until boxStartCol + 3) {
                if ((boxRow != row || boxCol != col) && cells[boxRow][boxCol].number == value) {
                    return true
                }
            }
        }

        return false
    }

    fun solutionValueAt(row: Int, col: Int): Int? {
        return solution.getOrNull(row)?.getOrNull(col)
    }

    fun playerValues(): IntArray = IntArray(81) { index ->
        cells[index / 9][index % 9].number
    }

    fun solutionValues(): IntArray = IntArray(81) { index ->
        solution[index / 9][index % 9]
    }


    fun isBoardCorrect(): Boolean {
        for (i in cells.indices) {
            for (j in cells[i].indices) {
                if (cells[i][j].number == 0 || cells[i][j].number != solution[i][j]) {
                    return false
                }
            }
        }
        return true
    }


}
