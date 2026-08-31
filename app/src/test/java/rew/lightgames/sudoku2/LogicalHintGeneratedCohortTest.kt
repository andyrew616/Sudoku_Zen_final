package rew.lightgames.sudoku2

import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicalHintGeneratedCohortTest {
    @Test
    fun generatedCohort_1000PerDifficulty_isFullyTraversableAndSafe() {
        val requested = listOf(
            SudokuDifficulty.EASY,
            SudokuDifficulty.MEDIUM,
            SudokuDifficulty.HARD
        )
        val techniqueCounts = LongArray(SudokuTechnique.entries.size)
        val nextHintNanos = ArrayList<Long>()
        val mappingNanos = ArrayList<Long>()
        var totalPuzzles = 0
        var totalSteps = 0L
        var generationFailuresSkipped = 0
        var firstHintMismatches = 0
        var noHintFailures = 0
        var mappingInconsistencies = 0
        var incorrectPlacements = 0
        var incorrectEliminations = 0
        var applicationFailures = 0
        var incompleteSolves = 0

        requested.forEach { difficulty ->
            val collected = ArrayList<Sample>()
            var nextSeed = 0L
            while (collected.size < COHORT_SIZE) {
                val remaining = COHORT_SIZE - collected.size
                val seeds = (nextSeed until nextSeed + remaining + SEED_SLACK).toList()
                nextSeed += seeds.size
                val batch = parallelMap(seeds) { seed -> sample(seed, difficulty) }
                generationFailuresSkipped += batch.count { it == null }
                collected.addAll(batch.filterNotNull().take(remaining))
                require(nextSeed <= MAX_SEEDS_PER_DIFFICULTY) {
                    "Could not collect $COHORT_SIZE generated $difficulty puzzles"
                }
            }

            val samples = collected.take(COHORT_SIZE)
            totalPuzzles += samples.size
            val difficultySteps = samples.sumOf { it.steps.toLong() }
            totalSteps += difficultySteps
            samples.forEach { sample ->
                sample.techniqueCounts.indices.forEach { index ->
                    techniqueCounts[index] += sample.techniqueCounts[index].toLong()
                }
                nextHintNanos.addAll(sample.nextHintNanos)
                mappingNanos.addAll(sample.mappingNanos)
                firstHintMismatches += sample.firstHintMismatches
                noHintFailures += sample.noHintFailures
                mappingInconsistencies += sample.mappingInconsistencies
                incorrectPlacements += sample.incorrectPlacements
                incorrectEliminations += sample.incorrectEliminations
                applicationFailures += sample.applicationFailures
                if (!sample.solved) incompleteSolves++
            }
            println(
                "PR11 HINT COHORT $difficulty puzzles=${samples.size} steps=$difficultySteps " +
                    "averageSteps=${decimal(difficultySteps / samples.size.toDouble())}"
            )
        }

        val techniqueReport = SudokuTechnique.entries.joinToString { technique ->
            "$technique=${techniqueCounts[technique.ordinal]}"
        }
        val nextP95 = percentileMillis(nextHintNanos, 0.95)
        val mappingP95 = percentileMillis(mappingNanos, 0.95)
        println("PR11 HINT TECHNIQUES: $techniqueReport")
        println(
            "PR11 HINT FAILURES: firstMismatch=$firstHintMismatches noHint=$noHintFailures " +
                "mapping=$mappingInconsistencies incorrectPlacements=$incorrectPlacements " +
                "incorrectEliminations=$incorrectEliminations application=$applicationFailures " +
                "incomplete=$incompleteSolves skippedGenerationFailures=$generationFailuresSkipped"
        )
        println(
            "PR11 HINT PERFORMANCE: nextAverageMs=${decimal(averageMillis(nextHintNanos))} " +
                "nextP95Ms=${decimal(nextP95)} mappingAverageMs=${decimal(averageMillis(mappingNanos))} " +
                "mappingP95Ms=${decimal(mappingP95)}"
        )

        assertEquals(3_000, totalPuzzles)
        assertTrue(totalSteps > 0)
        assertEquals(0, firstHintMismatches)
        assertEquals(0, noHintFailures)
        assertEquals(0, mappingInconsistencies)
        assertEquals(0, incorrectPlacements)
        assertEquals(0, incorrectEliminations)
        assertEquals(0, applicationFailures)
        assertEquals(0, incompleteSolves)
        assertTrue("Next hint p95 must remain below 50 ms", nextP95 < 50.0)
        assertTrue("Hint mapping p95 must remain below 50 ms", mappingP95 < 50.0)
    }

    private fun sample(seed: Long, difficulty: SudokuDifficulty): Sample? {
        val generated = DifficultyTargetGenerator().generate(seed, difficulty)
        if (generated !is TargetGenerationResult.Success) return null
        val puzzle = generated.targetedPuzzle.puzzle
        val solution = generated.targetedPuzzle.solution
        val creation = CandidateGrid.create(puzzle)
        require(creation is CandidateGridCreationResult.Success)
        val grid = creation.grid
        val solver = SudokuLogicalSolver()
        val mapper = LogicalHintMapper()
        val provider = LogicalHintProvider(solver, mapper)
        val techniqueCounts = IntArray(SudokuTechnique.entries.size)
        val nextHintNanos = ArrayList<Long>()
        val mappingNanos = ArrayList<Long>()
        var firstHintMismatches = 0
        var noHintFailures = 0
        var mappingInconsistencies = 0
        var incorrectPlacements = 0
        var incorrectEliminations = 0
        var applicationFailures = 0
        var steps = 0

        while (grid.snapshot().values.any { it == 0 }) {
            val nextStarted = System.nanoTime()
            val step = solver.nextStep(grid)
            nextHintNanos.add(System.nanoTime() - nextStarted)
            if (step == null) {
                noHintFailures++
                break
            }

            val mappingStarted = System.nanoTime()
            val expectedHint = mapper.map(step, HintDetailLevel.ACTION)
            mappingNanos.add(System.nanoTime() - mappingStarted)
            val result = if (steps == 0) {
                provider.hintFor(puzzle, solution, HintDetailLevel.ACTION)
            } else {
                provider.hintFor(grid, HintDetailLevel.ACTION)
            }
            if (result !is LogicalHintResult.Available) {
                noHintFailures++
                break
            }
            if (result.hint != expectedHint) {
                if (steps == 0) firstHintMismatches++
                mappingInconsistencies++
            }
            mappingInconsistencies += consistencyFailures(step, result.hint)

            step.actions.forEach { action ->
                when (action) {
                    is SolveAction.PlaceValue -> {
                        if (action.digit != solution[action.cell.index]) incorrectPlacements++
                    }
                    is SolveAction.EliminateCandidates -> {
                        if (solution[action.cell.index] in action.digits) incorrectEliminations++
                    }
                }
            }
            if (grid.applyActions(step.actions) != CandidateGridMutationResult.Success) {
                applicationFailures++
                break
            }
            techniqueCounts[step.technique.ordinal]++
            steps++
        }

        return Sample(
            steps = steps,
            techniqueCounts = techniqueCounts,
            nextHintNanos = nextHintNanos,
            mappingNanos = mappingNanos,
            firstHintMismatches = firstHintMismatches,
            noHintFailures = noHintFailures,
            mappingInconsistencies = mappingInconsistencies,
            incorrectPlacements = incorrectPlacements,
            incorrectEliminations = incorrectEliminations,
            applicationFailures = applicationFailures,
            solved = grid.snapshot().values.contentEquals(solution)
        )
    }

    private fun consistencyFailures(step: LogicalStep, hint: LogicalHint): Int {
        var failures = 0
        val actionTargets = step.actions.map { it.cell }.distinct().sorted()
        if (hint.technique != step.technique) failures++
        if (hint.actions != step.actions) failures++
        if (hint.targetCells != actionTargets) failures++
        if (hint.supportingCells != evidenceSupports(step.evidence)) failures++
        if (hint.houses != evidenceHouses(step.evidence)) failures++

        val actionableHighlights = hint.highlights.filter {
            it.role == HintHighlightRole.TARGET || it.role == HintHighlightRole.ELIMINATION
        }
        val expectedActionable = step.actions.flatMap { action ->
            when (action) {
                is SolveAction.PlaceValue -> listOf(
                    HintHighlight(action.cell, HintHighlightRole.TARGET, action.digit)
                )
                is SolveAction.EliminateCandidates -> action.digits.digitsAscending().map { digit ->
                    HintHighlight(action.cell, HintHighlightRole.ELIMINATION, digit)
                }
            }
        }.sortedWith(HIGHLIGHT_COMPARATOR)
        if (actionableHighlights.sortedWith(HIGHLIGHT_COMPARATOR) != expectedActionable) failures++
        return failures
    }

    private fun evidenceSupports(evidence: StepEvidence): List<CellRef> = when (evidence) {
        is StepEvidence.Single -> emptyList()
        is StepEvidence.LockedCandidates -> evidence.sourceCells
        is StepEvidence.Subset -> evidence.cells
        is StepEvidence.Fish -> evidence.cells
        is StepEvidence.XYWing -> (listOf(evidence.pivot) + evidence.pincers).distinct().sorted()
        is StepEvidence.Skyscraper -> (evidence.alignedCells + evidence.towers).distinct().sorted()
        is StepEvidence.TwoStringKite -> listOf(
            evidence.rowConnector,
            evidence.columnConnector,
            evidence.rowOuter,
            evidence.columnOuter
        ).distinct().sorted()
    }

    private fun evidenceHouses(evidence: StepEvidence): List<HouseRef> = when (evidence) {
        is StepEvidence.Single -> listOfNotNull(evidence.uniqueIn)
        is StepEvidence.LockedCandidates -> listOf(evidence.sourceHouse, evidence.targetHouse).sorted()
        is StepEvidence.Subset -> listOf(evidence.house)
        is StepEvidence.Fish -> (evidence.baseHouses + evidence.coverHouses).distinct().sorted()
        is StepEvidence.XYWing -> emptyList()
        is StepEvidence.Skyscraper -> evidence.sourceHouses
        is StepEvidence.TwoStringKite -> listOf(evidence.rowHouse, evidence.columnHouse).sorted()
    }

    private fun <T, R> parallelMap(values: List<T>, transform: (T) -> R): List<R> {
        val threadCount = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
        val executor = Executors.newFixedThreadPool(threadCount)
        return try {
            executor.invokeAll(values.map { value -> Callable { transform(value) } })
                .map { it.get() }
        } finally {
            executor.shutdownNow()
        }
    }

    private fun averageMillis(values: List<Long>): Double =
        if (values.isEmpty()) 0.0 else values.average() / 1_000_000.0

    private fun percentileMillis(values: List<Long>, percentile: Double): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val index = ((sorted.size - 1) * percentile).toInt().coerceIn(sorted.indices)
        return sorted[index] / 1_000_000.0
    }

    private fun decimal(value: Double): String = String.format(Locale.US, "%.3f", value)

    private data class Sample(
        val steps: Int,
        val techniqueCounts: IntArray,
        val nextHintNanos: List<Long>,
        val mappingNanos: List<Long>,
        val firstHintMismatches: Int,
        val noHintFailures: Int,
        val mappingInconsistencies: Int,
        val incorrectPlacements: Int,
        val incorrectEliminations: Int,
        val applicationFailures: Int,
        val solved: Boolean
    )

    private companion object {
        const val COHORT_SIZE = 1_000
        const val SEED_SLACK = 24
        const val MAX_SEEDS_PER_DIFFICULTY = 10_000L
        val HIGHLIGHT_COMPARATOR = compareBy<HintHighlight>(
            { it.cell },
            { it.role.ordinal },
            { it.digit ?: 0 }
        )
    }
}
