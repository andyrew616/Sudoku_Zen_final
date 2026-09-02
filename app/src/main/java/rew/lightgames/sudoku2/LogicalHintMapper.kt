package rew.lightgames.sudoku2

/** Pure mapping from solver evidence to stable presentation metadata. */
class LogicalHintMapper {
    fun map(step: LogicalStep, detailLevel: HintDetailLevel): LogicalHint {
        validateEvidenceActions(step)
        val mapping = mappingFor(step)
        val targets = step.actions.map { it.cell }
        val houses = (mapping.args.primaryHouses + mapping.args.secondaryHouses).distinct()
        return LogicalHint(
            technique = step.technique,
            detailLevel = detailLevel,
            targetCells = targets,
            supportingCells = mapping.supportingCells,
            houses = houses,
            actions = step.actions,
            explanationKey = mapping.key,
            explanationArgs = mapping.args,
            highlights = highlightsFor(
                detailLevel = detailLevel,
                houses = houses,
                supportingCells = mapping.supportingCells,
                focusCell = mapping.args.focusCell,
                actions = step.actions
            )
        )
    }

    private fun mappingFor(step: LogicalStep): Mapping = when (val evidence = step.evidence) {
        is StepEvidence.Single -> mapSingle(step.technique, evidence)
        is StepEvidence.LockedCandidates -> mapLocked(step.technique, evidence)
        is StepEvidence.Subset -> mapSubset(step.technique, evidence)
        is StepEvidence.Fish -> mapFish(step.technique, evidence)
        is StepEvidence.XYWing -> mapXYWing(step.technique, evidence)
        is StepEvidence.Skyscraper -> mapSkyscraper(step.technique, evidence)
        is StepEvidence.TwoStringKite -> mapTwoStringKite(step.technique, evidence)
    }

    private fun mapSingle(
        technique: SudokuTechnique,
        evidence: StepEvidence.Single
    ): Mapping {
        val key = when (technique) {
            SudokuTechnique.NAKED_SINGLE -> {
                require(evidence.uniqueIn == null) { "Naked Single must not claim a unique house" }
                HintExplanationKey.NAKED_SINGLE
            }
            SudokuTechnique.HIDDEN_SINGLE -> when (requireNotNull(evidence.uniqueIn).type) {
                HouseType.ROW -> HintExplanationKey.HIDDEN_SINGLE_ROW
                HouseType.COLUMN -> HintExplanationKey.HIDDEN_SINGLE_COLUMN
                HouseType.BOX -> HintExplanationKey.HIDDEN_SINGLE_BOX
            }
            else -> error("Single evidence cannot describe $technique")
        }
        return Mapping(
            key = key,
            supportingCells = emptyList(),
            args = HintExplanationArgs(
                digit = evidence.digit,
                digits = evidence.candidates,
                focusCell = evidence.cell,
                primaryHouses = listOfNotNull(evidence.uniqueIn)
            )
        )
    }

    private fun mapLocked(
        technique: SudokuTechnique,
        evidence: StepEvidence.LockedCandidates
    ): Mapping {
        val key = when (technique) {
            SudokuTechnique.LOCKED_CANDIDATES_POINTING -> {
                require(evidence.sourceHouse.type == HouseType.BOX)
                when (evidence.targetHouse.type) {
                    HouseType.ROW -> HintExplanationKey.POINTING_ROW
                    HouseType.COLUMN -> HintExplanationKey.POINTING_COLUMN
                    HouseType.BOX -> error("Pointing target must be a row or column")
                }
            }
            SudokuTechnique.LOCKED_CANDIDATES_CLAIMING -> {
                require(evidence.targetHouse.type == HouseType.BOX)
                when (evidence.sourceHouse.type) {
                    HouseType.ROW -> HintExplanationKey.CLAIMING_ROW
                    HouseType.COLUMN -> HintExplanationKey.CLAIMING_COLUMN
                    HouseType.BOX -> error("Claiming source must be a row or column")
                }
            }
            else -> error("Locked Candidates evidence cannot describe $technique")
        }
        return Mapping(
            key = key,
            supportingCells = evidence.sourceCells,
            args = HintExplanationArgs(
                digit = evidence.digit,
                primaryCells = evidence.sourceCells,
                primaryHouses = listOf(evidence.sourceHouse),
                secondaryHouses = listOf(evidence.targetHouse)
            )
        )
    }

