package rew.lightgames.sudoku2

import java.util.Collections

sealed interface CandidateGridCreationResult {
    data class Success(val grid: CandidateGrid) : CandidateGridCreationResult
    data class Failure(val error: CandidateGridValidationError) : CandidateGridCreationResult
}

sealed interface CandidateGridValidationError {
    data class WrongBoardSize(val actualSize: Int) : CandidateGridValidationError
    data class InvalidValue(val cellIndex: Int, val value: Int) : CandidateGridValidationError
    data class DuplicateGiven(val house: HouseRef, val digit: Int) : CandidateGridValidationError
    data class NoCandidates(val cell: CellRef) : CandidateGridValidationError
}

sealed interface CandidateGridMutationResult {
    object Success : CandidateGridMutationResult
    data class Failure(val error: CandidateGridMutationError) : CandidateGridMutationResult
}

sealed interface CandidateGridMutationError {
    data class InvalidDigit(val digit: Int) : CandidateGridMutationError
    object EmptyElimination : CandidateGridMutationError
    data class CellAlreadyPlaced(val cell: CellRef, val value: Int) : CandidateGridMutationError
    data class DigitNotCandidate(val cell: CellRef, val digit: Int) : CandidateGridMutationError
    data class NoCandidatesRemoved(val cell: CellRef, val requested: DigitSet) : CandidateGridMutationError
    data class WouldCreateContradiction(val cell: CellRef) : CandidateGridMutationError
    data class DuplicatePlacedValue(val house: HouseRef, val digit: Int) : CandidateGridMutationError
}

/**
 * Mutable logical state for a Sudoku board.
 *
 * Candidate masks are authoritative after creation. Placements only subtract from existing masks;
 * they never derive all masks again from placed values, so logical eliminations persist.
 */
class CandidateGrid private constructor(
    private val values: IntArray,
    private val candidateMasks: IntArray
) {
    fun valueAt(cell: CellRef): Int = values[cell.index]

    fun candidatesAt(cell: CellRef): DigitSet = DigitSet.fromMask(candidateMasks[cell.index])

    fun cellsRowMajor(): List<CellRef> = SudokuTopology.allCells

    fun cellsIn(house: HouseRef): List<CellRef> = SudokuTopology.cellsIn(house)

    fun housesFor(cell: CellRef): List<HouseRef> = SudokuTopology.housesFor(cell)

    fun peersOf(cell: CellRef): List<CellRef> = SudokuTopology.peersOf(cell)

    fun candidatePositions(house: HouseRef, digit: Int): List<CellRef> {
        DigitSet.requireDigit(digit)
        val bit = DigitSet.bitFor(digit)
        return immutableCellList(cellsIn(house).filter { candidateMasks[it.index] and bit != 0 })
    }

    fun snapshot(): CandidateGridSnapshot = CandidateGridSnapshot(values, candidateMasks)

    fun place(cell: CellRef, digit: Int): CandidateGridMutationResult {
        if (digit !in 1..9) {
            return CandidateGridMutationResult.Failure(
                CandidateGridMutationError.InvalidDigit(digit)
            )
        }
        val index = cell.index
        val existingValue = values[index]
        if (existingValue != 0) {
            return CandidateGridMutationResult.Failure(
                CandidateGridMutationError.CellAlreadyPlaced(cell, existingValue)
            )
        }

        val digitBit = DigitSet.bitFor(digit)
        if (candidateMasks[index] and digitBit == 0) {
            return CandidateGridMutationResult.Failure(
                CandidateGridMutationError.DigitNotCandidate(cell, digit)
            )
        }

        val nextValues = values.clone()
        val nextCandidateMasks = candidateMasks.clone()
        nextValues[index] = digit
        nextCandidateMasks[index] = 0
        for (peer in SudokuTopology.peersOf(cell)) {
            if (nextValues[peer.index] == 0) {
                nextCandidateMasks[peer.index] = nextCandidateMasks[peer.index] and digitBit.inv()
            }
        }

        findDuplicate(nextValues)?.let { (house, duplicateDigit) ->
            return CandidateGridMutationResult.Failure(
                CandidateGridMutationError.DuplicatePlacedValue(house, duplicateDigit)
            )
        }
        findEmptyCellWithoutCandidates(nextValues, nextCandidateMasks)?.let { contradictionCell ->
            return CandidateGridMutationResult.Failure(
                CandidateGridMutationError.WouldCreateContradiction(contradictionCell)
            )
        }

        nextValues.copyInto(values)
        nextCandidateMasks.copyInto(candidateMasks)
        return CandidateGridMutationResult.Success
    }

    fun eliminate(cell: CellRef, digits: DigitSet): CandidateGridMutationResult {
        if (digits.isEmpty) {
            return CandidateGridMutationResult.Failure(CandidateGridMutationError.EmptyElimination)
        }
        val index = cell.index
        val existingValue = values[index]
        if (existingValue != 0) {
            return CandidateGridMutationResult.Failure(
                CandidateGridMutationError.CellAlreadyPlaced(cell, existingValue)
            )
        }

        val existingMask = candidateMasks[index]
        val removedMask = existingMask and digits.mask
        if (removedMask == 0) {
            return CandidateGridMutationResult.Failure(
                CandidateGridMutationError.NoCandidatesRemoved(cell, digits)
            )
        }

        val remainingMask = existingMask and removedMask.inv()
        if (remainingMask == 0) {
            return CandidateGridMutationResult.Failure(
                CandidateGridMutationError.WouldCreateContradiction(cell)
            )
        }

        candidateMasks[index] = remainingMask
        return CandidateGridMutationResult.Success
    }

    companion object {
        fun create(board: IntArray): CandidateGridCreationResult {
            if (board.size != 81) {
                return CandidateGridCreationResult.Failure(
                    CandidateGridValidationError.WrongBoardSize(board.size)
                )
            }

            for (index in board.indices) {
                if (board[index] !in 0..9) {
                    return CandidateGridCreationResult.Failure(
                        CandidateGridValidationError.InvalidValue(index, board[index])
                    )
                }
            }

            findDuplicate(board)?.let { (house, digit) ->
                return CandidateGridCreationResult.Failure(
                    CandidateGridValidationError.DuplicateGiven(house, digit)
                )
            }

            val values = board.clone()
            val candidateMasks = IntArray(81)
            for (index in values.indices) {
                if (values[index] != 0) continue
                val cell = CellRef.fromIndex(index)
                var mask = DigitSet.ALL_DIGITS.mask
                for (peer in SudokuTopology.peersOf(cell)) {
                    val peerValue = values[peer.index]
                    if (peerValue != 0) {
                        mask = mask and DigitSet.bitFor(peerValue).inv()
                    }
                }
                if (mask == 0) {
                    return CandidateGridCreationResult.Failure(
                        CandidateGridValidationError.NoCandidates(cell)
                    )
                }
                candidateMasks[index] = mask
            }
            return CandidateGridCreationResult.Success(CandidateGrid(values, candidateMasks))
        }

        private fun findDuplicate(board: IntArray): Pair<HouseRef, Int>? {
            for (house in SudokuTopology.allHouses) {
                var seenMask = 0
                for (cell in SudokuTopology.cellsIn(house)) {
                    val digit = board[cell.index]
                    if (digit == 0) continue
                    val bit = DigitSet.bitFor(digit)
                    if (seenMask and bit != 0) return house to digit
                    seenMask = seenMask or bit
                }
            }
            return null
        }

        private fun findEmptyCellWithoutCandidates(
            values: IntArray,
            candidateMasks: IntArray
        ): CellRef? {
            for (index in values.indices) {
                if (values[index] == 0 && candidateMasks[index] == 0) {
                    return CellRef.fromIndex(index)
                }
            }
            return null
        }
    }
}

