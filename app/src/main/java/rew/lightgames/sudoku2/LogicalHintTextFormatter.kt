package rew.lightgames.sudoku2

import android.content.Context

/** Android-only localization for the presentation-neutral logical hint model. */
class LogicalHintTextFormatter(private val context: Context) {
    fun format(hint: LogicalHint): String = when (hint.detailLevel) {
        HintDetailLevel.TECHNIQUE -> context.getString(
            R.string.hint_technique_prompt,
            techniqueName(hint.explanationKey)
        )
        HintDetailLevel.EVIDENCE -> context.getString(
            R.string.hint_evidence_prompt,
            evidenceText(hint)
        )
        HintDetailLevel.ACTION -> context.getString(
            R.string.hint_action_prompt,
            evidenceText(hint),
            actionText(hint.actions)
        )
    }

    private fun evidenceText(hint: LogicalHint): String {
        val args = hint.explanationArgs
        return when (hint.explanationKey) {
            HintExplanationKey.NAKED_SINGLE -> context.getString(
                R.string.hint_evidence_naked_single,
                cell(requireNotNull(args.focusCell))
            )
            HintExplanationKey.HIDDEN_SINGLE_ROW,
            HintExplanationKey.HIDDEN_SINGLE_COLUMN,
            HintExplanationKey.HIDDEN_SINGLE_BOX -> context.getString(
                R.string.hint_evidence_hidden_single,
                cell(requireNotNull(args.focusCell)),
                house(args.primaryHouses.single())
            )
            HintExplanationKey.POINTING_ROW,
            HintExplanationKey.POINTING_COLUMN -> context.getString(
                R.string.hint_evidence_pointing,
                house(args.primaryHouses.single()),
                house(args.secondaryHouses.single()),
                cells(args.primaryCells)
            )
            HintExplanationKey.CLAIMING_ROW,
            HintExplanationKey.CLAIMING_COLUMN -> context.getString(
                R.string.hint_evidence_claiming,
                house(args.primaryHouses.single()),
                house(args.secondaryHouses.single()),
                cells(args.primaryCells)
            )
            HintExplanationKey.NAKED_PAIR,
            HintExplanationKey.HIDDEN_PAIR,
            HintExplanationKey.NAKED_TRIPLE,
            HintExplanationKey.HIDDEN_TRIPLE -> context.getString(
                R.string.hint_evidence_subset,
                techniqueName(hint.explanationKey),
                digits(args.digits),
                house(args.primaryHouses.single()),
                cells(args.primaryCells)
            )
            HintExplanationKey.X_WING_ROW,
            HintExplanationKey.X_WING_COLUMN -> context.getString(
                R.string.hint_evidence_x_wing,
                houses(args.primaryHouses),
                houses(args.secondaryHouses),
                cells(args.primaryCells)
            )
            HintExplanationKey.XY_WING -> context.getString(
                R.string.hint_evidence_xy_wing,
                cell(requireNotNull(args.focusCell)),
                cells(args.primaryCells)
            )
            HintExplanationKey.SKYSCRAPER_ROW,
            HintExplanationKey.SKYSCRAPER_COLUMN -> context.getString(
                R.string.hint_evidence_skyscraper,
                cells(args.primaryCells),
                cells(args.secondaryCells)
            )
            HintExplanationKey.TWO_STRING_KITE -> context.getString(
                R.string.hint_evidence_two_string_kite,
                cells(args.primaryCells),
                cells(args.secondaryCells)
            )
        }
    }

    private fun actionText(actions: List<SolveAction>): String {
        val placements = actions.filterIsInstance<SolveAction.PlaceValue>().map { action ->
            context.getString(
                R.string.hint_action_place,
                action.digit,
                cell(action.cell)
            )
        }
        val eliminations = actions.filterIsInstance<SolveAction.EliminateCandidates>()
            .groupBy { it.digits }
            .entries
            .sortedBy { it.key.mask }
            .map { (digits, groupedActions) ->
                context.getString(
                    R.string.hint_action_eliminate,
                    digits(digits),
                    cells(groupedActions.map { it.cell })
                )
            }
        return (placements + eliminations).joinToString(
            separator = context.getString(R.string.hint_action_separator)
        )
    }

    private fun techniqueName(key: HintExplanationKey): String = context.getString(
        when (key) {
            HintExplanationKey.NAKED_SINGLE -> R.string.hint_technique_naked_single
            HintExplanationKey.HIDDEN_SINGLE_ROW,
            HintExplanationKey.HIDDEN_SINGLE_COLUMN,
            HintExplanationKey.HIDDEN_SINGLE_BOX -> R.string.hint_technique_hidden_single
            HintExplanationKey.POINTING_ROW,
            HintExplanationKey.POINTING_COLUMN -> R.string.hint_technique_pointing
            HintExplanationKey.CLAIMING_ROW,
            HintExplanationKey.CLAIMING_COLUMN -> R.string.hint_technique_claiming
            HintExplanationKey.NAKED_PAIR -> R.string.hint_technique_naked_pair
            HintExplanationKey.HIDDEN_PAIR -> R.string.hint_technique_hidden_pair
            HintExplanationKey.NAKED_TRIPLE -> R.string.hint_technique_naked_triple
            HintExplanationKey.HIDDEN_TRIPLE -> R.string.hint_technique_hidden_triple
            HintExplanationKey.X_WING_ROW,
            HintExplanationKey.X_WING_COLUMN -> R.string.hint_technique_x_wing
            HintExplanationKey.XY_WING -> R.string.hint_technique_xy_wing
            HintExplanationKey.SKYSCRAPER_ROW,
            HintExplanationKey.SKYSCRAPER_COLUMN -> R.string.hint_technique_skyscraper
            HintExplanationKey.TWO_STRING_KITE -> R.string.hint_technique_two_string_kite
        }
    )

    private fun cell(cell: CellRef): String = context.getString(
        R.string.hint_cell,
        cell.row + 1,
        cell.column + 1
    )

    private fun cells(cells: Collection<CellRef>): String =
        cells.distinct().sorted().joinToString(context.getString(R.string.hint_list_separator)) {
            cell(it)
        }

    private fun house(house: HouseRef): String = context.getString(
        when (house.type) {
            HouseType.ROW -> R.string.hint_house_row
            HouseType.COLUMN -> R.string.hint_house_column
            HouseType.BOX -> R.string.hint_house_box
        },
        house.index + 1
    )

    private fun houses(houses: Collection<HouseRef>): String =
        houses.distinct().sorted().joinToString(context.getString(R.string.hint_list_separator)) {
            house(it)
        }

    private fun digits(digits: DigitSet): String =
        digits.digitsAscending().joinToString(context.getString(R.string.hint_digit_separator))
}
