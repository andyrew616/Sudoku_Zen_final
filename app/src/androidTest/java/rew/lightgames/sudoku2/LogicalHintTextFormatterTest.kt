package rew.lightgames.sudoku2

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LogicalHintTextFormatterTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val formatter = LogicalHintTextFormatter(context)

    @Test
    fun originalFixturesForEveryTechnique_renderAllThreeLevelsWithResolvedArguments() {
        fixtures().forEach { fixture ->
            HintDetailLevel.entries.forEach { level ->
                val hint = fixture(level)
                val text = formatter.format(hint)
                assertTrue("${hint.technique} $level text must not be blank", text.isNotBlank())
                assertFalse("${hint.technique} $level left a format token", text.contains("%1"))
                assertFalse("${hint.technique} $level left a format token", text.contains("%2"))
                if (level == HintDetailLevel.ACTION) {
                    assertTrue("${hint.technique} action must name a cell", text.contains("row"))
                    hint.actions.forEach { action ->
                        val actionDigits = when (action) {
                            is SolveAction.PlaceValue -> listOf(action.digit)
                            is SolveAction.EliminateCandidates -> action.digits.digitsAscending()
                        }
                        actionDigits.forEach { digit ->
                            assertTrue(
                                "${hint.technique} action must name digit $digit",
                                text.contains(digit.toString())
                            )
                        }
                    }
                }
            }
        }
        assertTrue(fixtures().size == SudokuTechnique.entries.size)
    }

    private fun fixtures(): List<(HintDetailLevel) -> LogicalHint> = listOf(
        fixture(
            SudokuTechnique.NAKED_SINGLE,
            HintExplanationKey.NAKED_SINGLE,
            HintExplanationArgs(digit = 5, focusCell = CellRef(0, 0)),
            SolveAction.PlaceValue(CellRef(0, 0), 5)
        ),
        fixture(
            SudokuTechnique.HIDDEN_SINGLE,
            HintExplanationKey.HIDDEN_SINGLE_ROW,
            HintExplanationArgs(
                digit = 5,
                focusCell = CellRef(0, 0),
                primaryHouses = listOf(HouseRef(HouseType.ROW, 0))
            )
        ),
        lockedFixture(
            SudokuTechnique.LOCKED_CANDIDATES_POINTING,
            HintExplanationKey.POINTING_ROW,
            HouseRef(HouseType.BOX, 0),
            HouseRef(HouseType.ROW, 0)
        ),
        lockedFixture(
            SudokuTechnique.LOCKED_CANDIDATES_CLAIMING,
            HintExplanationKey.CLAIMING_ROW,
            HouseRef(HouseType.ROW, 0),
            HouseRef(HouseType.BOX, 0)
        ),
        subsetFixture(SudokuTechnique.NAKED_PAIR, HintExplanationKey.NAKED_PAIR, 2),
        subsetFixture(SudokuTechnique.HIDDEN_PAIR, HintExplanationKey.HIDDEN_PAIR, 2),
        subsetFixture(SudokuTechnique.NAKED_TRIPLE, HintExplanationKey.NAKED_TRIPLE, 3),
        subsetFixture(SudokuTechnique.HIDDEN_TRIPLE, HintExplanationKey.HIDDEN_TRIPLE, 3),
        fixture(
            SudokuTechnique.X_WING,
            HintExplanationKey.X_WING_ROW,
            HintExplanationArgs(
                digit = 5,
                primaryCells = listOf(
                    CellRef(0, 0), CellRef(0, 1), CellRef(1, 0), CellRef(1, 1)
                ),
                primaryHouses = listOf(HouseRef(HouseType.ROW, 0), HouseRef(HouseType.ROW, 1)),
                secondaryHouses = listOf(
                    HouseRef(HouseType.COLUMN, 0), HouseRef(HouseType.COLUMN, 1)
                )
            )
        ),
        fixture(
            SudokuTechnique.XY_WING,
            HintExplanationKey.XY_WING,
            HintExplanationArgs(
                digit = 5,
                focusCell = CellRef(0, 0),
                primaryCells = listOf(CellRef(0, 4), CellRef(4, 0))
            )
        ),
        fixture(
            SudokuTechnique.SKYSCRAPER,
            HintExplanationKey.SKYSCRAPER_ROW,
            HintExplanationArgs(
                digit = 5,
                primaryCells = listOf(CellRef(0, 0), CellRef(1, 0)),
                secondaryCells = listOf(CellRef(0, 4), CellRef(1, 5)),
                primaryHouses = listOf(HouseRef(HouseType.ROW, 0), HouseRef(HouseType.ROW, 1))
            )
        ),
        fixture(
            SudokuTechnique.TWO_STRING_KITE,
            HintExplanationKey.TWO_STRING_KITE,
            HintExplanationArgs(
                digit = 5,
                primaryCells = listOf(CellRef(0, 0), CellRef(1, 1)),
                secondaryCells = listOf(CellRef(0, 5), CellRef(5, 1)),
                primaryHouses = listOf(
                    HouseRef(HouseType.ROW, 0), HouseRef(HouseType.COLUMN, 1)
                )
            )
        )
    )

    private fun lockedFixture(
        technique: SudokuTechnique,
        key: HintExplanationKey,
        source: HouseRef,
        target: HouseRef
    ) = fixture(
        technique,
        key,
        HintExplanationArgs(
            digit = 5,
            primaryCells = listOf(CellRef(0, 0), CellRef(0, 1)),
            primaryHouses = listOf(source),
            secondaryHouses = listOf(target)
        )
    )

    private fun subsetFixture(
        technique: SudokuTechnique,
        key: HintExplanationKey,
        size: Int
    ) = fixture(
        technique,
        key,
        HintExplanationArgs(
            digits = if (size == 2) DigitSet.of(1, 2) else DigitSet.of(1, 2, 3),
            primaryCells = (0 until size).map { CellRef(0, it) },
            primaryHouses = listOf(HouseRef(HouseType.ROW, 0))
        ),
        action = if (
            technique == SudokuTechnique.NAKED_PAIR ||
            technique == SudokuTechnique.NAKED_TRIPLE
        ) {
            SolveAction.EliminateCandidates(CellRef(0, 8), DigitSet.of(1))
        } else {
            SolveAction.EliminateCandidates(CellRef(0, 0), DigitSet.of(5))
        }
    )

    private fun fixture(
        technique: SudokuTechnique,
        key: HintExplanationKey,
        args: HintExplanationArgs,
        action: SolveAction = SolveAction.EliminateCandidates(CellRef(8, 8), DigitSet.of(5))
    ): (HintDetailLevel) -> LogicalHint = { level ->
        LogicalHint(
            technique = technique,
            detailLevel = level,
            targetCells = listOf(action.cell),
            supportingCells = (args.primaryCells + args.secondaryCells).distinct(),
            houses = (args.primaryHouses + args.secondaryHouses).distinct(),
            actions = listOf(action),
            explanationKey = key,
            explanationArgs = args,
            highlights = emptyList()
        )
    }
}
