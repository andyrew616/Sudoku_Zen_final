package rew.lightgames.sudoku2

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SudokuLogicalSolverCohortTest {
    private val solver = SudokuLogicalSolver()

    @Test
    fun generatedCohort_10000Seeds_preservesOracleDeterminismAndMeasuresCoverage() {
        val cohortSize = 10_000
        val cohortStarted = System.nanoTime()
        val techniqueCounts = linkedMapOf<SudokuTechnique, Int>()
        val baselineTechniqueCounts = linkedMapOf<SudokuTechnique, Int>()
        val solveTimesNanos = ArrayList<Long>()
        var solved = 0
        var stalled = 0
        var invalid = 0
        var totalSteps = 0
        var incorrectPlacements = 0
        var incorrectEliminations = 0
        var nondeterministicTraces = 0
        var baselineSolved = 0
        var baselineStalled = 0
        var baselineInvalid = 0
        var baselineXWingPuzzles = 0
        var baselineXYWingPuzzles = 0
        var baselineSkyscraperPuzzles = 0
        var baselineSkyscraperSteps = 0
        var baselineTwoStringKitePuzzles = 0
        var baselineTwoStringKiteSteps = 0
        var baselineHardPuzzles = 0
        var xWingPuzzles = 0
        var xyWingPuzzles = 0
        var hardTechniquePuzzles = 0
        var hardStepsInHardPuzzles = 0
        var solvedWithHardTechnique = 0
        var hardStepsInSolvedWithHardPuzzles = 0
        var skyscraperPuzzles = 0
        var skyscraperSteps = 0
        var solvedWithSkyscraper = 0
        var skyscraperStepsInSolvedPuzzles = 0
        var twoStringKitePuzzles = 0
        var twoStringKiteSteps = 0
        var solvedWithTwoStringKite = 0
        var twoStringKiteStepsInSolvedPuzzles = 0
        val stalledDiagnostics = ArrayList<StalledDiagnostic>()

        for (seed in 1L..cohortSize.toLong()) {
            val generated = SudokuPuzzleEngine(seed).generate()
            val oracle = SudokuPuzzleEngine().solve(generated.puzzle)
            assertNotNull("Seed $seed must have an oracle solution", oracle)

            val started = System.nanoTime()
            val result = solver.solve(generated.puzzle)
            solveTimesNanos.add(System.nanoTime() - started)
            val repeated = solver.solve(generated.puzzle.clone())
            if (result != repeated) nondeterministicTraces++

            when (result.status) {
                LogicalSolveStatus.SOLVED -> {
                    solved++
                    assertArrayEquals("Seed $seed final solution mismatch", oracle, result.finalBoard)
                }
                LogicalSolveStatus.STALLED -> stalled++
                LogicalSolveStatus.INVALID -> invalid++
            }

            totalSteps += result.steps.size
            result.steps.forEach { step ->
                techniqueCounts[step.technique] = techniqueCounts.getOrDefault(step.technique, 0) + 1
                if (seed <= 500L) {
                    baselineTechniqueCounts[step.technique] =
                        baselineTechniqueCounts.getOrDefault(step.technique, 0) + 1
                }
            }
            val techniquesUsed = result.steps.map { it.technique }.toSet()
            if (SudokuTechnique.X_WING in techniquesUsed) xWingPuzzles++
            if (SudokuTechnique.XY_WING in techniquesUsed) xyWingPuzzles++
            val puzzleSkyscraperSteps = result.steps.count {
                it.technique == SudokuTechnique.SKYSCRAPER
            }
            if (puzzleSkyscraperSteps > 0) {
                skyscraperPuzzles++
                skyscraperSteps += puzzleSkyscraperSteps
                if (result.status == LogicalSolveStatus.SOLVED) {
                    solvedWithSkyscraper++
                    skyscraperStepsInSolvedPuzzles += puzzleSkyscraperSteps
                }
            }
            val puzzleTwoStringKiteSteps = result.steps.count {
                it.technique == SudokuTechnique.TWO_STRING_KITE
            }
            if (puzzleTwoStringKiteSteps > 0) {
                twoStringKitePuzzles++
                twoStringKiteSteps += puzzleTwoStringKiteSteps
                if (result.status == LogicalSolveStatus.SOLVED) {
                    solvedWithTwoStringKite++
                    twoStringKiteStepsInSolvedPuzzles += puzzleTwoStringKiteSteps
                }
            }
            val hardSteps = result.steps.count {
                it.technique in setOf(
                    SudokuTechnique.NAKED_TRIPLE,
                    SudokuTechnique.HIDDEN_TRIPLE,
                    SudokuTechnique.X_WING,
                    SudokuTechnique.XY_WING,
                    SudokuTechnique.SKYSCRAPER,
                    SudokuTechnique.TWO_STRING_KITE
                )
            }
            if (hardSteps > 0) {
                hardTechniquePuzzles++
                hardStepsInHardPuzzles += hardSteps
                if (result.status == LogicalSolveStatus.SOLVED) {
                    solvedWithHardTechnique++
                    hardStepsInSolvedWithHardPuzzles += hardSteps
                }
            }
            if (seed <= 500L) {
                if (SudokuTechnique.X_WING in techniquesUsed) baselineXWingPuzzles++
                if (SudokuTechnique.XY_WING in techniquesUsed) baselineXYWingPuzzles++
                if (SudokuTechnique.SKYSCRAPER in techniquesUsed) baselineSkyscraperPuzzles++
                baselineSkyscraperSteps += puzzleSkyscraperSteps
                if (SudokuTechnique.TWO_STRING_KITE in techniquesUsed) {
                    baselineTwoStringKitePuzzles++
                }
                baselineTwoStringKiteSteps += puzzleTwoStringKiteSteps
                if (hardSteps > 0) baselineHardPuzzles++
                when (result.status) {
                    LogicalSolveStatus.SOLVED -> baselineSolved++
                    LogicalSolveStatus.STALLED -> baselineStalled++
                    LogicalSolveStatus.INVALID -> baselineInvalid++
                }
            }

            val grid = (CandidateGrid.create(generated.puzzle) as CandidateGridCreationResult.Success).grid
            for (step in result.steps) {
                val progressBefore = solver.progress(grid)
                validateEvidence(grid, step)
                for (action in step.actions) {
                    when (action) {
                        is SolveAction.PlaceValue -> {
                            if (action.digit != oracle!![action.cell.index]) incorrectPlacements++
                        }
                        is SolveAction.EliminateCandidates -> {
                            if (oracle!![action.cell.index] in action.digits) incorrectEliminations++
                        }
                    }
                }
                assertEquals(
                    "Seed $seed failed to apply ${step.technique}",
                    CandidateGridMutationResult.Success,
                    grid.applyActions(step.actions)
                )
                assertTrue("Seed $seed step did not reduce progress", solver.progress(grid) < progressBefore)
            }
            assertArrayEquals(result.finalBoard, grid.snapshot().values)
            assertEquals(result.remainingCandidates, grid.snapshot())
            if (result.status == LogicalSolveStatus.STALLED && stalledDiagnostics.size < 300) {
                stalledDiagnostics.add(classifyStall(seed, grid))
            }
        }
        val cohortRuntimeMillis = (System.nanoTime() - cohortStarted) / 1_000_000.0

        val sortedMillis = solveTimesNanos.map { it / 1_000_000.0 }.sorted()
        val averageMillis = sortedMillis.average()
        val medianMillis = sortedMillis[sortedMillis.size / 2]
        val p95Millis = sortedMillis[
            (sortedMillis.size * 0.95).toInt().coerceAtMost(cohortSize - 1)
        ]
        val slowestMillis = sortedMillis.last()
        val averageSteps = totalSteps / cohortSize.toDouble()
        val frequency = SudokuTechnique.entries.joinToString { technique ->
            "$technique=${techniqueCounts.getOrDefault(technique, 0)}"
        }
        val baselineFrequency = SudokuTechnique.entries.joinToString { technique ->
            "$technique=${baselineTechniqueCounts.getOrDefault(technique, 0)}"
        }
        val additionalBaselineSolved = baselineSolved - 329
        val averageHardSteps = if (hardTechniquePuzzles == 0) 0.0
        else hardStepsInHardPuzzles / hardTechniquePuzzles.toDouble()
        val averageHardStepsSolved = if (solvedWithHardTechnique == 0) 0.0
        else hardStepsInSolvedWithHardPuzzles / solvedWithHardTechnique.toDouble()
        val averageSkyscraperStepsSolved = if (solvedWithSkyscraper == 0) 0.0
        else skyscraperStepsInSolvedPuzzles / solvedWithSkyscraper.toDouble()
        val averageTwoStringKiteStepsSolved = if (solvedWithTwoStringKite == 0) 0.0
        else twoStringKiteStepsInSolvedPuzzles / solvedWithTwoStringKite.toDouble()
        val solvedWithoutHardTechnique = solved - solvedWithHardTechnique
        val classifications = stalledDiagnostics.groupingBy { it.classification }.eachCount()
        val swordfishIncidence = stalledDiagnostics.count { it.swordfishSignature }
        val xyzWingIncidence = stalledDiagnostics.count { it.xyzWingSignature }

        println(
            "PR6 BASELINE 500: solved=$baselineSolved stalled=$baselineStalled " +
                "invalid=$baselineInvalid additionalSolvedVsPR5=$additionalBaselineSolved"
        )
        println("PR6 BASELINE TECHNIQUE FREQUENCY: $baselineFrequency")
        println(
            "PR6 BASELINE HARD FREQUENCY: xWingPuzzles=$baselineXWingPuzzles " +
                "xyWingPuzzles=$baselineXYWingPuzzles skyscraperPuzzles=$baselineSkyscraperPuzzles " +
                "skyscraperSteps=$baselineSkyscraperSteps " +
                "twoStringKitePuzzles=$baselineTwoStringKitePuzzles " +
                "twoStringKiteSteps=$baselineTwoStringKiteSteps " +
                "hardTechniquePuzzles=$baselineHardPuzzles"
        )
        println(
            "PR6 COHORT REPORT: solved=$solved stalled=$stalled invalid=$invalid " +
                "totalSteps=$totalSteps incorrectPlacements=$incorrectPlacements " +
                "incorrectEliminations=$incorrectEliminations nondeterministic=$nondeterministicTraces"
        )
        println("PR6 TECHNIQUE FREQUENCY: $frequency")
        println(
            "PR6 HARD COVERAGE: xWingPuzzles=$xWingPuzzles xyWingPuzzles=$xyWingPuzzles " +
                "hardTechniquePuzzles=$hardTechniquePuzzles solvedWithHard=$solvedWithHardTechnique " +
                "solvedWithoutHard=$solvedWithoutHardTechnique " +
                "averageHardStepsUsing=${"%.3f".format(averageHardSteps)} " +
                "averageHardStepsSolved=${"%.3f".format(averageHardStepsSolved)}"
        )
        println(
            "PR6 SKYSCRAPER COVERAGE: puzzles=$skyscraperPuzzles steps=$skyscraperSteps " +
                "solvedWithSkyscraper=$solvedWithSkyscraper " +
                "averageStepsSolved=${"%.3f".format(averageSkyscraperStepsSolved)}"
        )
        println(
            "PR6 TWO-STRING KITE COVERAGE: puzzles=$twoStringKitePuzzles " +
                "steps=$twoStringKiteSteps solvedWithTwoStringKite=$solvedWithTwoStringKite " +
                "averageStepsSolved=${"%.3f".format(averageTwoStringKiteStepsSolved)}"
        )
        println(
            "PR6 PERFORMANCE: averageMs=${"%.3f".format(averageMillis)} " +
                "medianMs=${"%.3f".format(medianMillis)} p95Ms=${"%.3f".format(p95Millis)} " +
                "slowestMs=${"%.3f".format(slowestMillis)} averageSteps=${"%.2f".format(averageSteps)} " +
                "cohortRuntimeMs=${"%.3f".format(cohortRuntimeMillis)}"
        )
        println("PR6 STALLED SAMPLE: size=${stalledDiagnostics.size} classifications=$classifications")
        println(
            "PR6 STALLED SIGNATURE INCIDENCE: swordfish=$swordfishIncidence " +
                "xyzWing=$xyzWingIncidence"
        )
        println(
            "PR6 STALLED UNCLASSIFIED FAMILIES: simpleColoring=notClassified " +
                "otherSingleDigitChain=notClassified otherWing=notClassified"
        )
        stalledDiagnostics.forEach { println("PR6 STALL: $it") }

        assertEquals(cohortSize, solved + stalled)
        assertEquals(0, invalid)
        assertEquals(0, incorrectPlacements)
        assertEquals(0, incorrectEliminations)
        assertEquals(0, nondeterministicTraces)
        assertEquals(500, baselineSolved + baselineStalled)
        assertEquals(0, baselineInvalid)
        assertTrue("PR6 must not regress PR5 solved coverage", baselineSolved >= 329)
        assertEquals(300, stalledDiagnostics.size)
        assertTrue("Median exceeded 5 ms budget", medianMillis <= 5.0)
        assertTrue("P95 exceeded 20 ms budget", p95Millis <= 20.0)
    }

    private fun validateEvidence(grid: CandidateGrid, step: LogicalStep) {
        assertFalse(step.actions.isEmpty())
        step.actions.forEach { action ->
            assertEquals(0, grid.valueAt(action.cell))
            when (action) {
                is SolveAction.PlaceValue -> assertTrue(action.digit in grid.candidatesAt(action.cell))
                is SolveAction.EliminateCandidates -> action.digits.digitsAscending().forEach { digit ->
                    assertTrue(digit in grid.candidatesAt(action.cell))
                }
            }
        }

        when (val evidence = step.evidence) {
            is StepEvidence.Single -> validateSingle(grid, step, evidence)
            is StepEvidence.LockedCandidates -> validateLocked(grid, step, evidence)
            is StepEvidence.Subset -> validateSubset(grid, step, evidence)
            is StepEvidence.Fish -> validateFish(grid, step, evidence)
            is StepEvidence.XYWing -> validateXYWing(grid, step, evidence)
            is StepEvidence.Skyscraper -> validateSkyscraper(grid, step, evidence)
            is StepEvidence.TwoStringKite -> validateTwoStringKite(grid, step, evidence)
        }
    }

    private fun validateSingle(
        grid: CandidateGrid,
        step: LogicalStep,
        evidence: StepEvidence.Single
    ) {
        val action = step.actions.single() as SolveAction.PlaceValue
        assertEquals(evidence.cell, action.cell)
        assertEquals(evidence.digit, action.digit)
        assertEquals(grid.candidatesAt(action.cell), evidence.candidates)
        if (step.technique == SudokuTechnique.NAKED_SINGLE) {
            assertEquals(1, evidence.candidates.size)
            assertEquals(null, evidence.uniqueIn)
        } else {
            val house = evidence.uniqueIn!!
            assertEquals(listOf(action.cell), grid.candidatePositions(house, action.digit))
        }
    }

    private fun validateLocked(
        grid: CandidateGrid,
        step: LogicalStep,
        evidence: StepEvidence.LockedCandidates
    ) {
        assertTrue(evidence.sourceCells.size >= 2)
        assertEquals(
            evidence.sourceCells,
            grid.candidatePositions(evidence.sourceHouse, evidence.digit)
        )
        val expectedTargets = grid.candidatePositions(evidence.targetHouse, evidence.digit)
            .filter { it !in grid.cellsIn(evidence.sourceHouse) }
        val expectedActions = expectedTargets.map {
            SolveAction.EliminateCandidates(it, DigitSet.of(evidence.digit))
        }
        assertEquals(expectedActions, step.actions)

        when (step.technique) {
            SudokuTechnique.LOCKED_CANDIDATES_POINTING -> {
                assertEquals(HouseType.BOX, evidence.sourceHouse.type)
                assertTrue(evidence.targetHouse.type in listOf(HouseType.ROW, HouseType.COLUMN))
            }
            SudokuTechnique.LOCKED_CANDIDATES_CLAIMING -> {
                assertTrue(evidence.sourceHouse.type in listOf(HouseType.ROW, HouseType.COLUMN))
                assertEquals(HouseType.BOX, evidence.targetHouse.type)
            }
            else -> throw AssertionError("Wrong locked-candidate technique: ${step.technique}")
        }
    }

    private fun validateSubset(
        grid: CandidateGrid,
        step: LogicalStep,
        evidence: StepEvidence.Subset
    ) {
        assertEquals(evidence.digits.size, evidence.cells.size)
        assertTrue(evidence.cells.all { it in grid.cellsIn(evidence.house) })
        val expectedActions = if (evidence.hidden) {
            evidence.digits.digitsAscending().forEach { digit ->
                assertTrue(grid.candidatePositions(evidence.house, digit).isNotEmpty())
                assertTrue(grid.candidatePositions(evidence.house, digit).all { it in evidence.cells })
            }
            evidence.cells.mapNotNull { cell ->
                val extras = grid.candidatesAt(cell).remove(evidence.digits)
                if (extras.isEmpty) null else SolveAction.EliminateCandidates(cell, extras)
            }
        } else {
            evidence.cells.forEach { cell ->
                val candidates = grid.candidatesAt(cell)
                assertFalse(candidates.isEmpty)
                assertEquals(0, candidates.mask and evidence.digits.mask.inv())
            }
            grid.cellsIn(evidence.house)
                .filter { grid.valueAt(it) == 0 && it !in evidence.cells }
                .mapNotNull { cell ->
                    val intersection = DigitSet.fromMask(
                        grid.candidatesAt(cell).mask and evidence.digits.mask
                    )
                    if (intersection.isEmpty) null
                    else SolveAction.EliminateCandidates(cell, intersection)
                }
        }
        assertEquals(expectedActions, step.actions)
    }

    private fun validateFish(
        grid: CandidateGrid,
        step: LogicalStep,
        evidence: StepEvidence.Fish
    ) {
        assertEquals(SudokuTechnique.X_WING, step.technique)
        assertEquals(2, evidence.baseHouses.size)
        assertEquals(2, evidence.coverHouses.size)
        assertEquals(4, evidence.cells.size)
        assertTrue(evidence.baseHouses.all { it.type == evidence.baseHouses.first().type })
        assertTrue(evidence.coverHouses.all { it.type == evidence.coverHouses.first().type })
        assertTrue(evidence.baseHouses.first().type != evidence.coverHouses.first().type)
        evidence.baseHouses.forEach { base ->
            assertEquals(
                evidence.cells.filter { it in grid.cellsIn(base) },
                grid.candidatePositions(base, evidence.digit)
            )
        }
        evidence.cells.forEach { support ->
            assertTrue(evidence.baseHouses.any { support in grid.cellsIn(it) })
            assertTrue(evidence.coverHouses.any { support in grid.cellsIn(it) })
            assertTrue(evidence.digit in grid.candidatesAt(support))
        }
        val expectedActions = evidence.coverHouses
            .flatMap { grid.candidatePositions(it, evidence.digit) }
            .filter { it !in evidence.cells }
            .distinct()
            .sorted()
            .map { SolveAction.EliminateCandidates(it, DigitSet.of(evidence.digit)) }
        assertEquals(expectedActions, step.actions)
    }

    private fun validateXYWing(
        grid: CandidateGrid,
        step: LogicalStep,
        evidence: StepEvidence.XYWing
    ) {
        assertEquals(SudokuTechnique.XY_WING, step.technique)
        assertEquals(evidence.pivotDigits, grid.candidatesAt(evidence.pivot))
        assertEquals(2, evidence.pincers.size)
        val sharedPivotDigits = evidence.pincers.map { pincer ->
            assertTrue(pincer in grid.peersOf(evidence.pivot))
            val candidates = grid.candidatesAt(pincer)
            assertEquals(2, candidates.size)
            val shared = DigitSet.fromMask(candidates.mask and evidence.pivotDigits.mask)
            assertEquals(1, shared.size)
            val third = candidates.remove(evidence.pivotDigits)
            assertEquals(DigitSet.of(evidence.eliminationDigit), third)
            shared
        }
        assertNotEquals(sharedPivotDigits[0], sharedPivotDigits[1])
        val firstPeers = grid.peersOf(evidence.firstPincer)
        val secondPeers = grid.peersOf(evidence.secondPincer)
        val expectedActions = grid.cellsRowMajor()
            .filter { cell ->
                cell != evidence.pivot &&
                    cell !in evidence.pincers &&
                    grid.valueAt(cell) == 0 &&
                    evidence.eliminationDigit in grid.candidatesAt(cell) &&
                    cell in firstPeers &&
                    cell in secondPeers
            }
            .map {
                SolveAction.EliminateCandidates(it, DigitSet.of(evidence.eliminationDigit))
            }
        assertEquals(expectedActions, step.actions)
    }

    private fun validateSkyscraper(
        grid: CandidateGrid,
        step: LogicalStep,
        evidence: StepEvidence.Skyscraper
    ) {
        assertEquals(SudokuTechnique.SKYSCRAPER, step.technique)
        assertEquals(2, evidence.sourceHouses.size)
        assertEquals(2, evidence.alignedCells.size)
        assertEquals(2, evidence.towers.size)
        evidence.sourceHouses.forEach { house ->
            assertEquals(evidence.orientation, house.type)
            val support = (evidence.alignedCells + evidence.towers).filter {
                it in grid.cellsIn(house)
            }.sorted()
            assertEquals(support, grid.candidatePositions(house, evidence.digit))
            assertEquals(2, support.size)
        }
        val alignedCovers = evidence.alignedCells.map {
            if (evidence.orientation == HouseType.ROW) it.column else it.row
        }
        assertEquals(alignedCovers[0], alignedCovers[1])
        val towerCovers = evidence.towers.map {
            if (evidence.orientation == HouseType.ROW) it.column else it.row
        }
        assertNotEquals(towerCovers[0], towerCovers[1])
        val firstPeers = grid.peersOf(evidence.towers[0])
        val secondPeers = grid.peersOf(evidence.towers[1])
        val support = evidence.alignedCells + evidence.towers
        val expectedActions = grid.cellsRowMajor()
            .filter { cell ->
                cell !in support &&
                    grid.valueAt(cell) == 0 &&
                    evidence.digit in grid.candidatesAt(cell) &&
                    cell in firstPeers &&
                    cell in secondPeers
            }
            .map { SolveAction.EliminateCandidates(it, DigitSet.of(evidence.digit)) }
        assertEquals(expectedActions, step.actions)
    }

    private fun validateTwoStringKite(
        grid: CandidateGrid,
        step: LogicalStep,
        evidence: StepEvidence.TwoStringKite
    ) {
        assertEquals(SudokuTechnique.TWO_STRING_KITE, step.technique)
        assertEquals(
            listOf(evidence.rowConnector, evidence.rowOuter).sorted(),
            grid.candidatePositions(evidence.rowHouse, evidence.digit)
        )
        assertEquals(
            listOf(evidence.columnConnector, evidence.columnOuter).sorted(),
            grid.candidatePositions(evidence.columnHouse, evidence.digit)
        )
        assertEquals(boxIndex(evidence.rowConnector), boxIndex(evidence.columnConnector))
        val canonicalConnectors = listOf(evidence.rowConnector, evidence.rowOuter)
            .flatMap { rowCell ->
                listOf(evidence.columnConnector, evidence.columnOuter).mapNotNull { columnCell ->
                    if (boxIndex(rowCell) == boxIndex(columnCell)) rowCell to columnCell else null
                }
            }
            .minWithOrNull(compareBy<Pair<CellRef, CellRef>>({ it.first }, { it.second }))
        assertEquals(evidence.rowConnector to evidence.columnConnector, canonicalConnectors)
        val support = listOf(
            evidence.rowConnector,
            evidence.columnConnector,
            evidence.rowOuter,
            evidence.columnOuter
        )
        assertEquals(4, support.distinct().size)
        val rowOuterPeers = grid.peersOf(evidence.rowOuter)
        val columnOuterPeers = grid.peersOf(evidence.columnOuter)
        val expectedActions = grid.cellsRowMajor()
            .filter { cell ->
                cell !in support &&
                    grid.valueAt(cell) == 0 &&
                    evidence.digit in grid.candidatesAt(cell) &&
                    cell in rowOuterPeers &&
                    cell in columnOuterPeers
            }
            .map { SolveAction.EliminateCandidates(it, DigitSet.of(evidence.digit)) }
        assertEquals(expectedActions, step.actions)
    }

    private fun classifyStall(seed: Long, grid: CandidateGrid): StalledDiagnostic {
        val emptyCells = grid.cellsRowMajor().filter { grid.valueAt(it) == 0 }
        val swordfish = hasSwordfishSignature(grid)
        val xyzWing = hasXYZWingSignature(grid)
        val classification = when {
            swordfish -> "likely Swordfish"
            xyzWing -> "likely XYZ-Wing"
            else -> "unclear/advanced"
        }
        val strongLinks = HouseType.entries.sumOf { type ->
            (0..8).sumOf { index ->
                (1..9).count { digit ->
                    grid.candidatePositions(HouseRef(type, index), digit).size == 2
                }
            }
        }
        return StalledDiagnostic(
            seed = seed,
            emptyCells = emptyCells.size,
            candidates = emptyCells.sumOf { grid.candidatesAt(it).size },
            bivalueCells = emptyCells.count { grid.candidatesAt(it).size == 2 },
            strongLinks = strongLinks,
            swordfishSignature = swordfish,
            xyzWingSignature = xyzWing,
            classification = classification
        )
    }

    /** Diagnostic signature only; it does not construct or apply a logical step. */
    private fun hasSwordfishSignature(grid: CandidateGrid): Boolean {
        for (baseType in listOf(HouseType.ROW, HouseType.COLUMN)) {
            for (digit in 1..9) {
                val eligible = (0..8).filter { index ->
                    grid.candidatePositions(HouseRef(baseType, index), digit).size in 2..3
                }
                for (first in 0 until eligible.size - 2) {
                    for (second in first + 1 until eligible.size - 1) {
                        for (third in second + 1 until eligible.size) {
                            val baseIndices = listOf(eligible[first], eligible[second], eligible[third])
                            val support = baseIndices.flatMap { index ->
                                grid.candidatePositions(HouseRef(baseType, index), digit)
                            }
                            val covers = support.map { cell ->
                                if (baseType == HouseType.ROW) cell.column else cell.row
                            }.distinct()
                            if (covers.size != 3) continue
                            val hasTarget = grid.cellsRowMajor().any { cell ->
                                grid.valueAt(cell) == 0 &&
                                    digit in grid.candidatesAt(cell) &&
                                    cell !in support &&
                                    (if (baseType == HouseType.ROW) cell.column else cell.row) in covers
                            }
                            if (hasTarget) return true
                        }
                    }
                }
            }
        }
        return false
    }

    /** Approximate XYZ-Wing signature used only to label likely beyond-XY stalls. */
    private fun hasXYZWingSignature(grid: CandidateGrid): Boolean {
        for (pivot in grid.cellsRowMajor()) {
            val pivotDigits = grid.candidatesAt(pivot)
            if (grid.valueAt(pivot) != 0 || pivotDigits.size != 3) continue
            val pincers = grid.peersOf(pivot).filter { cell ->
                grid.valueAt(cell) == 0 &&
                    grid.candidatesAt(cell).size == 2 &&
                    grid.candidatesAt(cell).mask and pivotDigits.mask.inv() == 0
            }
            for (first in 0 until pincers.lastIndex) {
                for (second in first + 1 until pincers.size) {
                    val firstDigits = grid.candidatesAt(pincers[first])
                    val secondDigits = grid.candidatesAt(pincers[second])
                    if (DigitSet.fromMask(firstDigits.mask or secondDigits.mask) != pivotDigits) continue
                    val shared = DigitSet.fromMask(firstDigits.mask and secondDigits.mask)
                    if (shared.size != 1) continue
                    val digit = shared.digitsAscending().single()
                    if (grid.cellsRowMajor().any { target ->
                            target != pivot &&
                                target != pincers[first] && target != pincers[second] &&
                                grid.valueAt(target) == 0 && digit in grid.candidatesAt(target) &&
                                target in grid.peersOf(pivot) &&
                                target in grid.peersOf(pincers[first]) &&
                                target in grid.peersOf(pincers[second])
                        }
                    ) return true
                }
            }
        }
        return false
    }

    private data class StalledDiagnostic(
        val seed: Long,
        val emptyCells: Int,
        val candidates: Int,
        val bivalueCells: Int,
        val strongLinks: Int,
        val swordfishSignature: Boolean,
        val xyzWingSignature: Boolean,
        val classification: String
    )

    private fun boxIndex(cell: CellRef): Int = (cell.row / 3) * 3 + cell.column / 3
}
