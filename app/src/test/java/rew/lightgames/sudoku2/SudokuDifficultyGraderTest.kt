package rew.lightgames.sudoku2

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SudokuDifficultyGraderTest {
    private val grader = SudokuDifficultyGrader()

    private val solvedBoard = intArrayOf(
        5, 3, 4, 6, 7, 8, 9, 1, 2,
        6, 7, 2, 1, 9, 5, 3, 4, 8,
        1, 9, 8, 3, 4, 2, 5, 6, 7,
        8, 5, 9, 7, 6, 1, 4, 2, 3,
        4, 2, 6, 8, 5, 3, 7, 9, 1,
        7, 1, 3, 9, 2, 4, 8, 5, 6,
        9, 6, 1, 5, 3, 7, 2, 8, 4,
        2, 8, 7, 4, 1, 9, 6, 3, 5,
        3, 4, 5, 2, 8, 6, 1, 7, 9
    )

    @Test
    fun v1BaseWeights_areExactForEveryTechnique() {
        val expected = linkedMapOf(
            SudokuTechnique.NAKED_SINGLE to 1,
            SudokuTechnique.HIDDEN_SINGLE to 2,
            SudokuTechnique.LOCKED_CANDIDATES_POINTING to 4,
            SudokuTechnique.LOCKED_CANDIDATES_CLAIMING to 5,
            SudokuTechnique.NAKED_PAIR to 6,
            SudokuTechnique.HIDDEN_PAIR to 8,
            SudokuTechnique.NAKED_TRIPLE to 10,
            SudokuTechnique.HIDDEN_TRIPLE to 12,
            SudokuTechnique.X_WING to 14,
            SudokuTechnique.XY_WING to 18,
            SudokuTechnique.SKYSCRAPER to 16,
            SudokuTechnique.TWO_STRING_KITE to 16
        )

        assertEquals(SudokuTechnique.entries, expected.keys.toList())
        expected.forEach { (technique, weight) ->
            assertEquals("Weight changed for $technique", weight, DifficultyGradingV1.baseWeight(technique))
        }
    }

    @Test
    fun stepScore_nakedAndHiddenSinglesUseTheirExactBaseWeights() {
        assertEquals(1, DifficultyGradingV1.stepScore(singleStep(SudokuTechnique.NAKED_SINGLE), 0))
        assertEquals(2, DifficultyGradingV1.stepScore(singleStep(SudokuTechnique.HIDDEN_SINGLE), 0))
    }

    @Test
    fun stepScore_eliminationBreadthOneAddsZero() {
        val step = eliminationStep(SudokuTechnique.NAKED_PAIR, candidatesRemoved = 1)
        assertEquals(6, DifficultyGradingV1.stepScore(step, 0))
    }

    @Test
    fun stepScore_eliminationBreadthTwoAddsOne() {
        val step = eliminationStep(SudokuTechnique.NAKED_PAIR, candidatesRemoved = 2)
        assertEquals(7, DifficultyGradingV1.stepScore(step, 0))
    }

    @Test
    fun stepScore_eliminationBreadthFourAndAboveCapsAtThree() {
        val breadthFour = eliminationStep(SudokuTechnique.NAKED_PAIR, candidatesRemoved = 4)
        val breadthNine = eliminationStep(SudokuTechnique.NAKED_PAIR, candidatesRemoved = 9)
        assertEquals(9, DifficultyGradingV1.stepScore(breadthFour, 0))
        assertEquals(9, DifficultyGradingV1.stepScore(breadthNine, 0))
    }

    @Test
    fun stepScore_firstWeightedTechniqueUseHasNoRepetitionSurcharge() {
        val step = eliminationStep(SudokuTechnique.X_WING, candidatesRemoved = 1)
        assertEquals(14, DifficultyGradingV1.stepScore(step, 0))
    }

    @Test
    fun stepScore_repetitionSurchargeUsesPreviousOccurrencesAndIntegerFlooring() {
        val step = eliminationStep(SudokuTechnique.NAKED_PAIR, candidatesRemoved = 1)
        assertEquals(7, DifficultyGradingV1.stepScore(step, 1))
        assertEquals(9, DifficultyGradingV1.stepScore(step, 2))
        assertEquals(10, DifficultyGradingV1.stepScore(step, 3))
    }

    @Test
    fun stepScore_repetitionSurchargeCapsAfterThreePreviousUses() {
        val step = eliminationStep(SudokuTechnique.XY_WING, candidatesRemoved = 1)
        assertEquals(31, DifficultyGradingV1.stepScore(step, 3))
        assertEquals(31, DifficultyGradingV1.stepScore(step, 4))
        assertEquals(31, DifficultyGradingV1.stepScore(step, 100))
    }

    @Test
    fun stepScore_baseWeightsBelowSixNeverReceiveRepetitionSurcharge() {
        val step = eliminationStep(SudokuTechnique.LOCKED_CANDIDATES_CLAIMING, 1)
        assertEquals(5, DifficultyGradingV1.stepScore(step, 100))
    }

    @Test
    fun classify_exactBandBoundariesAreStable() {
        assertEquals(
            SudokuDifficulty.EASY,
            DifficultyGradingV1.classify(LogicalSolveStatus.SOLVED, 65, false, false)
        )
        assertEquals(
            SudokuDifficulty.MEDIUM,
            DifficultyGradingV1.classify(LogicalSolveStatus.SOLVED, 66, false, false)
        )
        assertEquals(
            SudokuDifficulty.MEDIUM,
            DifficultyGradingV1.classify(LogicalSolveStatus.SOLVED, 160, true, false)
        )
        assertEquals(
            SudokuDifficulty.UNSUPPORTED,
            DifficultyGradingV1.classify(LogicalSolveStatus.SOLVED, 161, true, false)
        )
        assertEquals(
            SudokuDifficulty.HARD,
            DifficultyGradingV1.classify(LogicalSolveStatus.SOLVED, 320, true, true)
        )
        assertEquals(
            SudokuDifficulty.UNSUPPORTED,
            DifficultyGradingV1.classify(LogicalSolveStatus.SOLVED, 321, true, true)
        )
    }

    @Test
    fun grade_singlesOnlyBelowEasyCeilingIsEasy() {
        val rating = ratingForSeed(28L)

        assertEquals(SudokuDifficulty.EASY, rating.difficulty)
        assertEquals(57, rating.totalScore)
        assertEquals(SudokuTechnique.NAKED_SINGLE, rating.hardestTechnique)
        assertEquals(57, rating.stepCount)
        assertEquals(57, rating.placements)
        assertEquals(0, rating.candidateEliminations)
    }

    @Test
    fun grade_longSinglesOnlyTraceIsMediumButNeverHard() {
        val rating = ratingForSeed(2L)

        assertEquals(77, rating.totalScore)
        assertEquals(SudokuDifficulty.MEDIUM, rating.difficulty)
        assertEquals(SudokuTechnique.HIDDEN_SINGLE, rating.hardestTechnique)
        assertTrue(rating.techniqueCounts.entries.all { (technique, count) ->
            count == 0 || DifficultyGradingV1.tierOf(technique) == DifficultyTechniqueTier.EASY
        })
    }

    @Test
    fun grade_pointingClaimingAndPairEachImposeMediumFloor() {
        val techniques = listOf(
            SudokuTechnique.LOCKED_CANDIDATES_POINTING,
            SudokuTechnique.LOCKED_CANDIDATES_CLAIMING,
            SudokuTechnique.NAKED_PAIR,
            SudokuTechnique.HIDDEN_PAIR
        )

        techniques.forEach { technique ->
            assertEquals(DifficultyTechniqueTier.MEDIUM, DifficultyGradingV1.tierOf(technique))
            assertEquals(
                "$technique must impose Medium",
                SudokuDifficulty.MEDIUM,
                DifficultyGradingV1.classify(
                    LogicalSolveStatus.SOLVED,
                    DifficultyGradingV1.baseWeight(technique),
                    hasMediumTechnique = true,
                    hasHardTechnique = false
                )
            )
        }
    }

    @Test
    fun grade_everyHardTechniqueImposesHardFloorAtLowScore() {
        val techniques = listOf(
            SudokuTechnique.NAKED_TRIPLE,
            SudokuTechnique.HIDDEN_TRIPLE,
            SudokuTechnique.X_WING,
            SudokuTechnique.XY_WING,
            SudokuTechnique.SKYSCRAPER,
            SudokuTechnique.TWO_STRING_KITE
        )

        techniques.forEach { technique ->
            val lowScore = DifficultyGradingV1.baseWeight(technique)
            assertEquals(DifficultyTechniqueTier.HARD, DifficultyGradingV1.tierOf(technique))
            assertTrue(lowScore < DifficultyGradingV1.EASY_MAX_SCORE)
            assertEquals(
                "$technique must impose Hard",
                SudokuDifficulty.HARD,
                DifficultyGradingV1.classify(
                    LogicalSolveStatus.SOLVED,
                    lowScore,
                    hasMediumTechnique = false,
                    hasHardTechnique = true
                )
            )
        }
    }

    @Test
    fun grade_mediumOnlyScoreAbove160IsUnsupportedNotHard() {
        assertEquals(
            SudokuDifficulty.UNSUPPORTED,
            DifficultyGradingV1.classify(
                LogicalSolveStatus.SOLVED,
                161,
                hasMediumTechnique = true,
                hasHardTechnique = false
            )
        )
    }

    @Test
    fun grade_hardTechniqueScoreAbove320IsUnsupported() {
        assertEquals(
            SudokuDifficulty.UNSUPPORTED,
            DifficultyGradingV1.classify(
                LogicalSolveStatus.SOLVED,
                321,
                hasMediumTechnique = true,
                hasHardTechnique = true
            )
        )
    }

    @Test
    fun grade_stalledAndInvalidAreUnsupported() {
        val stalledPuzzle = SudokuPuzzleEngine(1L).generate().puzzle
        val stalled = SudokuLogicalSolver().solve(stalledPuzzle)
        val invalidPuzzle = IntArray(81).also { it[0] = 10 }
        val invalid = SudokuLogicalSolver().solve(invalidPuzzle)

        assertEquals(SudokuDifficulty.UNSUPPORTED, grader.grade(stalledPuzzle, stalled).difficulty)
        assertEquals(SudokuDifficulty.UNSUPPORTED, grader.grade(invalidPuzzle, invalid).difficulty)
    }

    @Test
    fun grade_exposesExactCanonicalDiagnosticsAndVersions() {
        val generated = SudokuPuzzleEngine(9L).generate()
        val result = SudokuLogicalSolver().solve(generated.puzzle)
        val rating = grader.grade(generated.puzzle, result)
        val expectedPlacements = result.steps.sumOf { step ->
            step.actions.count { it is SolveAction.PlaceValue }
        }
        val expectedEliminations = result.steps.sumOf { step ->
            step.actions.sumOf { action ->
                if (action is SolveAction.EliminateCandidates) action.digits.size else 0
            }
        }

        assertEquals(result.steps.size, rating.stepCount)
        assertEquals(expectedPlacements, rating.placements)
        assertEquals(expectedEliminations, rating.candidateEliminations)
        assertTrue(rating.candidateEliminations > 0)
        assertEquals(SudokuTechnique.entries, rating.techniqueCounts.keys.toList())
        assertEquals(result.steps.size, rating.techniqueCounts.values.sum())
        assertTrue(rating.techniqueCounts.getValue(SudokuTechnique.HIDDEN_PAIR) > 0)
        assertEquals(1, rating.graderVersion)
        assertEquals(1, rating.logicalSolverVersion)
    }

    @Test
    fun grade_techniqueCountsCannotBeMutated() {
        val rating = ratingForSeed(28L)
        val before = rating.techniqueCounts.getValue(SudokuTechnique.NAKED_SINGLE)

        assertThrows(ClassCastException::class.java) {
            @Suppress("UNCHECKED_CAST")
            val mutable = rating.techniqueCounts as MutableMap<SudokuTechnique, Int>
            mutable[SudokuTechnique.NAKED_SINGLE] = 99
        }
        assertEquals(before, rating.techniqueCounts.getValue(SudokuTechnique.NAKED_SINGLE))
    }

    @Test
    fun grade_isStructurallyDeterministicIncludingMapOrder() {
        val puzzle = SudokuPuzzleEngine(16L).generate().puzzle
        val result = SudokuLogicalSolver().solve(puzzle)
        val expected = grader.grade(puzzle, result)

        repeat(20) {
            val actual = grader.grade(puzzle.clone(), result)
            assertEquals(expected, actual)
            assertEquals(expected.hashCode(), actual.hashCode())
            assertEquals(SudokuTechnique.entries, actual.techniqueCounts.keys.toList())
        }
        assertEquals(SudokuTechnique.SKYSCRAPER, expected.hardestTechnique)
    }

    @Test
    fun grade_clueCountIsTelemetryOnly() {
        val oneBlank = solvedBoard.clone().also { it[0] = 0 }
        val oneBlankRating = grader.grade(oneBlank, SudokuLogicalSolver().solve(oneBlank))
        val solvedRating = grader.grade(solvedBoard, SudokuLogicalSolver().solve(solvedBoard))

        assertEquals(80, oneBlankRating.clueCount)
        assertEquals(81, solvedRating.clueCount)
        assertEquals(SudokuDifficulty.EASY, oneBlankRating.difficulty)
        assertEquals(SudokuDifficulty.EASY, solvedRating.difficulty)
    }

    @Test
    fun grade_doesNotMutatePuzzleOrSolveResult() {
        val puzzle = SudokuPuzzleEngine(28L).generate().puzzle
        val before = puzzle.clone()
        val result = SudokuLogicalSolver().solve(puzzle)
        val beforeBoard = result.finalBoard
        val beforeSteps = result.steps.toList()

        grader.grade(puzzle, result)

        assertArrayEquals(before, puzzle)
        assertArrayEquals(beforeBoard, result.finalBoard)
        assertEquals(beforeSteps, result.steps)
    }

    @Test
    fun grade_rejectsWrongPuzzleLength() {
        assertThrows(IllegalArgumentException::class.java) {
            grader.grade(IntArray(80), SudokuLogicalSolver().solve(solvedBoard))
        }
    }

    @Test
    fun grade_rejectsSolvedResultWithIncompleteFinalBoard() {
        val incomplete = solvedBoard.clone().also { it[80] = 0 }
        val masks = IntArray(81).also { it[80] = DigitSet.of(9).mask }
        val result = LogicalSolveResult(
            LogicalSolveStatus.SOLVED,
            incomplete,
            CandidateGridSnapshot(incomplete, masks),
            emptyList()
        )

        assertThrows(IllegalArgumentException::class.java) {
            grader.grade(IntArray(81), result)
        }
    }

    @Test
    fun grade_rejectsFinalBoardAndCandidateSnapshotMismatch() {
        val mismatched = solvedBoard.clone().also { it[0] = 0 }
        val result = LogicalSolveResult(
            LogicalSolveStatus.SOLVED,
            solvedBoard,
            CandidateGridSnapshot(mismatched, IntArray(81)),
            emptyList()
        )

        assertThrows(IllegalArgumentException::class.java) {
            grader.grade(IntArray(81), result)
        }
    }

    @Test
    fun grade_rejectsTechniqueActionOrEvidenceMismatch() {
        val evidence = StepEvidence.Fish(
            digit = 1,
            baseHouses = listOf(HouseRef(HouseType.ROW, 0), HouseRef(HouseType.ROW, 1)),
            coverHouses = listOf(HouseRef(HouseType.COLUMN, 0), HouseRef(HouseType.COLUMN, 1)),
            cells = listOf(CellRef(0, 0), CellRef(0, 1), CellRef(1, 0), CellRef(1, 1))
        )
        val invalidStep = LogicalStep(
            SudokuTechnique.X_WING,
            listOf(SolveAction.PlaceValue(CellRef(8, 8), 9)),
            evidence
        )

        assertThrows(IllegalArgumentException::class.java) {
            grader.grade(
                IntArray(81),
                LogicalSolveResult(
                    LogicalSolveStatus.SOLVED,
                    solvedBoard,
                    CandidateGridSnapshot(solvedBoard, IntArray(81)),
                    listOf(invalidStep)
                )
            )
        }
    }

    @Test
    fun grade_rejectsTraceThatDoesNotReproduceFinalSnapshot() {
        val unrelatedTrace = LogicalSolveResult(
            LogicalSolveStatus.SOLVED,
            solvedBoard,
            CandidateGridSnapshot(solvedBoard, IntArray(81)),
            listOf(singleStep(SudokuTechnique.NAKED_SINGLE))
        )

        assertThrows(IllegalArgumentException::class.java) {
            grader.grade(IntArray(81), unrelatedTrace)
        }
    }

    @Test
    fun grade_rejectsEliminationThatIncludesCandidateNotPresent() {
        val puzzle = solvedBoard.clone().also { it[0] = 0 }
        val invalidElimination = LogicalStep(
            SudokuTechnique.NAKED_PAIR,
            listOf(
                SolveAction.EliminateCandidates(CellRef(0, 0), DigitSet.of(1, 5))
            ),
            StepEvidence.Subset(
                house = HouseRef(HouseType.ROW, 0),
                digits = DigitSet.of(1, 5),
                cells = listOf(CellRef(0, 0), CellRef(0, 1)),
                hidden = false
            )
        )
        val result = LogicalSolveResult(
            LogicalSolveStatus.STALLED,
            puzzle,
            (CandidateGrid.create(puzzle) as CandidateGridCreationResult.Success).grid.snapshot(),
            listOf(invalidElimination)
        )

        assertThrows(IllegalArgumentException::class.java) {
            grader.grade(puzzle, result)
        }
    }

    @Test
    fun grade_alreadySolvedBoardHasZeroScoreAndNoHardestTechnique() {
        val rating = grader.grade(solvedBoard, SudokuLogicalSolver().solve(solvedBoard))

        assertEquals(SudokuDifficulty.EASY, rating.difficulty)
        assertEquals(0, rating.totalScore)
        assertEquals(0, rating.stepCount)
        assertNull(rating.hardestTechnique)
        assertEquals(81, rating.clueCount)
    }

    private fun ratingForSeed(seed: Long): DifficultyRating {
        val puzzle = SudokuPuzzleEngine(seed).generate().puzzle
        return grader.grade(puzzle, SudokuLogicalSolver().solve(puzzle))
    }

    private fun singleStep(technique: SudokuTechnique): LogicalStep {
        require(technique == SudokuTechnique.NAKED_SINGLE || technique == SudokuTechnique.HIDDEN_SINGLE)
        val cell = CellRef(0, 0)
        val digit = 5
        return LogicalStep(
            technique,
            listOf(SolveAction.PlaceValue(cell, digit)),
            StepEvidence.Single(
                cell = cell,
                digit = digit,
                candidates = DigitSet.of(digit),
                uniqueIn = if (technique == SudokuTechnique.HIDDEN_SINGLE) {
                    HouseRef(HouseType.ROW, 0)
                } else {
                    null
                }
            )
        )
    }

    private fun eliminationStep(
        technique: SudokuTechnique,
        candidatesRemoved: Int
    ): LogicalStep {
        require(technique != SudokuTechnique.NAKED_SINGLE && technique != SudokuTechnique.HIDDEN_SINGLE)
        require(candidatesRemoved in 1..9)
        val digits = DigitSet.of(*(1..candidatesRemoved).toList().toIntArray())
        val evidence: StepEvidence = when (technique) {
            SudokuTechnique.LOCKED_CANDIDATES_POINTING -> StepEvidence.LockedCandidates(
                digit = 1,
                sourceHouse = HouseRef(HouseType.BOX, 0),
                targetHouse = HouseRef(HouseType.ROW, 0),
                sourceCells = listOf(CellRef(0, 0), CellRef(0, 1))
            )
            SudokuTechnique.LOCKED_CANDIDATES_CLAIMING -> StepEvidence.LockedCandidates(
                digit = 1,
                sourceHouse = HouseRef(HouseType.ROW, 0),
                targetHouse = HouseRef(HouseType.BOX, 0),
                sourceCells = listOf(CellRef(0, 0), CellRef(0, 1))
            )
            SudokuTechnique.NAKED_PAIR,
            SudokuTechnique.HIDDEN_PAIR -> StepEvidence.Subset(
                house = HouseRef(HouseType.ROW, 0),
                digits = DigitSet.of(1, 2),
                cells = listOf(CellRef(0, 0), CellRef(0, 1)),
                hidden = technique == SudokuTechnique.HIDDEN_PAIR
            )
            SudokuTechnique.NAKED_TRIPLE,
            SudokuTechnique.HIDDEN_TRIPLE -> StepEvidence.Subset(
                house = HouseRef(HouseType.ROW, 0),
                digits = DigitSet.of(1, 2, 3),
                cells = listOf(CellRef(0, 0), CellRef(0, 1), CellRef(0, 2)),
                hidden = technique == SudokuTechnique.HIDDEN_TRIPLE
            )
            SudokuTechnique.X_WING -> StepEvidence.Fish(
                digit = 1,
                baseHouses = listOf(HouseRef(HouseType.ROW, 0), HouseRef(HouseType.ROW, 1)),
                coverHouses = listOf(HouseRef(HouseType.COLUMN, 0), HouseRef(HouseType.COLUMN, 1)),
                cells = listOf(CellRef(0, 0), CellRef(0, 1), CellRef(1, 0), CellRef(1, 1))
            )
            SudokuTechnique.XY_WING -> StepEvidence.XYWing(
                pivot = CellRef(0, 0),
                pincers = listOf(CellRef(0, 1), CellRef(1, 0)),
                pivotDigits = DigitSet.of(1, 2),
                eliminationDigit = 3
            )
            SudokuTechnique.SKYSCRAPER -> StepEvidence.Skyscraper(
                digit = 1,
                orientation = HouseType.ROW,
                sourceHouses = listOf(HouseRef(HouseType.ROW, 0), HouseRef(HouseType.ROW, 1)),
                alignedCells = listOf(CellRef(0, 0), CellRef(1, 0)),
                towers = listOf(CellRef(0, 1), CellRef(1, 2))
            )
            SudokuTechnique.TWO_STRING_KITE -> StepEvidence.TwoStringKite(
                digit = 1,
                rowHouse = HouseRef(HouseType.ROW, 0),
                columnHouse = HouseRef(HouseType.COLUMN, 0),
                rowConnector = CellRef(0, 1),
                columnConnector = CellRef(1, 0),
                rowOuter = CellRef(0, 8),
                columnOuter = CellRef(8, 0)
            )
            SudokuTechnique.NAKED_SINGLE,
            SudokuTechnique.HIDDEN_SINGLE -> error("Singles use placement actions")
        }
        return LogicalStep(
            technique,
            listOf(SolveAction.EliminateCandidates(CellRef(8, 8), digits)),
            evidence
        )
    }
}
