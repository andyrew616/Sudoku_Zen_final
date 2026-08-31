package rew.lightgames.sudoku2

import java.util.Collections

enum class HintDetailLevel {
    TECHNIQUE,
    EVIDENCE,
    ACTION
}

enum class HintExplanationKey {
    NAKED_SINGLE,
    HIDDEN_SINGLE_ROW,
    HIDDEN_SINGLE_COLUMN,
    HIDDEN_SINGLE_BOX,
    POINTING_ROW,
    POINTING_COLUMN,
    CLAIMING_ROW,
    CLAIMING_COLUMN,
    NAKED_PAIR,
    HIDDEN_PAIR,
    NAKED_TRIPLE,
    HIDDEN_TRIPLE,
    X_WING_ROW,
    X_WING_COLUMN,
    XY_WING,
    SKYSCRAPER_ROW,
    SKYSCRAPER_COLUMN,
    TWO_STRING_KITE
}

enum class HintHighlightRole {
    TARGET,
    SUPPORT,
    HOUSE,
    ELIMINATION
}

data class HintHighlight(
    val cell: CellRef,
    val role: HintHighlightRole,
    val digit: Int? = null
) {
    init {
        if (digit != null) DigitSet.requireDigit(digit)
    }
}

/**
 * Bounded structured arguments for Android explanation rendering.
 *
 * The generic primary/secondary groups retain technique-specific roles without carrying prose or
 * Android resources into the logical layer. For example, an X-Wing uses primary/secondary houses
 * for base/cover houses, while a Skyscraper uses primary/secondary cells for aligned cells/towers.
 */
class HintExplanationArgs(
    val digit: Int? = null,
    val digits: DigitSet = DigitSet.EMPTY,
    val orientation: HouseType? = null,
    val focusCell: CellRef? = null,
    primaryCells: Collection<CellRef> = emptyList(),
    secondaryCells: Collection<CellRef> = emptyList(),
    primaryHouses: Collection<HouseRef> = emptyList(),
    secondaryHouses: Collection<HouseRef> = emptyList()
) {
    val primaryCells: List<CellRef> = immutableSortedDistinct(primaryCells)
    val secondaryCells: List<CellRef> = immutableSortedDistinct(secondaryCells)
    val primaryHouses: List<HouseRef> = immutableSortedDistinct(primaryHouses)
    val secondaryHouses: List<HouseRef> = immutableSortedDistinct(secondaryHouses)

    init {
        if (digit != null) DigitSet.requireDigit(digit)
    }

    override fun equals(other: Any?): Boolean =
        other is HintExplanationArgs &&
            digit == other.digit &&
            digits == other.digits &&
            orientation == other.orientation &&
            focusCell == other.focusCell &&
            primaryCells == other.primaryCells &&
            secondaryCells == other.secondaryCells &&
            primaryHouses == other.primaryHouses &&
            secondaryHouses == other.secondaryHouses

    override fun hashCode(): Int {
        var result = digit ?: 0
        result = 31 * result + digits.hashCode()
        result = 31 * result + (orientation?.hashCode() ?: 0)
        result = 31 * result + (focusCell?.hashCode() ?: 0)
        result = 31 * result + primaryCells.hashCode()
        result = 31 * result + secondaryCells.hashCode()
        result = 31 * result + primaryHouses.hashCode()
        result = 31 * result + secondaryHouses.hashCode()
        return result
    }

    override fun toString(): String =
        "HintExplanationArgs(digit=$digit, digits=$digits, orientation=$orientation, " +
            "focusCell=$focusCell, primaryCells=$primaryCells, " +
            "secondaryCells=$secondaryCells, primaryHouses=$primaryHouses, " +
            "secondaryHouses=$secondaryHouses)"
}

