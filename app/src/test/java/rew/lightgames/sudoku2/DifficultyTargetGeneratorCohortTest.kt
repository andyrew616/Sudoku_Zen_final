package rew.lightgames.sudoku2

import java.util.Locale
import java.util.TreeMap
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.math.ceil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DifficultyTargetGeneratorCohortTest {
    private val requestedDifficulties = listOf(
        SudokuDifficulty.EASY,
        SudokuDifficulty.MEDIUM,
        SudokuDifficulty.HARD
    )

    @Test
    fun correctnessCohort_1000SeedsPerDifficulty_hasNoWrongUniqueOrUnstableSuccess() {
        val cohortSize = 1_000
        val samples = parallelMap(requestedDifficulties.flatMap { difficulty ->
            (0L until cohortSize.toLong()).map { seed -> difficulty to seed }
        }) { (difficulty, seed) -> correctnessSample(seed, difficulty) }

        var wrongGradeResults = 0
        var uniquenessFailures = 0
        var storedSolutionFailures = 0
        var invalidLogicalResults = 0
        var determinismFailures = 0
        var internalFailures = 0

        requestedDifficulties.forEach { difficulty ->
            val targetSamples = samples.filter { it.difficulty == difficulty }
            val successes = targetSamples.filter { it.success }
            val failures = targetSamples.size - successes.size
            wrongGradeResults += targetSamples.count { !it.correctGrade }
            uniquenessFailures += targetSamples.count { !it.unique }
            storedSolutionFailures += targetSamples.count { !it.solutionMatches }
            invalidLogicalResults += targetSamples.count { it.invalidLogicalResult }
            determinismFailures += targetSamples.count { !it.deterministic }
            internalFailures += targetSamples.count { it.internalFailure }

            println(
                "PR8 CORRECTNESS $difficulty requests=${targetSamples.size} " +
                    "successes=${successes.size} failures=$failures " +
                    "successRate=${decimal(successes.size * 100.0 / targetSamples.size)} " +
                    "averageAttemptIndex=${decimal(successes.map { it.attemptIndex }.averageOrZero())} " +
                    "p95AttemptIndex=${percentile(successes.map { it.attemptIndex }, 0.95)} " +
                    "averageRestorations=${decimal(successes.map { it.restorations }.averageOrZero())} " +
                    "p95Restorations=${percentile(successes.map { it.restorations }, 0.95)}"
            )
        }

        println(
            "PR8 CORRECTNESS FAILURES wrongGrade=$wrongGradeResults " +
                "uniqueness=$uniquenessFailures storedSolution=$storedSolutionFailures " +
                "invalidLogical=$invalidLogicalResults determinism=$determinismFailures " +
                "internal=$internalFailures"
        )

        assertEquals(3_000, samples.size)
        assertEquals(0, wrongGradeResults)
        assertEquals(0, uniquenessFailures)
        assertEquals(0, storedSolutionFailures)
        assertEquals(0, invalidLogicalResults)
        assertEquals(0, determinismFailures)
        assertEquals(0, internalFailures)
    }

    @Test
    fun acceptanceCohort_5000RequestsPerDifficulty_reportsV1QualityAndPerformance() {
        val cohortSize = 5_000
        val requests = requestedDifficulties.flatMap { difficulty ->
            (0L until cohortSize.toLong()).map { seed -> difficulty to seed }
        }
        val samples = parallelMap(requests) { (difficulty, seed) ->
            acceptanceSample(seed, difficulty)
        }

        var wrongGradeResults = 0
        var internalFailures = 0
        val puzzleOwners = HashMap<String, SudokuDifficulty>()
        var crossGradePuzzleDuplicates = 0

        requestedDifficulties.forEach { difficulty ->
            val targetSamples = samples.filter { it.difficulty == difficulty }
            val successes = targetSamples.filter { it.success }
            val failures = targetSamples.filterNot { it.success }
            val puzzleKeys = successes.mapNotNull { it.puzzleKey }
            val solutionKeys = successes.mapNotNull { it.solutionKey }
            val duplicatePuzzles = puzzleKeys.size - puzzleKeys.toSet().size
            val duplicateSolutions = solutionKeys.size - solutionKeys.toSet().size
            val attemptDistribution = distribution(successes.map { it.attemptIndex })
            val restorationDistribution = distribution(successes.map { it.restorations })
            val clueCounts = successes.map { it.clueCount }
            val scores = successes.map { it.score }
            val latencies = targetSamples.map { it.latencyNanos }
            val hardestDistribution = buildList {
                SudokuTechnique.entries.forEach { technique ->
                    val count = successes.count { it.hardestTechnique == technique }
                    if (count > 0) add("${technique.name}=$count")
                }
                val none = successes.count { it.hardestTechnique == null }
                if (none > 0) add("NONE=$none")
            }.joinToString()
            val rawGradeDistribution = SudokuDifficulty.entries.joinToString { grade ->
                "$grade=${targetSamples.sumOf { it.rawGradeCounts[grade.ordinal] }}"
            }
            val rawStatusDistribution = LogicalSolveStatus.entries.joinToString { status ->
                "$status=${targetSamples.sumOf { it.rawStatusCounts[status.ordinal] }}"
            }
            val overshoots = targetSamples.sumOf { it.overshoots }
            val limitHits = targetSamples.sumOf { it.restorationLimitHits }
            val restoredSuccesses = successes.count { it.restorations > 0 }
            val attemptExhaustions = failures.count {
                it.failureReason == TargetGenerationFailureReason.ATTEMPT_BUDGET_EXHAUSTED
            }
            val extremeClueCount = when (difficulty) {
                SudokuDifficulty.EASY -> clueCounts.count { it <= 22 }
                SudokuDifficulty.HARD -> clueCounts.count { it >= 35 }
                SudokuDifficulty.MEDIUM -> 0
                SudokuDifficulty.UNSUPPORTED -> error("Not requested")
            }

            successes.forEach { sample ->
                val puzzleKey = requireNotNull(sample.puzzleKey)
                val previous = puzzleOwners.putIfAbsent(puzzleKey, difficulty)
                if (previous != null && previous != difficulty) crossGradePuzzleDuplicates++
            }
            wrongGradeResults += targetSamples.count { it.wrongGrade }
            internalFailures += targetSamples.count { it.internalFailure }

            println(
                "PR8 ACCEPTANCE $difficulty requests=${targetSamples.size} " +
                    "successes=${successes.size} failures=${failures.size} " +
                    "acceptancePercent=${decimal(successes.size * 100.0 / targetSamples.size)}"
            )
            println(
                "PR8 ATTEMPTS $difficulty distribution=$attemptDistribution " +
                    "average=${decimal(successes.map { it.attemptIndex }.averageOrZero())} " +
                    "p95=${percentile(successes.map { it.attemptIndex }, 0.95)} " +
                    "attemptExhaustions=$attemptExhaustions"
            )
            println(
                "PR8 RESTORATIONS $difficulty distribution=$restorationDistribution " +
                    "average=${decimal(successes.map { it.restorations }.averageOrZero())} " +
                    "p95=${percentile(successes.map { it.restorations }, 0.95)} " +
                    "restoredSuccesses=$restoredSuccesses overshoots=$overshoots " +
                    "limitHits=$limitHits evaluations=${targetSamples.sumOf { it.restorationEvaluations }}"
            )
            println(
                "PR8 CLUES $difficulty average=${decimal(clueCounts.averageOrZero())} " +
                    "median=${decimal(median(clueCounts))} p95=${percentile(clueCounts, 0.95)} " +
                    "range=${range(clueCounts)} extremeCount=$extremeClueCount"
            )
            println(
                "PR8 SCORES $difficulty average=${decimal(scores.averageOrZero())} " +
                    "median=${decimal(median(scores))} p95=${percentile(scores, 0.95)} " +
                    "range=${range(scores)}"
            )
            println("PR8 HARDEST $difficulty $hardestDistribution")
            println(
                "PR8 RAW $difficulty grades=[$rawGradeDistribution] " +
                    "statuses=[$rawStatusDistribution]"
            )
            println(
                "PR8 PERFORMANCE $difficulty averageMs=${nanosToDecimalMs(latencies.averageLong())} " +
                    "medianMs=${nanosToDecimalMs(percentileLong(latencies, 0.50))} " +
                    "p95Ms=${nanosToDecimalMs(percentileLong(latencies, 0.95))} " +
                    "slowestMs=${nanosToDecimalMs(latencies.maxOrNull() ?: 0L)}"
            )
            println(
                "PR8 DUPLICATES $difficulty puzzles=$duplicatePuzzles " +
                    "solutions=$duplicateSolutions"
            )

            val minimumAcceptance = when (difficulty) {
                SudokuDifficulty.EASY, SudokuDifficulty.MEDIUM -> 95.0
                SudokuDifficulty.HARD -> 80.0
                SudokuDifficulty.UNSUPPORTED -> error("Not requested")
            }
            assertTrue(
                "$difficulty acceptance fell below the provisional product target",
                successes.size * 100.0 / targetSamples.size >= minimumAcceptance
            )
        }

        val scoreRanges = requestedDifficulties.associateWith { difficulty ->
            samples.filter { it.difficulty == difficulty && it.success }.map { it.score }
        }
        println(
            "PR8 SCORE OVERLAP " + requestedDifficulties.zipWithNext().joinToString { (left, right) ->
                "$left/$right=${overlap(scoreRanges.getValue(left), scoreRanges.getValue(right))}"
            }
        )
        println(
            "PR8 GLOBAL requests=${samples.size} wrongGrade=$wrongGradeResults " +
                "internalFailures=$internalFailures crossGradePuzzleDuplicates=" +
                "$crossGradePuzzleDuplicates"
        )

        assertEquals(15_000, samples.size)
        assertEquals(0, wrongGradeResults)
        assertEquals(0, internalFailures)
        assertEquals(0, crossGradePuzzleDuplicates)
    }

    private fun correctnessSample(
        baseSeed: Long,
        difficulty: SudokuDifficulty
    ): CorrectnessSample {
        val firstObservations = arrayListOf<TargetGenerationObservation>()
        val repeatedObservations = arrayListOf<TargetGenerationObservation>()
        val first = DifficultyTargetGenerator().generate(baseSeed, difficulty, firstObservations::add)
        val repeated = DifficultyTargetGenerator().generate(
            baseSeed,
            difficulty,
            repeatedObservations::add
        )
        val deterministic = first == repeated && firstObservations == repeatedObservations
        if (first is TargetGenerationResult.Failure) {
            return CorrectnessSample(
                difficulty = difficulty,
                success = false,
                correctGrade = true,
                unique = true,
                solutionMatches = true,
                invalidLogicalResult = false,
                deterministic = deterministic,
                internalFailure = first.failure.reason !=
                    TargetGenerationFailureReason.ATTEMPT_BUDGET_EXHAUSTED,
                attemptIndex = -1,
                restorations = 0
            )
        }

        val targeted = (first as TargetGenerationResult.Success).targetedPuzzle
        val puzzle = targeted.puzzle
        val solution = targeted.solution
        val validationEngine = SudokuPuzzleEngine(0L)
        val logicalResult = SudokuLogicalSolver().solve(puzzle)
        val rating = SudokuDifficultyGrader().grade(puzzle, logicalResult)
        return CorrectnessSample(
            difficulty = difficulty,
            success = true,
            correctGrade = targeted.requestedDifficulty == difficulty &&
                targeted.rating.difficulty == difficulty && rating == targeted.rating,
            unique = validationEngine.countSolutions(puzzle, 2) == 1,
            solutionMatches = validationEngine.solve(puzzle)?.contentEquals(solution) == true,
            invalidLogicalResult = logicalResult.status == LogicalSolveStatus.INVALID,
            deterministic = deterministic,
            internalFailure = false,
            attemptIndex = targeted.attemptIndex,
            restorations = targeted.cluesRestored
        )
    }

    private fun acceptanceSample(
        baseSeed: Long,
        difficulty: SudokuDifficulty
    ): AcceptanceSample {
        val observations = arrayListOf<TargetGenerationObservation>()
        val started = System.nanoTime()
        val result = DifficultyTargetGenerator().generate(baseSeed, difficulty, observations::add)
        val latency = System.nanoTime() - started
        val rawGradeCounts = IntArray(SudokuDifficulty.entries.size)
        val rawStatusCounts = IntArray(LogicalSolveStatus.entries.size)
        observations.filter { it.phase == TargetGenerationPhase.RAW }.forEach { observation ->
            rawGradeCounts[observation.rating.difficulty.ordinal]++
            rawStatusCounts[observation.logicalStatus.ordinal]++
        }
        val restorationObservations = observations.filter {
            it.phase == TargetGenerationPhase.RESTORATION
        }
        val overshoots = restorationObservations.count {
            difficultyRank(it.rating.difficulty) < difficultyRank(difficulty)
        }
        val observedLimitHits = restorationObservations.groupBy { it.attemptIndex }.count { (_, values) ->
            values.size == DifficultyTargetingV1.MAX_CLUE_RESTORATIONS &&
                difficultyRank(values.last().rating.difficulty) > difficultyRank(difficulty)
        }

        if (result is TargetGenerationResult.Failure) {
            return AcceptanceSample(
                difficulty = difficulty,
                success = false,
                wrongGrade = false,
                internalFailure = result.failure.reason !=
                    TargetGenerationFailureReason.ATTEMPT_BUDGET_EXHAUSTED,
                attemptIndex = -1,
                restorations = 0,
                clueCount = 0,
                score = 0,
                hardestTechnique = null,
                latencyNanos = latency,
                puzzleKey = null,
                solutionKey = null,
                failureReason = result.failure.reason,
                rawGradeCounts = rawGradeCounts,
                rawStatusCounts = rawStatusCounts,
                restorationEvaluations = restorationObservations.size,
                overshoots = overshoots,
                restorationLimitHits = result.failure.restorationLimitHits
            )
        }

        val targeted = (result as TargetGenerationResult.Success).targetedPuzzle
        return AcceptanceSample(
            difficulty = difficulty,
            success = true,
            wrongGrade = targeted.requestedDifficulty != difficulty ||
                targeted.rating.difficulty != difficulty,
            internalFailure = false,
            attemptIndex = targeted.attemptIndex,
            restorations = targeted.cluesRestored,
            clueCount = targeted.rating.clueCount,
            score = targeted.rating.totalScore,
            hardestTechnique = targeted.rating.hardestTechnique,
            latencyNanos = latency,
            puzzleKey = targeted.puzzle.joinToString(separator = ""),
            solutionKey = targeted.solution.joinToString(separator = ""),
            failureReason = null,
            rawGradeCounts = rawGradeCounts,
            rawStatusCounts = rawStatusCounts,
            restorationEvaluations = restorationObservations.size,
            overshoots = overshoots,
            restorationLimitHits = observedLimitHits
        )
    }

    private fun <Input, Output> parallelMap(
        values: List<Input>,
        operation: (Input) -> Output
    ): List<Output> {
        val workers = Runtime.getRuntime().availableProcessors().coerceIn(1, 8)
        val executor = Executors.newFixedThreadPool(workers)
        return try {
            values.map { value -> executor.submit(Callable { operation(value) }) }.map { it.get() }
        } finally {
            executor.shutdown()
        }
    }

    private fun difficultyRank(difficulty: SudokuDifficulty): Int = when (difficulty) {
        SudokuDifficulty.EASY -> 0
        SudokuDifficulty.MEDIUM -> 1
        SudokuDifficulty.HARD -> 2
        SudokuDifficulty.UNSUPPORTED -> 3
    }

    private fun distribution(values: List<Int>): String {
        val counts = TreeMap<Int, Int>()
        values.forEach { counts[it] = counts.getOrDefault(it, 0) + 1 }
        return counts.entries.joinToString(prefix = "[", postfix = "]") { (value, count) ->
            "$value=$count"
        }
    }

    private fun List<Int>.averageOrZero(): Double = if (isEmpty()) 0.0 else average()

    private fun List<Long>.averageLong(): Long = if (isEmpty()) 0L else sum() / size

    private fun percentile(values: List<Int>, percentile: Double): Int {
        if (values.isEmpty()) return 0
        val sorted = values.sorted()
        return sorted[(ceil(sorted.size * percentile).toInt() - 1).coerceAtLeast(0)]
    }

    private fun percentileLong(values: List<Long>, percentile: Double): Long {
        if (values.isEmpty()) return 0L
        val sorted = values.sorted()
        return sorted[(ceil(sorted.size * percentile).toInt() - 1).coerceAtLeast(0)]
    }

    private fun median(values: List<Int>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[middle - 1] + sorted[middle]) / 2.0
        } else {
            sorted[middle].toDouble()
        }
    }

    private fun range(values: List<Int>): String = if (values.isEmpty()) {
        "n/a"
    } else {
        "${values.minOrNull()}..${values.maxOrNull()}"
    }

    private fun overlap(left: List<Int>, right: List<Int>): String {
        if (left.isEmpty() || right.isEmpty()) return "n/a"
        val start = maxOf(left.minOrNull()!!, right.minOrNull()!!)
        val end = minOf(left.maxOrNull()!!, right.maxOrNull()!!)
        return if (start <= end) "$start..$end" else "none"
    }

    private fun nanosToDecimalMs(nanos: Long): String = decimal(nanos / 1_000_000.0)

    private fun decimal(value: Double): String = String.format(Locale.US, "%.3f", value)

    private data class CorrectnessSample(
        val difficulty: SudokuDifficulty,
        val success: Boolean,
        val correctGrade: Boolean,
        val unique: Boolean,
        val solutionMatches: Boolean,
        val invalidLogicalResult: Boolean,
        val deterministic: Boolean,
        val internalFailure: Boolean,
        val attemptIndex: Int,
        val restorations: Int
    )

    private data class AcceptanceSample(
        val difficulty: SudokuDifficulty,
        val success: Boolean,
        val wrongGrade: Boolean,
        val internalFailure: Boolean,
        val attemptIndex: Int,
        val restorations: Int,
        val clueCount: Int,
        val score: Int,
        val hardestTechnique: SudokuTechnique?,
        val latencyNanos: Long,
        val puzzleKey: String?,
        val solutionKey: String?,
        val failureReason: TargetGenerationFailureReason?,
        val rawGradeCounts: IntArray,
        val rawStatusCounts: IntArray,
        val restorationEvaluations: Int,
        val overshoots: Int,
        val restorationLimitHits: Int
    )
}
