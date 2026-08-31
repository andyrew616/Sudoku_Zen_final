package rew.lightgames.sudoku2

import java.util.Random

data class GeneratedPuzzle(
    val puzzle: IntArray,
    val solution: IntArray,
    val seed: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GeneratedPuzzle) return false
        return seed == other.seed &&
                puzzle.contentEquals(other.puzzle) &&
                solution.contentEquals(other.solution)
    }

    override fun hashCode(): Int {
        var result = seed.hashCode()
        result = 31 * result + puzzle.contentHashCode()
        result = 31 * result + solution.contentHashCode()
        return result
    }
}

class SudokuPuzzleEngine(private val seed: Long = System.currentTimeMillis()) {

    companion object {
        private const val SIZE = 9
        private const val BOX_SIZE = 3
        private const val TOTAL_CELLS = SIZE * SIZE
    }

    fun generate(): GeneratedPuzzle {
        val rng = Random(seed)
        val solution = generateCompleteGrid(rng)
        val solutionCopy = solution.clone()
        val puzzle = removeClues(solution, rng)
        return GeneratedPuzzle(puzzle, solutionCopy, seed)
    }

    fun countSolutions(board: IntArray, limit: Int = 2): Int {
        if (limit <= 0) return 0
        try {
            validateBoard(board)
        } catch (e: IllegalArgumentException) {
            return 0
        }
        val grid = board.clone()
        val solutionCount = intArrayOf(0)
        countSolutionsMRV(grid, limit, solutionCount = solutionCount)
        return solutionCount[0]
    }

    fun solve(board: IntArray): IntArray? {
        try {
            validateBoard(board)
        } catch (e: IllegalArgumentException) {
            return null
        }
        val grid = board.clone()
        return if (solveMRV(grid)) grid else null
    }

    // --- Validation ---

    internal fun validateBoard(board: IntArray) {
        require(board.size == TOTAL_CELLS) {
            "Board must have exactly $TOTAL_CELLS cells, got ${board.size}"
        }
        for (i in board.indices) {
            require(board[i] in 0..9) {
                "Cell $i has value ${board[i]}, must be in 0..9"
            }
        }
        for (r in 0 until SIZE) {
            val seen = mutableSetOf<Int>()
            for (c in 0 until SIZE) {
                val v = board[r * SIZE + c]
                if (v == 0) continue
                if (!seen.add(v)) {
                    throw IllegalArgumentException(
                        "Board has duplicate value $v in row $r"
                    )
                }
            }
        }
        for (c in 0 until SIZE) {
            val seen = mutableSetOf<Int>()
            for (r in 0 until SIZE) {
                val v = board[r * SIZE + c]
                if (v == 0) continue
                if (!seen.add(v)) {
                    throw IllegalArgumentException(
                        "Board has duplicate value $v in column $c"
                    )
                }
            }
        }
        for (br in 0 until BOX_SIZE) {
            for (bc in 0 until BOX_SIZE) {
                val seen = mutableSetOf<Int>()
                for (r in br * BOX_SIZE until br * BOX_SIZE + BOX_SIZE) {
                    for (c in bc * BOX_SIZE until bc * BOX_SIZE + BOX_SIZE) {
                        val v = board[r * SIZE + c]
                        if (v == 0) continue
                        if (!seen.add(v)) {
                            throw IllegalArgumentException(
                                "Board has duplicate value $v in box ($br,$bc)"
                            )
                        }
                    }
                }
            }
        }
    }

    // --- Completed grid generation ---

    internal fun generateCompleteGrid(rng: Random): IntArray {
        val grid = IntArray(TOTAL_CELLS)
        fillGrid(grid, rng)
        return grid
    }

    private fun fillGrid(grid: IntArray, rng: Random): Boolean {
        val emptyIdx = findFirstEmpty(grid)
        if (emptyIdx == -1) return true

        val row = emptyIdx / SIZE
        val col = emptyIdx % SIZE
        val candidates = getCandidates(grid, row, col)
        shuffleArray(candidates, rng)

        for (digit in candidates) {
            grid[emptyIdx] = digit
            if (fillGrid(grid, rng)) return true
            grid[emptyIdx] = 0
        }
        return false
    }

    // --- Clue removal with early termination ---