    private fun mapSubset(
        technique: SudokuTechnique,
        evidence: StepEvidence.Subset
    ): Mapping {
        val key = when (technique) {
            SudokuTechnique.NAKED_PAIR -> HintExplanationKey.NAKED_PAIR
            SudokuTechnique.HIDDEN_PAIR -> HintExplanationKey.HIDDEN_PAIR
            SudokuTechnique.NAKED_TRIPLE -> HintExplanationKey.NAKED_TRIPLE
            SudokuTechnique.HIDDEN_TRIPLE -> HintExplanationKey.HIDDEN_TRIPLE
            else -> error("Subset evidence cannot describe $technique")
        }
        val expectedHidden = technique == SudokuTechnique.HIDDEN_PAIR ||
            technique == SudokuTechnique.HIDDEN_TRIPLE
        require(evidence.hidden == expectedHidden) { "Subset visibility does not match $technique" }
        val expectedSize = if (
            technique == SudokuTechnique.NAKED_PAIR || technique == SudokuTechnique.HIDDEN_PAIR
        ) 2 else 3
        require(evidence.cells.size == expectedSize) { "Subset size does not match $technique" }
        return Mapping(
            key = key,
            supportingCells = evidence.cells,
            args = HintExplanationArgs(
                digits = evidence.digits,
                primaryCells = evidence.cells,
                primaryHouses = listOf(evidence.house)
            )
        )
    }

    private fun mapFish(technique: SudokuTechnique, evidence: StepEvidence.Fish): Mapping {
        require(technique == SudokuTechnique.X_WING) { "Fish evidence cannot describe $technique" }
        val orientation = evidence.baseHouses.first().type
        val key = when (orientation) {
            HouseType.ROW -> HintExplanationKey.X_WING_ROW
            HouseType.COLUMN -> HintExplanationKey.X_WING_COLUMN
            HouseType.BOX -> error("X-Wing bases cannot be boxes")
        }
        return Mapping(
            key = key,
            supportingCells = evidence.cells,
            args = HintExplanationArgs(
                digit = evidence.digit,
                orientation = orientation,
                primaryCells = evidence.cells,
                primaryHouses = evidence.baseHouses,
                secondaryHouses = evidence.coverHouses
            )
        )
    }

    private fun mapXYWing(technique: SudokuTechnique, evidence: StepEvidence.XYWing): Mapping {
        require(technique == SudokuTechnique.XY_WING) { "XY-Wing evidence cannot describe $technique" }
        val supports = listOf(evidence.pivot) + evidence.pincers
        return Mapping(
            key = HintExplanationKey.XY_WING,
            supportingCells = supports,
            args = HintExplanationArgs(
                digit = evidence.eliminationDigit,
                digits = evidence.digits,
                focusCell = evidence.pivot,
                primaryCells = evidence.pincers
            )
        )
    }

    private fun mapSkyscraper(
        technique: SudokuTechnique,
        evidence: StepEvidence.Skyscraper
    ): Mapping {
        require(technique == SudokuTechnique.SKYSCRAPER) {
            "Skyscraper evidence cannot describe $technique"
        }
        val key = when (evidence.orientation) {
            HouseType.ROW -> HintExplanationKey.SKYSCRAPER_ROW
            HouseType.COLUMN -> HintExplanationKey.SKYSCRAPER_COLUMN
            HouseType.BOX -> error("Skyscraper orientation cannot be a box")
        }
        return Mapping(
            key = key,
            supportingCells = evidence.alignedCells + evidence.towers,
            args = HintExplanationArgs(
                digit = evidence.digit,
                orientation = evidence.orientation,
                primaryCells = evidence.alignedCells,
                secondaryCells = evidence.towers,
                primaryHouses = evidence.sourceHouses
            )
        )
    }

