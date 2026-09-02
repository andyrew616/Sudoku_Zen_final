package rew.lightgames.sudoku2

import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.math.ceil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SudokuDifficultyGraderCohortTest {
    private val solver = SudokuLogicalSolver()
    private val grader = SudokuDifficultyGrader()

    @Test
    fun generatedCohort_10000Seeds_calibratesV1BandsAndMeasuresGradingOverhead() {
        val cohortSize = 10_000
        val metrics = SudokuDifficulty.entries.associateWith { GradeMetrics() }
        val gradeTimesNanos = ArrayList<Long>(cohortSize)
        val solveAndGradeTimesNanos = ArrayList<Long>(cohortSize)
        val examples = SudokuDifficulty.entries.associateWith { ArrayList<String>() }
        val thresholdHits = linkedMapOf(65 to 0, 66 to 0, 160 to 0, 161 to 0, 320 to 0, 321 to 0)
        var solved = 0
        var stalled = 0
        var invalid = 0
        var nondeterministicRatings = 0
        var singlesOnlyPromotedToMedium = 0
        var mediumTechniqueRejectedAbove160 = 0
        var hardTechniqueRejectedAbove320 = 0
        var solvedRejectedByScore = 0
        var hardWithOneHardStep = 0
        var hardWithOneHardStepAndAtMostFiveTotalSteps = 0
        var unsupportedOther = 0

        val workers = Runtime.getRuntime().availableProcessors().coerceIn(1, 8)
        val executor = Executors.newFixedThreadPool(workers)
        val seedRatings = try {
            (1L..cohortSize.toLong()).map { seed ->
                executor.submit(Callable { gradeSeed(seed) })
            }.map { it.get() }
        } finally {
            executor.shutdown()
        }

        for (seedRating in seedRatings) {
            val seed = seedRating.seed
            val rating = seedRating.rating
            val status = seedRating.status
            gradeTimesNanos.add(seedRating.gradeNanos)
            solveAndGradeTimesNanos.add(seedRating.solveAndGradeNanos)
            if (!seedRating.deterministic) nondeterministicRatings++
            when (status) {
                LogicalSolveStatus.SOLVED -> solved++
                LogicalSolveStatus.STALLED -> stalled++
                LogicalSolveStatus.INVALID -> invalid++
            }

            metrics.getValue(rating.difficulty).record(rating)
            thresholdHits.computeIfPresent(rating.totalScore) { _, count -> count + 1 }
            if (examples.getValue(rating.difficulty).size < 5) {
                examples.getValue(rating.difficulty).add(
                        "seed=$seed score=${rating.totalScore} clues=${rating.clueCount} " +
                        "steps=${rating.stepCount} hardest=${rating.hardestTechnique} " +
                        "status=$status"
                )
            }

            val techniquesUsed = rating.techniqueCounts.filterValues { it > 0 }.keys
            val hasMedium = techniquesUsed.any {
                DifficultyGradingV1.tierOf(it) == DifficultyTechniqueTier.MEDIUM
            }
            val hasHard = techniquesUsed.any {
                DifficultyGradingV1.tierOf(it) == DifficultyTechniqueTier.HARD
            }
            if (
                status == LogicalSolveStatus.SOLVED &&
                !hasMedium && !hasHard &&
                rating.difficulty == SudokuDifficulty.MEDIUM
            ) {
                singlesOnlyPromotedToMedium++
            }
            if (
                status == LogicalSolveStatus.SOLVED &&
                hasMedium && !hasHard &&
                rating.totalScore > DifficultyGradingV1.MEDIUM_MAX_SCORE
            ) {
                mediumTechniqueRejectedAbove160++
            }
            if (
                status == LogicalSolveStatus.SOLVED &&
                hasHard && rating.totalScore > DifficultyGradingV1.HARD_MAX_SCORE
            ) {
                hardTechniqueRejectedAbove320++
            }
            if (
                status == LogicalSolveStatus.SOLVED &&
                rating.difficulty == SudokuDifficulty.UNSUPPORTED
            ) {
                solvedRejectedByScore++
            }
            if (rating.difficulty == SudokuDifficulty.HARD) {
                val hardSteps = rating.techniqueCounts.entries.sumOf { (technique, count) ->
                    if (DifficultyGradingV1.tierOf(technique) == DifficultyTechniqueTier.HARD) {
                        count
                    } else {
                        0
                    }
                }
                if (hardSteps == 1) {
                    hardWithOneHardStep++
                    if (rating.stepCount <= 5) hardWithOneHardStepAndAtMostFiveTotalSteps++
                }
            }
            if (rating.difficulty == SudokuDifficulty.UNSUPPORTED) {
                val accountedFor = when {
                    status == LogicalSolveStatus.STALLED -> true
                    status == LogicalSolveStatus.INVALID -> true
                    hasHard && rating.totalScore > DifficultyGradingV1.HARD_MAX_SCORE -> true
                    !hasHard && rating.totalScore > DifficultyGradingV1.MEDIUM_MAX_SCORE -> true
                    else -> false
                }
                if (!accountedFor) unsupportedOther++
            }
        }

        println("PR7 DIFFICULTY DISTRIBUTION")
        SudokuDifficulty.entries.forEach { difficulty ->
            val gradeMetrics = metrics.getValue(difficulty)
            println(
                "$difficulty count=${gradeMetrics.count} " +
                    "percent=${decimal(gradeMetrics.count * 100.0 / cohortSize)} " +
                    "medianScore=${decimal(gradeMetrics.medianScore())} " +
                    "p95Score=${gradeMetrics.p95Score()} scoreRange=${gradeMetrics.scoreRange()} " +
                    "averageSteps=${decimal(gradeMetrics.averageSteps())} " +
                    "averageClues=${decimal(gradeMetrics.averageClues())}"
            )
            println("$difficulty HARDEST: ${gradeMetrics.hardestDistribution()}")
            println("$difficulty EXAMPLES: ${examples.getValue(difficulty).joinToString(" | ")}")
        }
        println(
            "PR7 UNSUPPORTED BREAKDOWN: stalled=$stalled invalid=$invalid " +
                "mediumAbove160=$mediumTechniqueRejectedAbove160 " +
                "hardAbove320=$hardTechniqueRejectedAbove320 other=$unsupportedOther"
        )
        println(
            "PR7 CALIBRATION SIGNALS: solved=$solved singlesOnlyPromotedToMedium=" +
                "$singlesOnlyPromotedToMedium solvedRejectedByScore=$solvedRejectedByScore " +
                "hardWithOneHardStep=$hardWithOneHardStep " +
                "hardWithOneHardStepAndAtMostFiveTotalSteps=" +
                "$hardWithOneHardStepAndAtMostFiveTotalSteps thresholdHits=$thresholdHits"
        )

        val gradeMedianMs = percentileNanos(gradeTimesNanos, 0.50) / 1_000_000.0
        val gradeP95Ms = percentileNanos(gradeTimesNanos, 0.95) / 1_000_000.0
        val totalMedianMs = percentileNanos(solveAndGradeTimesNanos, 0.50) / 1_000_000.0
        val totalP95Ms = percentileNanos(solveAndGradeTimesNanos, 0.95) / 1_000_000.0
        println(
            "PR7 PERFORMANCE: gradeMedianMs=${decimal(gradeMedianMs)} " +
                "gradeP95Ms=${decimal(gradeP95Ms)} " +
                "solveAndGradeMedianMs=${decimal(totalMedianMs)} " +
                "solveAndGradeP95Ms=${decimal(totalP95Ms)}"
        )

        assertEquals(cohortSize, SudokuDifficulty.entries.sumOf { metrics.getValue(it).count })
        assertEquals(cohortSize, solved + stalled + invalid)
        assertEquals(0, invalid)
        assertEquals(0, nondeterministicRatings)
        assertEquals(0, unsupportedOther)
        assertTrue("Grading median exceeded 1 ms target", gradeMedianMs <= 1.0)
        assertTrue("Solve + grade p95 exceeded 20 ms budget", totalP95Ms <= 20.0)
    }

    private fun gradeSeed(seed: Long): SeedRating {
        val generated = SudokuPuzzleEngine(seed).generate()
        val solveAndGradeStarted = System.nanoTime()
        val result = solver.solve(generated.puzzle)
        val gradeStarted = System.nanoTime()
        val rating = grader.grade(generated.puzzle, result)
        val gradeNanos = System.nanoTime() - gradeStarted
        val solveAndGradeNanos = System.nanoTime() - solveAndGradeStarted
        return SeedRating(
            seed = seed,
            status = result.status,
            rating = rating,
            gradeNanos = gradeNanos,
            solveAndGradeNanos = solveAndGradeNanos,
            deterministic = rating == grader.grade(generated.puzzle.clone(), result)
        )
    }

    private data class SeedRating(
        val seed: Long,
        val status: LogicalSolveStatus,
        val rating: DifficultyRating,
        val gradeNanos: Long,
        val solveAndGradeNanos: Long,
        val deterministic: Boolean
    )

    private class GradeMetrics {
        private val scores = ArrayList<Int>()
        private val hardestCounts = linkedMapOf<String, Int>()
        private var totalSteps = 0L
        private var totalClues = 0L

        val count: Int
            get() = scores.size

        fun record(rating: DifficultyRating) {
            scores.add(rating.totalScore)
            totalSteps += rating.stepCount
            totalClues += rating.clueCount
            val key = rating.hardestTechnique?.name ?: "NONE"
            hardestCounts[key] = hardestCounts.getOrDefault(key, 0) + 1
        }

        fun medianScore(): Double {
            if (scores.isEmpty()) return 0.0
            val sorted = scores.sorted()
            val middle = sorted.size / 2
            return if (sorted.size % 2 == 0) {
                (sorted[middle - 1] + sorted[middle]) / 2.0
            } else {
                sorted[middle].toDouble()
            }
        }

        fun p95Score(): Int {
            if (scores.isEmpty()) return 0
            val sorted = scores.sorted()
            return sorted[(ceil(sorted.size * 0.95).toInt() - 1).coerceAtLeast(0)]
        }

        fun scoreRange(): String = if (scores.isEmpty()) {
            "n/a"
        } else {
            "${scores.minOrNull()}..${scores.maxOrNull()}"
        }

        fun averageSteps(): Double = if (count == 0) 0.0 else totalSteps / count.toDouble()

        fun averageClues(): Double = if (count == 0) 0.0 else totalClues / count.toDouble()

        fun hardestDistribution(): String = buildList {
            if (hardestCounts.containsKey("NONE")) {
                add("NONE=${hardestCounts.getValue("NONE")}")
            }
            SudokuTechnique.entries.forEach { technique ->
                hardestCounts[technique.name]?.let { add("${technique.name}=$it") }
            }
        }.joinToString()
    }

    private fun percentileNanos(values: List<Long>, percentile: Double): Long {
        val sorted = values.sorted()
        val index = (ceil(sorted.size * percentile).toInt() - 1).coerceAtLeast(0)
        return sorted[index]
    }

    private fun decimal(value: Double): String = String.format(Locale.US, "%.3f", value)
}
