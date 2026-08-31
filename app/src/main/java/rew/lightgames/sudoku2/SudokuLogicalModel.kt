package rew.lightgames.sudoku2

import java.util.Collections

/** A zero-based location in a 9x9 Sudoku grid. */
data class CellRef(val row: Int, val column: Int) : Comparable<CellRef> {
    init {
        require(row in 0..8) { "Row must be in 0..8, got $row" }
        require(column in 0..8) { "Column must be in 0..8, got $column" }
    }

    val index: Int = row * 9 + column

    override fun compareTo(other: CellRef): Int = index.compareTo(other.index)

    companion object {
        fun fromIndex(index: Int): CellRef {
            require(index in 0..80) { "Cell index must be in 0..80, got $index" }
            return CellRef(index / 9, index % 9)
        }
    }
}

enum class HouseType {
    ROW,
    COLUMN,
    BOX
}

/** A zero-based row, column, or box reference. Boxes are ordered row-major. */
data class HouseRef(val type: HouseType, val index: Int) : Comparable<HouseRef> {
    init {
        require(index in 0..8) { "House index must be in 0..8, got $index" }
    }

    override fun compareTo(other: HouseRef): Int {
        val typeComparison = type.ordinal.compareTo(other.type.ordinal)
        return if (typeComparison != 0) typeComparison else index.compareTo(other.index)
    }
}

/** Immutable set of Sudoku digits backed by the low nine bits of an Int. */
class DigitSet private constructor(internal val mask: Int) {
    val size: Int
        get() = Integer.bitCount(mask)

    val count: Int
        get() = size

    val isEmpty: Boolean
        get() = mask == 0

    operator fun contains(digit: Int): Boolean {
        requireDigit(digit)
        return mask and bitFor(digit) != 0
    }

    fun digitsAscending(): List<Int> = (1..9).filter { mask and bitFor(it) != 0 }

    fun add(digit: Int): DigitSet {
        requireDigit(digit)
        return fromMask(mask or bitFor(digit))
    }

    fun remove(digit: Int): DigitSet {
        requireDigit(digit)
        return fromMask(mask and bitFor(digit).inv())
    }

    fun remove(digits: DigitSet): DigitSet = fromMask(mask and digits.mask.inv())

    override fun equals(other: Any?): Boolean = other is DigitSet && mask == other.mask

    override fun hashCode(): Int = mask

    override fun toString(): String = digitsAscending().joinToString(prefix = "{", postfix = "}")

    companion object {
        private const val ALL_MASK = (1 shl 9) - 1

        val EMPTY: DigitSet = DigitSet(0)
        val ALL_DIGITS: DigitSet = DigitSet(ALL_MASK)

        fun of(vararg digits: Int): DigitSet {
            var mask = 0
            for (digit in digits) {
                requireDigit(digit)
                mask = mask or bitFor(digit)
            }
            return fromMask(mask)
        }

        internal fun fromMask(mask: Int): DigitSet {
            require(mask and ALL_MASK.inv() == 0) { "Candidate mask contains bits outside digits 1..9" }
            return when (mask) {
                0 -> EMPTY
                ALL_MASK -> ALL_DIGITS
                else -> DigitSet(mask)
            }
        }

        internal fun bitFor(digit: Int): Int = 1 shl (digit - 1)

        internal fun requireDigit(digit: Int) {
            require(digit in 1..9) { "Digit must be in 1..9, got $digit" }
        }
    }
}

enum class SudokuTechnique {
    NAKED_SINGLE,
    HIDDEN_SINGLE,
    LOCKED_CANDIDATES_POINTING,
    LOCKED_CANDIDATES_CLAIMING,
    NAKED_PAIR,
    HIDDEN_PAIR,
    NAKED_TRIPLE,
    HIDDEN_TRIPLE,
    X_WING,
    XY_WING
}

sealed interface SolveAction {
    val cell: CellRef

    data class PlaceValue(override val cell: CellRef, val digit: Int) : SolveAction {
        init {
            DigitSet.requireDigit(digit)
        }
    }

    data class EliminateCandidates(override val cell: CellRef, val digits: DigitSet) : SolveAction {
        init {
            require(!digits.isEmpty) { "An elimination must contain at least one digit" }
        }
    }
}