    internal fun removeClues(solution: IntArray, rng: Random): IntArray {
        val puzzle = solution.clone()
        val order = IntArray(TOTAL_CELLS) { it }
        shuffleArray(order, rng)

        for (idx in order) {
            if (puzzle[idx] == 0) continue
            val backup = puzzle[idx]
            puzzle[idx] = 0

            if (countSolutionsInternal(puzzle, 2) != 1) {
                puzzle[idx] = backup
            }
        }

        return puzzle
    }

    // --- Internal solution counter (no validation, no clone) ---

    private fun countSolutionsInternal(board: IntArray, limit: Int): Int {
        val grid = board.clone()
        val solutionCount = intArrayOf(0)
        countSolutionsMRV(
            grid = grid,
            limit = limit,
            solutionCount = solutionCount
        )
        return solutionCount[0]
    }

    // --- MRV-based solution counter ---

    private fun countSolutionsMRV(
        grid: IntArray,
        limit: Int,
        solutionCount: IntArray
    ): Boolean {
        val pos = findBestEmptyCell(grid)
        if (pos == -1) {
            solutionCount[0]++
            return solutionCount[0] >= limit
        }

        val row = pos / SIZE
        val col = pos % SIZE
        val candidates = getCandidates(grid, row, col)

        for (digit in candidates) {
            grid[pos] = digit
            if (countSolutionsMRV(grid, limit, solutionCount)) {
                grid[pos] = 0
                return true
            }
            grid[pos] = 0
        }
        return false
    }

    // --- MRV-based solver ---

    private fun solveMRV(grid: IntArray): Boolean {
        val pos = findBestEmptyCell(grid)
        if (pos == -1) return true

        val row = pos / SIZE
        val col = pos % SIZE

        for (digit in 1..SIZE) {
            if (canPlace(grid, row, col, digit)) {
                grid[pos] = digit
                if (solveMRV(grid)) return true
                grid[pos] = 0
            }
        }
        return false
    }

    // --- MRV cell selection ---

    private fun findBestEmptyCell(board: IntArray): Int {
        var bestPos = -1
        var bestCount = SIZE + 1

        for (i in 0 until TOTAL_CELLS) {
            if (board[i] != 0) continue
            val row = i / SIZE
            val col = i % SIZE
            var count = 0
            for (d in 1..SIZE) {
                if (canPlace(board, row, col, d)) count++
            }
            if (count == 0) return i
            if (count < bestCount) {
                bestCount = count
                bestPos = i
                if (count == 1) return i
            }
        }
        return bestPos
    }

    // --- Constraint helpers ---

    internal fun canPlace(board: IntArray, row: Int, col: Int, value: Int): Boolean {
        for (i in 0 until SIZE) {
            if (board[row * SIZE + i] == value) return false
        }
        for (i in 0 until SIZE) {
            if (board[i * SIZE + col] == value) return false
        }
        val boxRow = (row / BOX_SIZE) * BOX_SIZE
        val boxCol = (col / BOX_SIZE) * BOX_SIZE
        for (r in boxRow until boxRow + BOX_SIZE) {
            for (c in boxCol until boxCol + BOX_SIZE) {
                if (board[r * SIZE + c] == value) return false
            }
        }
        return true
    }

    internal fun getCandidates(board: IntArray, row: Int, col: Int): IntArray {
        val candidates = mutableListOf<Int>()
        for (d in 1..SIZE) {
            if (canPlace(board, row, col, d)) candidates.add(d)
        }
        return candidates.toIntArray()
    }

    private fun findFirstEmpty(board: IntArray): Int {
        for (i in board.indices) {
            if (board[i] == 0) return i
        }
        return -1
    }

    private fun shuffleArray(arr: IntArray, rng: Random) {
        for (i in arr.size - 1 downTo 1) {
            val j = rng.nextInt(i + 1)
            val tmp = arr[i]
            arr[i] = arr[j]
            arr[j] = tmp
        }
    }

    internal fun boardToString(board: IntArray): String {
        require(board.size == TOTAL_CELLS)
        val sb = StringBuilder()
        for (r in 0 until SIZE) {
            if (r > 0 && r % BOX_SIZE == 0) sb.append("---+---+---\n")
            for (c in 0 until SIZE) {
                if (c > 0 && c % BOX_SIZE == 0) sb.append("|")
                sb.append(board[r * SIZE + c])
            }
            sb.append("\n")
        }
        return sb.toString()
    }
}
