package rew.lightgames.sudoku2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicalHintMapperTest {
    private val mapper = LogicalHintMapper()

    @Test
    fun everyTechnique_mapsToExpectedKeyTargetsSupportsAndHouses() {
        fixtures().forEach { fixture ->
            val hint = mapper.map(fixture.step, HintDetailLevel.ACTION)

            assertEquals(fixture.step.technique, hint.technique)
            assertEquals(fixture.key, hint.explanationKey)
            assertEquals(fixture.targets.sorted(), hint.targetCells)
            assertEquals(fixture.supports.sorted(), hint.supportingCells)
            assertEquals(fixture.houses.sorted(), hint.houses)
            assertEquals(fixture.step.actions, hint.actions)
        }
        assertEquals(SudokuTechnique.entries.toSet(), fixtures().map { it.step.technique }.toSet())
    }

    @Test
    fun orientationVariants_chooseBoundedExplanationKeys() {
        variantFixtures().forEach { fixture ->
            assertEquals(
                fixture.key,
                mapper.map(fixture.step, HintDetailLevel.EVIDENCE).explanationKey
            )
        }
    }

    @Test
    fun actionHighlights_exactlyMirrorActionTargetsAndDigits() {
        (fixtures() + variantFixtures()).forEach { fixture ->
            val hint = mapper.map(fixture.step, HintDetailLevel.ACTION)
            val placeHighlights = hint.highlights.filter { it.role == HintHighlightRole.TARGET }
            val eliminationHighlights = hint.highlights.filter {
                it.role == HintHighlightRole.ELIMINATION
            }
            val expectedPlaces = fixture.step.actions.filterIsInstance<SolveAction.PlaceValue>()
                .map { HintHighlight(it.cell, HintHighlightRole.TARGET, it.digit) }
                .sortedWith(highlightComparator)
            val expectedEliminations = fixture.step.actions
                .filterIsInstance<SolveAction.EliminateCandidates>()
                .flatMap { action ->
                    action.digits.digitsAscending().map { digit ->
                        HintHighlight(action.cell, HintHighlightRole.ELIMINATION, digit)
                    }
                }
                .sortedWith(highlightComparator)

            assertEquals(expectedPlaces, placeHighlights.sortedWith(highlightComparator))
            assertEquals(
                expectedEliminations,
                eliminationHighlights.sortedWith(highlightComparator)
            )
            assertTrue(
                hint.supportingCells.none { support ->
                    support !in hint.targetCells && hint.highlights.any {
                        it.cell == support &&
                            it.role in listOf(
                                HintHighlightRole.TARGET,
                                HintHighlightRole.ELIMINATION
                            )
                    }
                }
            )
        }
    }

    @Test
    fun detailLevels_revealNoHighlightsThenEvidenceThenExactAction() {
        val fixture = fixtures().first { it.step.technique == SudokuTechnique.XY_WING }
        val technique = mapper.map(fixture.step, HintDetailLevel.TECHNIQUE)
        val evidence = mapper.map(fixture.step, HintDetailLevel.EVIDENCE)
        val action = mapper.map(fixture.step, HintDetailLevel.ACTION)

        assertTrue(technique.highlights.isEmpty())
        assertTrue(evidence.highlights.any { it.role == HintHighlightRole.SUPPORT })
        assertFalse(
            evidence.highlights.any {
                it.role == HintHighlightRole.TARGET ||
                    it.role == HintHighlightRole.ELIMINATION
            }
        )
        assertTrue(action.highlights.any { it.role == HintHighlightRole.ELIMINATION })
        assertEquals(technique.actions, evidence.actions)
        assertEquals(evidence.actions, action.actions)
    }

    @Test
    fun mapping_isDeterministicAndCollectionsAreCanonicalAndImmutable() {
        fixtures().forEach { fixture ->
            val first = mapper.map(fixture.step, HintDetailLevel.ACTION)
            val repeated = mapper.map(fixture.step, HintDetailLevel.ACTION)
            assertEquals(first, repeated)
            assertEquals(first.hashCode(), repeated.hashCode())
            assertEquals(first.targetCells.sorted(), first.targetCells)
            assertEquals(first.supportingCells.distinct().sorted(), first.supportingCells)
            assertEquals(first.houses.distinct().sorted(), first.houses)
        }

        val hint = mapper.map(fixtures().last().step, HintDetailLevel.ACTION)
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (hint.targetCells as MutableList<CellRef>).add(CellRef(8, 8))
        }
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (hint.highlights as MutableList<HintHighlight>).clear()
        }
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (hint.actions as MutableList<SolveAction>).clear()
        }
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (hint.explanationArgs.primaryCells as MutableList<CellRef>).clear()
        }
    }

    @Test
    fun explanationArgs_retainTechniqueSpecificEvidenceRoles() {
        val byTechnique = fixtures().associate { fixture ->
            fixture.step.technique to mapper.map(fixture.step, HintDetailLevel.ACTION)
        }

        assertEquals(CellRef(0, 0), byTechnique.getValue(SudokuTechnique.XY_WING)
            .explanationArgs.focusCell)
        assertEquals(
            listOf(CellRef(0, 4), CellRef(4, 0)),
            byTechnique.getValue(SudokuTechnique.XY_WING).explanationArgs.primaryCells
        )
        assertEquals(
            listOf(CellRef(0, 0), CellRef(1, 0)),
            byTechnique.getValue(SudokuTechnique.SKYSCRAPER).explanationArgs.primaryCells
        )
        assertEquals(
            listOf(CellRef(0, 4), CellRef(1, 5)),
            byTechnique.getValue(SudokuTechnique.SKYSCRAPER).explanationArgs.secondaryCells
        )
        assertEquals(
            listOf(CellRef(0, 0), CellRef(1, 1)),
            byTechnique.getValue(SudokuTechnique.TWO_STRING_KITE).explanationArgs.primaryCells
        )
        assertEquals(
            listOf(CellRef(0, 5), CellRef(5, 1)),
            byTechnique.getValue(SudokuTechnique.TWO_STRING_KITE).explanationArgs.secondaryCells
        )
    }

    @Test
    fun malformedSubsetActions_areRejectedBeforePresentation() {
        val house = HouseRef(HouseType.ROW, 0)
        val subsetCells = listOf(CellRef(0, 0), CellRef(0, 1))
        val subsetDigits = DigitSet.of(1, 2)

        val wrongNakedDigit = LogicalStep(
            SudokuTechnique.NAKED_PAIR,
            listOf(SolveAction.EliminateCandidates(CellRef(0, 4), DigitSet.of(6))),
            StepEvidence.Subset(house, subsetDigits, subsetCells, hidden = false)
        )
        val wrongHiddenTarget = LogicalStep(
            SudokuTechnique.HIDDEN_PAIR,
            listOf(SolveAction.EliminateCandidates(CellRef(0, 4), DigitSet.of(6))),
            StepEvidence.Subset(house, subsetDigits, subsetCells, hidden = true)
        )
        val removedHiddenDigit = LogicalStep(
            SudokuTechnique.HIDDEN_PAIR,
            listOf(SolveAction.EliminateCandidates(CellRef(0, 0), DigitSet.of(1))),
            StepEvidence.Subset(house, subsetDigits, subsetCells, hidden = true)
        )

        listOf(wrongNakedDigit, wrongHiddenTarget, removedHiddenDigit).forEach { step ->
            assertThrows(IllegalArgumentException::class.java) {
                mapper.map(step, HintDetailLevel.ACTION)
            }
        }
    }

    private fun fixtures(): List<Fixture> = listOf(
        singleFixture(SudokuTechnique.NAKED_SINGLE, null, HintExplanationKey.NAKED_SINGLE),
        singleFixture(
            SudokuTechnique.HIDDEN_SINGLE,
            HouseRef(HouseType.ROW, 0),
            HintExplanationKey.HIDDEN_SINGLE_ROW
        ),
        lockedFixture(
            SudokuTechnique.LOCKED_CANDIDATES_POINTING,
            source = HouseRef(HouseType.BOX, 0),
            target = HouseRef(HouseType.ROW, 0),
            supports = listOf(CellRef(0, 0), CellRef(0, 1)),
            actionTarget = CellRef(0, 5),
            key = HintExplanationKey.POINTING_ROW
        ),
        lockedFixture(
            SudokuTechnique.LOCKED_CANDIDATES_CLAIMING,
            source = HouseRef(HouseType.ROW, 0),
            target = HouseRef(HouseType.BOX, 0),
            supports = listOf(CellRef(0, 0), CellRef(0, 1)),
            actionTarget = CellRef(1, 2),
            key = HintExplanationKey.CLAIMING_ROW
        ),
        subsetFixture(SudokuTechnique.NAKED_PAIR, hidden = false, size = 2),
        subsetFixture(SudokuTechnique.HIDDEN_PAIR, hidden = true, size = 2),
        subsetFixture(SudokuTechnique.NAKED_TRIPLE, hidden = false, size = 3),
        subsetFixture(SudokuTechnique.HIDDEN_TRIPLE, hidden = true, size = 3),
        fishFixture(HouseType.ROW),
        xyWingFixture(),
        skyscraperFixture(HouseType.ROW),
        twoStringKiteFixture()
    )

    private fun variantFixtures(): List<Fixture> = listOf(
        singleFixture(
            SudokuTechnique.HIDDEN_SINGLE,
            HouseRef(HouseType.COLUMN, 0),
            HintExplanationKey.HIDDEN_SINGLE_COLUMN
        ),
        singleFixture(
            SudokuTechnique.HIDDEN_SINGLE,
            HouseRef(HouseType.BOX, 0),
            HintExplanationKey.HIDDEN_SINGLE_BOX
        ),
        lockedFixture(
            SudokuTechnique.LOCKED_CANDIDATES_POINTING,
            source = HouseRef(HouseType.BOX, 0),
            target = HouseRef(HouseType.COLUMN, 0),
            supports = listOf(CellRef(0, 0), CellRef(1, 0)),
            actionTarget = CellRef(5, 0),
            key = HintExplanationKey.POINTING_COLUMN
        ),
        lockedFixture(
            SudokuTechnique.LOCKED_CANDIDATES_CLAIMING,
            source = HouseRef(HouseType.COLUMN, 0),
            target = HouseRef(HouseType.BOX, 0),
            supports = listOf(CellRef(0, 0), CellRef(1, 0)),
            actionTarget = CellRef(2, 1),
            key = HintExplanationKey.CLAIMING_COLUMN
        ),
        fishFixture(HouseType.COLUMN),
        skyscraperFixture(HouseType.COLUMN)
    )

    private fun singleFixture(
        technique: SudokuTechnique,
        house: HouseRef?,
        key: HintExplanationKey
    ): Fixture {
        val cell = CellRef(0, 0)
        val step = LogicalStep(
            technique,
            listOf(SolveAction.PlaceValue(cell, 5)),
            StepEvidence.Single(cell, 5, DigitSet.of(5), house)
        )
        return Fixture(step, key, listOf(cell), emptyList(), listOfNotNull(house))
    }

    private fun lockedFixture(
        technique: SudokuTechnique,
        source: HouseRef,
        target: HouseRef,
        supports: List<CellRef>,
        actionTarget: CellRef,
        key: HintExplanationKey
    ): Fixture {
        val step = LogicalStep(
            technique,
            listOf(SolveAction.EliminateCandidates(actionTarget, DigitSet.of(4))),
            StepEvidence.LockedCandidates(4, source, target, supports)
        )
        return Fixture(step, key, listOf(actionTarget), supports, listOf(source, target))
    }

    private fun subsetFixture(
        technique: SudokuTechnique,
        hidden: Boolean,
        size: Int
    ): Fixture {
        val house = HouseRef(HouseType.ROW, 0)
        val supports = (0 until size).map { CellRef(0, it) }
        val digits = if (size == 2) DigitSet.of(1, 2) else DigitSet.of(1, 2, 3)
        val target = if (hidden) supports.first() else CellRef(0, 4)
        val eliminatedDigits = if (hidden) DigitSet.of(6) else DigitSet.of(1)
        val step = LogicalStep(
            technique,
            listOf(SolveAction.EliminateCandidates(target, eliminatedDigits)),
            StepEvidence.Subset(house, digits, supports, hidden)
        )
        val key = when (technique) {
            SudokuTechnique.NAKED_PAIR -> HintExplanationKey.NAKED_PAIR
            SudokuTechnique.HIDDEN_PAIR -> HintExplanationKey.HIDDEN_PAIR
            SudokuTechnique.NAKED_TRIPLE -> HintExplanationKey.NAKED_TRIPLE
            SudokuTechnique.HIDDEN_TRIPLE -> HintExplanationKey.HIDDEN_TRIPLE
            else -> error("Not a subset")
        }
        return Fixture(step, key, listOf(target), supports, listOf(house))
    }

    private fun fishFixture(orientation: HouseType): Fixture {
        val rowBased = orientation == HouseType.ROW
        val base = (0..1).map { HouseRef(orientation, it) }
        val coverType = if (rowBased) HouseType.COLUMN else HouseType.ROW
        val cover = (0..1).map { HouseRef(coverType, it) }
        val supports = if (rowBased) {
            listOf(CellRef(0, 0), CellRef(0, 1), CellRef(1, 0), CellRef(1, 1))
        } else {
            listOf(CellRef(0, 0), CellRef(1, 0), CellRef(0, 1), CellRef(1, 1))
        }
        val target = if (rowBased) CellRef(2, 0) else CellRef(0, 2)
        val step = LogicalStep(
            SudokuTechnique.X_WING,
            listOf(SolveAction.EliminateCandidates(target, DigitSet.of(4))),
            StepEvidence.Fish(4, base, cover, supports)
        )
        val key = if (rowBased) HintExplanationKey.X_WING_ROW
        else HintExplanationKey.X_WING_COLUMN
        return Fixture(step, key, listOf(target), supports, base + cover)
    }

    private fun xyWingFixture(): Fixture {
        val pivot = CellRef(0, 0)
        val pincers = listOf(CellRef(0, 4), CellRef(4, 0))
        val target = CellRef(4, 4)
        val step = LogicalStep(
            SudokuTechnique.XY_WING,
            listOf(SolveAction.EliminateCandidates(target, DigitSet.of(3))),
            StepEvidence.XYWing(pivot, pincers, DigitSet.of(1, 2), 3)
        )
        return Fixture(
            step,
            HintExplanationKey.XY_WING,
            listOf(target),
            listOf(pivot) + pincers,
            emptyList()
        )
    }

    private fun skyscraperFixture(orientation: HouseType): Fixture {
        val rowBased = orientation == HouseType.ROW
        val sources = (0..1).map { HouseRef(orientation, it) }
        val aligned = if (rowBased) {
            listOf(CellRef(0, 0), CellRef(1, 0))
        } else {
            listOf(CellRef(0, 0), CellRef(0, 1))
        }
        val towers = if (rowBased) {
            listOf(CellRef(0, 4), CellRef(1, 5))
        } else {
            listOf(CellRef(4, 0), CellRef(5, 1))
        }
        val target = if (rowBased) CellRef(2, 4) else CellRef(4, 2)
        val step = LogicalStep(
            SudokuTechnique.SKYSCRAPER,
            listOf(SolveAction.EliminateCandidates(target, DigitSet.of(6))),
            StepEvidence.Skyscraper(6, orientation, sources, aligned, towers)
        )
        val key = if (rowBased) HintExplanationKey.SKYSCRAPER_ROW
        else HintExplanationKey.SKYSCRAPER_COLUMN
        return Fixture(step, key, listOf(target), aligned + towers, sources)
    }

    private fun twoStringKiteFixture(): Fixture {
        val connectors = listOf(CellRef(0, 0), CellRef(1, 1))
        val outers = listOf(CellRef(0, 5), CellRef(5, 1))
        val target = CellRef(5, 5)
        val step = LogicalStep(
            SudokuTechnique.TWO_STRING_KITE,
            listOf(SolveAction.EliminateCandidates(target, DigitSet.of(7))),
            StepEvidence.TwoStringKite(
                digit = 7,
                rowHouse = HouseRef(HouseType.ROW, 0),
                columnHouse = HouseRef(HouseType.COLUMN, 1),
                rowConnector = connectors[0],
                columnConnector = connectors[1],
                rowOuter = outers[0],
                columnOuter = outers[1]
            )
        )
        return Fixture(
            step,
            HintExplanationKey.TWO_STRING_KITE,
            listOf(target),
            connectors + outers,
            listOf(HouseRef(HouseType.ROW, 0), HouseRef(HouseType.COLUMN, 1))
        )
    }

    private data class Fixture(
        val step: LogicalStep,
        val key: HintExplanationKey,
        val targets: List<CellRef>,
        val supports: List<CellRef>,
        val houses: List<HouseRef>
    )

    private val highlightComparator = compareBy<HintHighlight>(
        { it.cell },
        { it.role.ordinal },
        { it.digit ?: 0 }
    )
}