/** Structured, presentation-neutral facts supporting a future logical step. */
sealed interface StepEvidence {
    data class Single(
        val cell: CellRef,
        val digit: Int,
        val candidates: DigitSet,
        val uniqueIn: HouseRef? = null
    ) : StepEvidence {
        init {
            DigitSet.requireDigit(digit)
            require(!candidates.isEmpty) { "Single evidence candidates must not be empty" }
            require(digit in candidates) { "Single evidence candidates must contain the digit" }
            require(uniqueIn == null || cell.belongsTo(uniqueIn)) {
                "Single evidence house must contain the target cell"
            }
        }
    }

    class LockedCandidates(
        val digit: Int,
        val sourceHouse: HouseRef,
        val targetHouse: HouseRef,
        sourceCells: Collection<CellRef>
    ) : StepEvidence {
        val sourceCells: List<CellRef> = immutableSortedDistinct(sourceCells)

        init {
            DigitSet.requireDigit(digit)
            require(this.sourceCells.isNotEmpty()) { "Locked candidates require source cells" }
            require(sourceHouse != targetHouse) { "Source and target houses must differ" }
        }

        override fun equals(other: Any?): Boolean =
            other is LockedCandidates &&
                digit == other.digit &&
                sourceHouse == other.sourceHouse &&
                targetHouse == other.targetHouse &&
                sourceCells == other.sourceCells

        override fun hashCode(): Int {
            var result = digit
            result = 31 * result + sourceHouse.hashCode()
            result = 31 * result + targetHouse.hashCode()
            result = 31 * result + sourceCells.hashCode()
            return result
        }

        override fun toString(): String =
            "LockedCandidates(digit=$digit, sourceHouse=$sourceHouse, " +
                "targetHouse=$targetHouse, sourceCells=$sourceCells)"
    }

    class Subset(
        val house: HouseRef,
        val digits: DigitSet,
        cells: Collection<CellRef>,
        val hidden: Boolean = false
    ) : StepEvidence {
        val cells: List<CellRef> = immutableSortedDistinct(cells)

        init {
            require(!digits.isEmpty) { "Subset digits must not be empty" }
            require(this.cells.isNotEmpty()) { "Subset cells must not be empty" }
            require(digits.size == this.cells.size) {
                "Subset evidence must contain the same number of digits and cells"
            }
        }

        override fun equals(other: Any?): Boolean =
            other is Subset &&
                house == other.house &&
                digits == other.digits &&
                cells == other.cells &&
                hidden == other.hidden

        override fun hashCode(): Int {
            var result = house.hashCode()
            result = 31 * result + digits.hashCode()
            result = 31 * result + cells.hashCode()
            result = 31 * result + hidden.hashCode()
            return result
        }

        override fun toString(): String =
            "Subset(house=$house, digits=$digits, cells=$cells, hidden=$hidden)"
    }

    class Fish(
        val digit: Int,
        baseHouses: Collection<HouseRef>,
        coverHouses: Collection<HouseRef>,
        cells: Collection<CellRef>
    ) : StepEvidence {
        val baseHouses: List<HouseRef> = immutableSortedDistinct(baseHouses)
        val coverHouses: List<HouseRef> = immutableSortedDistinct(coverHouses)
        val cells: List<CellRef> = immutableSortedDistinct(cells)

        init {
            DigitSet.requireDigit(digit)
            require(this.baseHouses.size == 2) { "X-Wing evidence requires two base houses" }
            require(this.coverHouses.size == 2) { "X-Wing evidence requires two cover houses" }
            require(this.cells.size == 4) { "X-Wing evidence requires four supporting cells" }
            require(this.baseHouses.map { it.type }.distinct().size == 1) {
                "X-Wing base houses must have one type"
            }
            require(this.coverHouses.map { it.type }.distinct().size == 1) {
                "X-Wing cover houses must have one type"
            }
            require(this.baseHouses.first().type != this.coverHouses.first().type) {
                "X-Wing base and cover house types must differ"
            }
        }

        override fun equals(other: Any?): Boolean =
            other is Fish &&
                digit == other.digit &&
                baseHouses == other.baseHouses &&
                coverHouses == other.coverHouses &&
                cells == other.cells

        override fun hashCode(): Int {
            var result = digit
            result = 31 * result + baseHouses.hashCode()
            result = 31 * result + coverHouses.hashCode()
            result = 31 * result + cells.hashCode()
            return result
        }

        override fun toString(): String =
            "Fish(digit=$digit, baseHouses=$baseHouses, coverHouses=$coverHouses, cells=$cells)"
    }

