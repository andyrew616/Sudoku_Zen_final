package rew.lightgames.sudoku2

import java.util.Collections

sealed interface AutoNotesResult {
    class Available internal constructor(
        private val candidatesByCell: List<List<Int>>
    ) : AutoNotesResult {
        init {
            require(candidatesByCell.size == CELL_COUNT)
        }

        fun candidatesAt(row: Int, column: Int): List<Int> {
            require(row in 0 until BOARD_SIZE && column in 0 until BOARD_SIZE)
            return candidatesByCell[row * BOARD_SIZE + column]
        }

        override fun equals(other: Any?): Boolean =
            other is Available && candidatesByCell == other.candidatesByCell

        override fun hashCode(): Int = candidatesByCell.hashCode()

        override fun toString(): String = "Available(candidatesByCell=$candidatesByCell)"

        companion object {
            private const val BOARD_SIZE = 9
            private const val CELL_COUNT = BOARD_SIZE * BOARD_SIZE
        }
    }

    data object InvalidBoard : AutoNotesResult
}

/**
 * Computes basic Sudoku candidates from placed values only. It deliberately has no solution,
 * note, or logical-solver input, so advanced logical eliminations cannot leak into Auto Notes.
 */
object AutoNotesCalculator {
    private const val BOARD_SIZE = 9
    private const val CELL_COUNT = BOARD_SIZE * BOARD_SIZE
    private const val ALL_DIGITS_MASK = (1 shl BOARD_SIZE) - 1

    fun compute(
        playerValues: IntArray,
        editableCells: BooleanArray
    ): AutoNotesResult {
        if (playerValues.size != CELL_COUNT || editableCells.size != CELL_COUNT) {
            return AutoNotesResult.InvalidBoard
        }

        val rowMasks = IntArray(BOARD_SIZE)
        val columnMasks = IntArray(BOARD_SIZE)
        val boxMasks = IntArray(BOARD_SIZE)

        playerValues.forEachIndexed { index, value ->
            if (value !in 0..BOARD_SIZE) return AutoNotesResult.InvalidBoard
            if (value == 0) return@forEachIndexed

            val row = index / BOARD_SIZE
            val column = index % BOARD_SIZE
            val box = row / 3 * 3 + column / 3
            val bit = 1 shl (value - 1)
            if (
                rowMasks[row] and bit != 0 ||
                columnMasks[column] and bit != 0 ||
                boxMasks[box] and bit != 0
            ) {
                return AutoNotesResult.InvalidBoard
            }
            rowMasks[row] = rowMasks[row] or bit
            columnMasks[column] = columnMasks[column] or bit
            boxMasks[box] = boxMasks[box] or bit
        }

        val candidates = ArrayList<List<Int>>(CELL_COUNT)
        for (index in 0 until CELL_COUNT) {
            if (playerValues[index] != 0 || !editableCells[index]) {
                candidates.add(emptyList())
                continue
            }

            val row = index / BOARD_SIZE
            val column = index % BOARD_SIZE
            val box = row / 3 * 3 + column / 3
            val unavailable = rowMasks[row] or columnMasks[column] or boxMasks[box]
            val legalMask = ALL_DIGITS_MASK and unavailable.inv()
            if (legalMask == 0) return AutoNotesResult.InvalidBoard

            val cellCandidates = ArrayList<Int>(BOARD_SIZE)
            for (digit in 1..BOARD_SIZE) {
                if (legalMask and (1 shl (digit - 1)) != 0) {
                    cellCandidates += digit
                }
            }
            candidates.add(Collections.unmodifiableList(cellCandidates))
        }

        return AutoNotesResult.Available(Collections.unmodifiableList(candidates))
    }
}