class CandidateGridSnapshot internal constructor(values: IntArray, candidateMasks: IntArray) {
    private val valueState: IntArray = values.clone()
    private val candidateState: IntArray = candidateMasks.clone()

    val values: IntArray
        get() = valueState.clone()

    val candidateMasks: IntArray
        get() = candidateState.clone()

    fun valueAt(cell: CellRef): Int = valueState[cell.index]

    fun candidatesAt(cell: CellRef): DigitSet = DigitSet.fromMask(candidateState[cell.index])

    override fun equals(other: Any?): Boolean =
        other is CandidateGridSnapshot &&
            valueState.contentEquals(other.valueState) &&
            candidateState.contentEquals(other.candidateState)

    override fun hashCode(): Int = 31 * valueState.contentHashCode() + candidateState.contentHashCode()

    override fun toString(): String = buildString {
        append("CandidateGridSnapshot(values=")
        append(valueState.joinToString(prefix = "[", postfix = "]"))
        append(", candidateMasks=")
        append(candidateState.joinToString(prefix = "[", postfix = "]"))
        append(')')
    }
}

private object SudokuTopology {
    val allCells: List<CellRef> = immutableCellList((0..80).map(CellRef::fromIndex))

    val allHouses: List<HouseRef> = immutableHouseList(
        HouseType.entries.flatMap { type -> (0..8).map { index -> HouseRef(type, index) } }
    )

    private val cellsByHouse: Map<HouseRef, List<CellRef>> = allHouses.associateWith { house ->
        val cells = when (house.type) {
            HouseType.ROW -> (0..8).map { column -> CellRef(house.index, column) }
            HouseType.COLUMN -> (0..8).map { row -> CellRef(row, house.index) }
            HouseType.BOX -> {
                val startRow = (house.index / 3) * 3
                val startColumn = (house.index % 3) * 3
                (0..2).flatMap { rowOffset ->
                    (0..2).map { columnOffset ->
                        CellRef(startRow + rowOffset, startColumn + columnOffset)
                    }
                }
            }
        }
        immutableCellList(cells)
    }

    private val housesByCell: List<List<HouseRef>> = (0..80).map { index ->
        val cell = CellRef.fromIndex(index)
        immutableHouseList(
            listOf(
                HouseRef(HouseType.ROW, cell.row),
                HouseRef(HouseType.COLUMN, cell.column),
                HouseRef(HouseType.BOX, (cell.row / 3) * 3 + cell.column / 3)
            ).sorted()
        )
    }

    private val peersByCell: List<List<CellRef>> = (0..80).map { index ->
        val cell = CellRef.fromIndex(index)
        immutableCellList(
            housesByCell[index]
                .flatMap { house -> cellsByHouse.getValue(house) }
                .filter { it != cell }
                .distinct()
                .sorted()
        )
    }

    fun cellsIn(house: HouseRef): List<CellRef> = cellsByHouse.getValue(house)

    fun housesFor(cell: CellRef): List<HouseRef> = housesByCell[cell.index]

    fun peersOf(cell: CellRef): List<CellRef> = peersByCell[cell.index]
}

private fun immutableCellList(values: Collection<CellRef>): List<CellRef> =
    Collections.unmodifiableList(ArrayList(values))

private fun immutableHouseList(values: Collection<HouseRef>): List<HouseRef> =
    Collections.unmodifiableList(ArrayList(values))