    class XYWing(
        val pivot: CellRef,
        pincers: Collection<CellRef>,
        val pivotDigits: DigitSet,
        val eliminationDigit: Int
    ) : StepEvidence {
        val pincers: List<CellRef> = immutableSortedDistinct(pincers)

        init {
            DigitSet.requireDigit(eliminationDigit)
            require(this.pincers.size == 2) { "XY-Wing evidence requires two pincers" }
            require(pivot !in this.pincers) {
                "XY-Wing pivot and pincers must be distinct"
            }
            require(pivotDigits.size == 2) { "XY-Wing pivot must contain exactly two digits" }
            require(eliminationDigit !in pivotDigits) {
                "XY-Wing elimination digit must not be a pivot digit"
            }
        }

        val firstPincer: CellRef
            get() = pincers[0]

        val secondPincer: CellRef
            get() = pincers[1]

        val digits: DigitSet
            get() = pivotDigits.add(eliminationDigit)

        override fun equals(other: Any?): Boolean =
            other is XYWing &&
                pivot == other.pivot &&
                pincers == other.pincers &&
                pivotDigits == other.pivotDigits &&
                eliminationDigit == other.eliminationDigit

        override fun hashCode(): Int {
            var result = pivot.hashCode()
            result = 31 * result + pincers.hashCode()
            result = 31 * result + pivotDigits.hashCode()
            result = 31 * result + eliminationDigit
            return result
        }

        override fun toString(): String =
            "XYWing(pivot=$pivot, pincers=$pincers, pivotDigits=$pivotDigits, " +
                "eliminationDigit=$eliminationDigit)"
    }
}

class LogicalStep(
    val technique: SudokuTechnique,
    actions: Collection<SolveAction>,
    val evidence: StepEvidence
) {
    val actions: List<SolveAction>

    init {
        require(actions.isNotEmpty()) { "A logical step must contain at least one action" }
        require(actions.distinct().size == actions.size) { "A logical step must not contain duplicate actions" }

        val actionsByCell = actions.groupBy { it.cell }
        require(actionsByCell.values.all { it.size == 1 }) {
            "A logical step must contain at most one action per cell"
        }

        if (evidence is StepEvidence.Single) {
            require(
                actions.singleOrNull() == SolveAction.PlaceValue(evidence.cell, evidence.digit)
            ) { "Single evidence must match its sole placement action" }
        }

        this.actions = immutableList(actions.sortedWith(SOLVE_ACTION_COMPARATOR))
    }

    override fun equals(other: Any?): Boolean =
        other is LogicalStep &&
            technique == other.technique &&
            actions == other.actions &&
            evidence == other.evidence

    override fun hashCode(): Int {
        var result = technique.hashCode()
        result = 31 * result + actions.hashCode()
        result = 31 * result + evidence.hashCode()
        return result
    }

    override fun toString(): String =
        "LogicalStep(technique=$technique, actions=$actions, evidence=$evidence)"

}

internal val SOLVE_ACTION_COMPARATOR = Comparator<SolveAction> { left, right ->
    val cellComparison = left.cell.compareTo(right.cell)
    if (cellComparison != 0) {
        cellComparison
    } else {
        val leftType = if (left is SolveAction.PlaceValue) 0 else 1
        val rightType = if (right is SolveAction.PlaceValue) 0 else 1
        val typeComparison = leftType.compareTo(rightType)
        if (typeComparison != 0) {
            typeComparison
        } else {
            val leftValue = when (left) {
                is SolveAction.PlaceValue -> left.digit
                is SolveAction.EliminateCandidates -> left.digits.mask
            }
            val rightValue = when (right) {
                is SolveAction.PlaceValue -> right.digit
                is SolveAction.EliminateCandidates -> right.digits.mask
            }
            leftValue.compareTo(rightValue)
        }
    }
}

private fun <T : Comparable<T>> immutableSortedDistinct(values: Collection<T>): List<T> =
    immutableList(values.distinct().sorted())

private fun <T> immutableList(values: Collection<T>): List<T> =
    Collections.unmodifiableList(ArrayList(values))

private fun CellRef.belongsTo(house: HouseRef): Boolean = when (house.type) {
    HouseType.ROW -> row == house.index
    HouseType.COLUMN -> column == house.index
    HouseType.BOX -> (row / 3) * 3 + column / 3 == house.index
}
