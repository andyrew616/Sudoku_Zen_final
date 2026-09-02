package rew.lightgames.sudoku2

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DifficultyTargetGeneratorTest {
    private val generator = DifficultyTargetGenerator()
    private val logicalSolver = SudokuLogicalSolver()
    private val grader = SudokuDifficultyGrader()

    @Test
    fun v1EngineVersionsAndBudgets_areExact() {
        assertEquals(
            PuzzleEngineVersion(generator = 1, logicalSolver = 1, grader = 1, targeting = 1),
            PuzzleEngineVersions.CURRENT
        )
        assertEquals(1, SudokuGeneratorVersion.CURRENT)
        assertEquals(1, LogicalSolverVersion.CURRENT)
        assertEquals(1, DifficultyGraderVersion.CURRENT)
        assertEquals(1, DifficultyTargetingVersion.CURRENT)
        assertEquals(8, DifficultyTargetingV1.EASY_ATTEMPT_BUDGET)
        assertEquals(12, DifficultyTargetingV1.MEDIUM_ATTEMPT_BUDGET)
        assertEquals(20, DifficultyTargetingV1.HARD_ATTEMPT_BUDGET)
        assertEquals(24, DifficultyTargetingV1.MAX_CLUE_RESTORATIONS)
    }

    @Test
    fun attemptSeedDerivation_matchesTargetingV1GoldenVectors() {
        val version = PuzzleEngineVersions.CURRENT
        val vectors = listOf(
            AttemptSeedVector(0L, SudokuDifficulty.EASY, 0, -5863655168647535853L),
            AttemptSeedVector(0L, SudokuDifficulty.MEDIUM, 0, 8486083641703992734L),
            AttemptSeedVector(0L, SudokuDifficulty.HARD, 0, 1211942191576309530L),
            AttemptSeedVector(1L, SudokuDifficulty.EASY, 1, 8065724295957728896L),
            AttemptSeedVector(-1L, SudokuDifficulty.MEDIUM, 1, -1275222317401429600L),
            AttemptSeedVector(Long.MIN_VALUE, SudokuDifficulty.EASY, 7, 2210884564322194224L),
            AttemptSeedVector(Long.MIN_VALUE, SudokuDifficulty.HARD, 19, -6589355282307719878L),
            AttemptSeedVector(Long.MAX_VALUE, SudokuDifficulty.MEDIUM, 7, 2436281088826687019L),
            AttemptSeedVector(Long.MAX_VALUE, SudokuDifficulty.HARD, 19, 3072461673424601105L)
        )

        vectors.forEach { vector ->
            assertEquals(
                vector.expected,
                TargetingSeedDerivationV1.attemptSeed(
                    vector.baseSeed,
                    vector.difficulty,
                    version,
                    vector.attemptIndex
                )
            )
        }
    }

    @Test
    fun attemptSeedDerivation_includesEveryVersionAndTargetField() {
        val version = PuzzleEngineVersions.CURRENT
        val baseline = TargetingSeedDerivationV1.attemptSeed(42L, SudokuDifficulty.EASY, version, 0)
        val variants = listOf(
            TargetingSeedDerivationV1.attemptSeed(43L, SudokuDifficulty.EASY, version, 0),
            TargetingSeedDerivationV1.attemptSeed(42L, SudokuDifficulty.MEDIUM, version, 0),
            TargetingSeedDerivationV1.attemptSeed(42L, SudokuDifficulty.EASY, version.copy(generator = 2), 0),
            TargetingSeedDerivationV1.attemptSeed(42L, SudokuDifficulty.EASY, version.copy(logicalSolver = 2), 0),
            TargetingSeedDerivationV1.attemptSeed(42L, SudokuDifficulty.EASY, version.copy(grader = 2), 0),
            TargetingSeedDerivationV1.attemptSeed(42L, SudokuDifficulty.EASY, version.copy(targeting = 2), 0),
            TargetingSeedDerivationV1.attemptSeed(42L, SudokuDifficulty.EASY, version, 1)
        )

        assertEquals(variants.size, variants.toSet().size)
        variants.forEach { assertNotEquals(baseline, it) }
    }

    @Test
    fun restorationShuffle_matchesTargetingV1GoldenVector() {
        val first = IntArray(12) { it }
        val repeated = first.clone()

        TargetingSeedDerivationV1.shuffleForRestoration(first, -5863655168647535853L)
        TargetingSeedDerivationV1.shuffleForRestoration(repeated, -5863655168647535853L)

        assertArrayEquals(intArrayOf(0, 9, 11, 5, 2, 1, 4, 8, 6, 3, 10, 7), first)
        assertArrayEquals(first, repeated)
    }

    @Test
    fun unsupportedRequest_returnsTypedFailureWithoutAttemptingGeneration() {
        var calls = 0
        val constrained = DifficultyTargetGenerator(TargetGenerationPolicy.V1) {
            calls++
            error("UNSUPPORTED must not generate")
        }

        val result = constrained.generate(42L, SudokuDifficulty.UNSUPPORTED)

        assertTrue(result is TargetGenerationResult.Failure)
        val failure = (result as TargetGenerationResult.Failure).failure
        assertEquals(TargetGenerationFailureReason.UNSUPPORTED_REQUEST, failure.reason)
        assertEquals(0, failure.attemptsUsed)
        assertEquals(0, failure.ratingsEvaluated)
        assertEquals(null, failure.bestObservedRating)
        assertEquals(0, calls)
    }

    @Test
    fun exactEasyMediumAndHardTargets_obeyPublishedDifficultyContract() {
        assertExactContract(success(7L, SudokuDifficulty.EASY))
        assertExactContract(success(0L, SudokuDifficulty.MEDIUM))
        assertExactContract(success(11L, SudokuDifficulty.HARD))
    }

    @Test
    fun harderSinglesEffortPuzzle_isDeterministicallyRestoredToEasy() {
        val observations = arrayListOf<TargetGenerationObservation>()
        val targeted = success(0L, SudokuDifficulty.EASY, observations)

        assertEquals(0, targeted.attemptIndex)
        assertEquals(3, targeted.cluesRestored)
        assertEquals(SudokuDifficulty.MEDIUM, observations.first().rating.difficulty)
        assertEquals(SudokuDifficulty.EASY, observations.last().rating.difficulty)
        assertTrue(observations.all { observation ->
            observation.rating.techniqueCounts.entries.all { (technique, count) ->
                count == 0 || DifficultyGradingV1.tierOf(technique) == DifficultyTechniqueTier.EASY
            }
        })
    }

    @Test
    fun hardPuzzle_isDeterministicallyRestoredToMedium() {
        val observations = arrayListOf<TargetGenerationObservation>()
        val targeted = success(5L, SudokuDifficulty.MEDIUM, observations)

        assertEquals(0, targeted.attemptIndex)
        assertEquals(2, targeted.cluesRestored)
        assertEquals(SudokuDifficulty.HARD, observations.first().rating.difficulty)
        assertEquals(SudokuDifficulty.MEDIUM, observations.last().rating.difficulty)
        assertExactContract(targeted)
    }

    @Test
    fun stalledPuzzle_isDeterministicallyRestoredToHard() {
        val observations = arrayListOf<TargetGenerationObservation>()
        val targeted = success(9L, SudokuDifficulty.HARD, observations)

        assertEquals(0, targeted.attemptIndex)
        assertEquals(7, targeted.cluesRestored)
        assertEquals(LogicalSolveStatus.STALLED, observations.first().logicalStatus)
        assertEquals(SudokuDifficulty.UNSUPPORTED, observations.first().rating.difficulty)
        assertEquals(LogicalSolveStatus.SOLVED, observations.last().logicalStatus)
        assertExactContract(targeted)
    }

    @Test
    fun restorationOvershoot_abandonsPathAndRetriesWithoutBranching() {
        val observations = arrayListOf<TargetGenerationObservation>()
        val targeted = success(4L, SudokuDifficulty.MEDIUM, observations)
        val firstAttempt = observations.filter { it.attemptIndex == 0 }

        assertEquals(SudokuDifficulty.HARD, firstAttempt.first().rating.difficulty)
        assertEquals(SudokuDifficulty.EASY, firstAttempt.last().rating.difficulty)
        assertEquals(2, firstAttempt.last().cluesRestored)
        assertFalse(firstAttempt.any { it.cluesRestored > 2 })
        assertEquals(1, targeted.attemptIndex)
        assertEquals(1, targeted.cluesRestored)
        assertEquals(SudokuDifficulty.MEDIUM, targeted.rating.difficulty)
    }

    @Test
    fun tooEasyCandidate_neverFallsBackToClosestGradeWhenAttemptBudgetExhausts() {
        val easyFixture = SudokuPuzzleEngine(28L).generate()
        val policy = TargetGenerationPolicy(1, 1, 1, 24)
        val constrained = fixtureGenerator(policy, easyFixture)

        val first = constrained.generate(99L, SudokuDifficulty.HARD)
        val repeated = constrained.generate(99L, SudokuDifficulty.HARD)

        assertEquals(first, repeated)
        assertTrue(first is TargetGenerationResult.Failure)
        val failure = (first as TargetGenerationResult.Failure).failure
        assertEquals(TargetGenerationFailureReason.ATTEMPT_BUDGET_EXHAUSTED, failure.reason)
        assertEquals(1, failure.attemptsUsed)
        assertEquals(1, failure.ratingsEvaluated)
        assertEquals(SudokuDifficulty.EASY, failure.bestObservedRating?.difficulty)
        assertEquals(0, failure.cluesRestored)
    }

    @Test
    fun restorationBudgetExhaustion_returnsDeterministicTypedFailure() {
        val stalledFixture = SudokuPuzzleEngine(1L).generate()
        val policy = TargetGenerationPolicy(1, 1, 1, 0)
        val constrained = fixtureGenerator(policy, stalledFixture)

        val first = constrained.generate(100L, SudokuDifficulty.EASY)
        val repeated = constrained.generate(100L, SudokuDifficulty.EASY)

        assertEquals(first, repeated)
        assertTrue(first is TargetGenerationResult.Failure)
        val failure = (first as TargetGenerationResult.Failure).failure
        assertEquals(TargetGenerationFailureReason.ATTEMPT_BUDGET_EXHAUSTED, failure.reason)
        assertEquals(1, failure.restorationLimitHits)
        assertEquals(0, failure.cluesRestored)
        assertEquals(0, failure.overshootCount)
        assertEquals(SudokuDifficulty.UNSUPPORTED, failure.bestObservedRating?.difficulty)
    }

    @Test
    fun malformedGeneratedPuzzle_isAnInternalDefectNotMerelyTooDifficult() {
        val constrained = DifficultyTargetGenerator(TargetGenerationPolicy.V1) { attemptSeed ->
            GeneratedPuzzle(IntArray(81), IntArray(81), attemptSeed)
        }

        val result = constrained.generate(7L, SudokuDifficulty.EASY)

        assertTrue(result is TargetGenerationResult.Failure)
        val failure = (result as TargetGenerationResult.Failure).failure
        assertEquals(TargetGenerationFailureReason.INTERNAL_GENERATION_DEFECT, failure.reason)
        assertEquals(1, failure.attemptsUsed)
        assertEquals(0, failure.ratingsEvaluated)
    }

    @Test
    fun seedEdgeCases_areStructurallyDeterministicForEveryDifficulty() {
        val seeds = listOf(0L, 1L, -1L, Long.MIN_VALUE, Long.MAX_VALUE)
        val difficulties = listOf(
            SudokuDifficulty.EASY,
            SudokuDifficulty.MEDIUM,
            SudokuDifficulty.HARD
        )

        seeds.forEach { seed ->
            difficulties.forEach { difficulty ->
                val first = generator.generate(seed, difficulty)
                val repeated = generator.generate(seed, difficulty)
                assertEquals("seed=$seed difficulty=$difficulty", first, repeated)
                if (first is TargetGenerationResult.Success) {
                    val targeted = first.targetedPuzzle
                    val puzzle = targeted.puzzle
                    val trace = logicalSolver.solve(puzzle)
                    assertEquals(LogicalSolveStatus.SOLVED, trace.status)
                    assertEquals(targeted.rating, grader.grade(puzzle, trace))
                }
            }
        }
    }

    @Test
    fun returnedArraysAndFactoryArrays_haveNoMutableAliases() {
        val generated = SudokuPuzzleEngine(1L).generate()
        val originalPuzzle = generated.puzzle.clone()
        val originalSolution = generated.solution.clone()
        val constrained = fixtureGenerator(TargetGenerationPolicy(1, 1, 1, 1), generated)
        constrained.generate(0L, SudokuDifficulty.EASY)
        assertArrayEquals(originalPuzzle, generated.puzzle)
        assertArrayEquals(originalSolution, generated.solution)

        val targeted = success(7L, SudokuDifficulty.EASY)
        val exposedPuzzle = targeted.puzzle
        val exposedSolution = targeted.solution
        val originalFirstPuzzleCell = targeted.puzzle[0]
        val originalFirstSolutionCell = targeted.solution[0]
        exposedPuzzle[0] = (exposedPuzzle[0] + 1) % 10
        exposedSolution[0] = (exposedSolution[0] % 9) + 1

        assertEquals(originalFirstPuzzleCell, targeted.puzzle[0])
        assertEquals(originalFirstSolutionCell, targeted.solution[0])
    }

    @Test
    fun targetedPuzzle_mapsToExistingBoardAndCellContractWithoutProductionIntegration() {
        val targeted = success(7L, SudokuDifficulty.EASY)
        val puzzle = targeted.puzzle
        val solution = targeted.solution
        val cells = Array(9) { row ->
            Array(9) { column ->
                val value = puzzle[row * 9 + column]
                Cell(number = value, original_number = value)
            }
        }
        val solutionGrid = Array(9) { row -> IntArray(9) { column -> solution[row * 9 + column] } }
        val board = SudokuBoard(cells, solutionGrid)

        for (row in 0 until 9) {
            for (column in 0 until 9) {
                val value = puzzle[row * 9 + column]
                assertEquals(value, board.getCell(row, column).number)
                assertEquals(value == 0, board.getCell(row, column).isEditable)
                if (value == 0) {
                    board.setCell(
                        row,
                        column,
                        Cell(number = solution[row * 9 + column], original_number = 0)
                    )
                }
            }
        }
        assertTrue(board.isBoardCorrect())
    }

    private fun success(
        baseSeed: Long,
        difficulty: SudokuDifficulty,
        observations: MutableList<TargetGenerationObservation>? = null
    ): TargetedPuzzle {
        val result = if (observations == null) {
            generator.generate(baseSeed, difficulty)
        } else {
            generator.generate(baseSeed, difficulty, observations::add)
        }
        assertTrue("Expected success for seed=$baseSeed target=$difficulty: $result", result is TargetGenerationResult.Success)
        return (result as TargetGenerationResult.Success).targetedPuzzle
    }

    private fun assertExactContract(targeted: TargetedPuzzle) {
        val puzzle = targeted.puzzle
        val solution = targeted.solution
        val solveResult = logicalSolver.solve(puzzle)
        val recomputed = grader.grade(puzzle, solveResult)
        val engine = SudokuPuzzleEngine(0L)

        assertEquals(81, puzzle.size)
        assertEquals(81, solution.size)
        assertEquals(targeted.requestedDifficulty, targeted.rating.difficulty)
        assertEquals(targeted.rating, recomputed)
        assertEquals(LogicalSolveStatus.SOLVED, solveResult.status)
        assertEquals(1, engine.countSolutions(puzzle, 2))
        assertArrayEquals(solution, engine.solve(puzzle))
        assertTrue(puzzle.indices.all { puzzle[it] == 0 || puzzle[it] == solution[it] })

        val techniques = targeted.rating.techniqueCounts.filterValues { it > 0 }.keys
        val hasMedium = techniques.any {
            DifficultyGradingV1.tierOf(it) == DifficultyTechniqueTier.MEDIUM
        }
        val hasHard = techniques.any {
            DifficultyGradingV1.tierOf(it) == DifficultyTechniqueTier.HARD
        }
        when (targeted.requestedDifficulty) {
            SudokuDifficulty.EASY -> {
                assertTrue(targeted.rating.totalScore <= 65)
                assertFalse(hasMedium)
                assertFalse(hasHard)
            }
            SudokuDifficulty.MEDIUM -> {
                assertTrue(targeted.rating.totalScore <= 160)
                assertFalse(hasHard)
                assertTrue(hasMedium || targeted.rating.totalScore > 65)
            }
            SudokuDifficulty.HARD -> {
                assertTrue(targeted.rating.totalScore <= 320)
                assertTrue(hasHard)
            }
            SudokuDifficulty.UNSUPPORTED -> error("UNSUPPORTED cannot be returned")
        }
    }

    private fun fixtureGenerator(
        policy: TargetGenerationPolicy,
        fixture: GeneratedPuzzle
    ): DifficultyTargetGenerator = DifficultyTargetGenerator(policy) { attemptSeed ->
        GeneratedPuzzle(fixture.puzzle, fixture.solution, attemptSeed)
    }

    private data class AttemptSeedVector(
        val baseSeed: Long,
        val difficulty: SudokuDifficulty,
        val attemptIndex: Int,
        val expected: Long
    )
}