/** Immutable, presentation-neutral logical hint derived from one [LogicalStep]. */
class LogicalHint(
    val technique: SudokuTechnique,
    val detailLevel: HintDetailLevel,
    targetCells: Collection<CellRef>,
    supportingCells: Collection<CellRef>,
    houses: Collection<HouseRef>,
    actions: Collection<SolveAction>,
    val explanationKey: HintExplanationKey,
    val explanationArgs: HintExplanationArgs,
    highlights: Collection<HintHighlight>
) {
    val targetCells: List<CellRef> = immutableSortedDistinct(targetCells)
    val supportingCells: List<CellRef> = immutableSortedDistinct(supportingCells)
    val houses: List<HouseRef> = immutableSortedDistinct(houses)
    val actions: List<SolveAction> = immutableActions(actions)
    val highlights: List<HintHighlight> = immutableHighlights(highlights)

    init {
        require(this.actions.isNotEmpty()) { "A logical hint must retain at least one action" }
        require(this.targetCells == this.actions.map { it.cell }.distinct().sorted()) {
            "Hint targets must exactly match its action targets"
        }
    }

    override fun equals(other: Any?): Boolean =
        other is LogicalHint &&
            technique == other.technique &&
            detailLevel == other.detailLevel &&
            targetCells == other.targetCells &&
            supportingCells == other.supportingCells &&
            houses == other.houses &&
            actions == other.actions &&
            explanationKey == other.explanationKey &&
            explanationArgs == other.explanationArgs &&
            highlights == other.highlights

    override fun hashCode(): Int {
        var result = technique.hashCode()
        result = 31 * result + detailLevel.hashCode()
        result = 31 * result + targetCells.hashCode()
        result = 31 * result + supportingCells.hashCode()
        result = 31 * result + houses.hashCode()
        result = 31 * result + actions.hashCode()
        result = 31 * result + explanationKey.hashCode()
        result = 31 * result + explanationArgs.hashCode()
        result = 31 * result + highlights.hashCode()
        return result
    }

    override fun toString(): String =
        "LogicalHint(technique=$technique, detailLevel=$detailLevel, " +
            "targetCells=$targetCells, supportingCells=$supportingCells, houses=$houses, " +
            "actions=$actions, explanationKey=$explanationKey, " +
            "explanationArgs=$explanationArgs, highlights=$highlights)"
}

sealed interface LogicalHintResult {
    data class Available(val hint: LogicalHint) : LogicalHintResult
    data object INVALID_PLAYER_STATE : LogicalHintResult
    data object INCORRECT_VALUE_PRESENT : LogicalHintResult
    data object NO_SUPPORTED_LOGICAL_HINT : LogicalHintResult
    data object SOLVED : LogicalHintResult
}

data class HintProgressionAdvance(
    val detailLevel: HintDetailLevel,
    val startsSequence: Boolean
)

/** Transient progression keyed only by placed values, never by selected cell or player notes. */
class LogicalHintProgression {
    private var boardValues: IntArray? = null
    private var detailLevel: HintDetailLevel? = null

    fun advance(currentValues: IntArray): HintProgressionAdvance {
        val sameBoard = boardValues?.contentEquals(currentValues) == true
        val nextLevel = if (!sameBoard) {
            HintDetailLevel.TECHNIQUE
        } else {
            when (detailLevel) {
                HintDetailLevel.TECHNIQUE -> HintDetailLevel.EVIDENCE
                HintDetailLevel.EVIDENCE,
                HintDetailLevel.ACTION -> HintDetailLevel.ACTION
                null -> HintDetailLevel.TECHNIQUE
            }
        }
        boardValues = currentValues.clone()
        detailLevel = nextLevel
        return HintProgressionAdvance(nextLevel, startsSequence = !sameBoard)
    }

    fun reset() {
        boardValues = null
        detailLevel = null
    }
}

private fun immutableActions(values: Collection<SolveAction>): List<SolveAction> =
    Collections.unmodifiableList(ArrayList(values.sortedWith(SOLVE_ACTION_COMPARATOR)))

private fun immutableHighlights(values: Collection<HintHighlight>): List<HintHighlight> =
    Collections.unmodifiableList(
        ArrayList(
            values.distinct().sortedWith(
                compareBy<HintHighlight>({ it.cell }, { it.role.ordinal }, { it.digit ?: 0 })
            )
        )
    )

private fun <T : Comparable<T>> immutableSortedDistinct(values: Collection<T>): List<T> =
    Collections.unmodifiableList(ArrayList(values.distinct().sorted()))