    private fun mapTwoStringKite(
        technique: SudokuTechnique,
        evidence: StepEvidence.TwoStringKite
    ): Mapping {
        require(technique == SudokuTechnique.TWO_STRING_KITE) {
            "Two-String Kite evidence cannot describe $technique"
        }
        val connectors = listOf(evidence.rowConnector, evidence.columnConnector)
        val outers = listOf(evidence.rowOuter, evidence.columnOuter)
        return Mapping(
            key = HintExplanationKey.TWO_STRING_KITE,
            supportingCells = connectors + outers,
            args = HintExplanationArgs(
                digit = evidence.digit,
                primaryCells = connectors,
                secondaryCells = outers,
                primaryHouses = listOf(evidence.rowHouse, evidence.columnHouse)
            )
        )
    }

    private fun highlightsFor(
        detailLevel: HintDetailLevel,
        houses: Collection<HouseRef>,
        supportingCells: Collection<CellRef>,
        focusCell: CellRef?,
        actions: Collection<SolveAction>
    ): List<HintHighlight> {
        if (detailLevel == HintDetailLevel.TECHNIQUE) return emptyList()

        val highlights = ArrayList<HintHighlight>()
        houses.flatMap(::cellsInHouse).distinct().forEach { cell ->
            highlights.add(HintHighlight(cell, HintHighlightRole.HOUSE))
        }
        supportingCells.forEach { cell ->
            highlights.add(HintHighlight(cell, HintHighlightRole.SUPPORT))
        }
        if (focusCell != null) {
            highlights.add(HintHighlight(focusCell, HintHighlightRole.SUPPORT))
        }
        if (detailLevel == HintDetailLevel.ACTION) {
            actions.forEach { action ->
                when (action) {
                    is SolveAction.PlaceValue -> highlights.add(
                        HintHighlight(action.cell, HintHighlightRole.TARGET, action.digit)
                    )
                    is SolveAction.EliminateCandidates ->
                        action.digits.digitsAscending().forEach { digit ->
                            highlights.add(
                                HintHighlight(action.cell, HintHighlightRole.ELIMINATION, digit)
                            )
                        }
                }
            }
        }
        return highlights
    }

    private fun cellsInHouse(house: HouseRef): List<CellRef> = when (house.type) {
        HouseType.ROW -> (0..8).map { column -> CellRef(house.index, column) }
        HouseType.COLUMN -> (0..8).map { row -> CellRef(row, house.index) }
        HouseType.BOX -> {
            val startRow = house.index / 3 * 3
            val startColumn = house.index % 3 * 3
            (0..2).flatMap { rowOffset ->
                (0..2).map { columnOffset ->
                    CellRef(startRow + rowOffset, startColumn + columnOffset)
                }
            }
        }
    }

    private fun validateEvidenceActions(step: LogicalStep) {
        val exactEliminationDigit = when (val evidence = step.evidence) {
            is StepEvidence.LockedCandidates -> evidence.digit
            is StepEvidence.Fish -> evidence.digit
            is StepEvidence.XYWing -> evidence.eliminationDigit
            is StepEvidence.Skyscraper -> evidence.digit
            is StepEvidence.TwoStringKite -> evidence.digit
            is StepEvidence.Single -> null
            is StepEvidence.Subset -> {
                require(step.actions.all { it is SolveAction.EliminateCandidates }) {
                    "Subset hints must contain only elimination actions"
                }
                step.actions.forEach { action ->
                    action as SolveAction.EliminateCandidates
                    if (evidence.hidden) {
                        require(action.cell in evidence.cells) {
                            "Hidden-subset eliminations must target subset cells"
                        }
                        require(action.digits.mask and evidence.digits.mask == 0) {
                            "Hidden-subset eliminations must preserve the subset digits"
                        }
                    } else {
                        require(action.cell !in evidence.cells) {
                            "Naked-subset eliminations must target cells outside the subset"
                        }
                        require(action.digits.mask and evidence.digits.mask.inv() == 0) {
                            "Naked-subset eliminations must remove only subset digits"
                        }
                    }
                }
                null
            }
        }
        if (exactEliminationDigit != null) {
            require(step.actions.all { action ->
                action is SolveAction.EliminateCandidates &&
                    action.digits == DigitSet.of(exactEliminationDigit)
            }) { "${step.technique} actions must eliminate exactly its evidence digit" }
        }
    }

    private data class Mapping(
        val key: HintExplanationKey,
        val supportingCells: List<CellRef>,
        val args: HintExplanationArgs
    )
}
