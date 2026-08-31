package rew.lightgames.sudoku2

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SudokuLogicalSolverCohortTest {
    private val solver = SudokuLogicalSolver()

    @Test
    fun generatedCohort_500Seeds_preservesOracleAndDeterminism() {
        val techniqueCounts = linkedMapOf<SudokuTechnique, Int>()
        val solveTimesNanos = ArrayList<Long>()
        var solved = 0
        var stalled = 0
        var invalid = 0
        var totalSteps = 0
        var incorrectPlacements = 0
        var incorrectEliminations = 0
        var nondeterministicTraces = 0

        for (seed in 1L..500L) {
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
        }

        val sortedMillis = solveTimesNanos.map { it / 1_000_000.0 }.sorted()
        val averageMillis = sortedMillis.average()
        val medianMillis = sortedMillis[sortedMillis.size / 2]
        val p95Millis = sortedMillis[(sortedMillis.size * 0.95).toInt().coerceAtMost(499)]
        val slowestMillis = sortedMillis.last()
        val averageSteps = totalSteps / 500.0
        val supportedTechniques = SudokuTechnique.entries.takeWhile { it != SudokuTechnique.X_WING }
        val frequency = supportedTechniques.joinToString { technique ->
            "$technique=${techniqueCounts.getOrDefault(technique, 0)}"
        }

        println(
            "PR3 COHORT REPORT: solved=$solved stalled=$stalled invalid=$invalid " +
                "totalSteps=$totalSteps incorrectPlacements=$incorrectPlacements " +
                "incorrectEliminations=$incorrectEliminations nondeterministic=$nondeterministicTraces"
        )
        println("PR3 TECHNIQUE FREQUENCY: $frequency")
        println(
            "PR3 PERFORMANCE: averageMs=${"%.3f".format(averageMillis)} " +
                "medianMs=${"%.3f".format(medianMillis)} p95Ms=${"%.3f".format(p95Millis)} " +
                "slowestMs=${"%.3f".format(slowestMillis)} averageSteps=${"%.2f".format(averageSteps)}"
        )

        assertEquals(500, solved + stalled)
        assertEquals(0, invalid)
        assertEquals(0, incorrectPlacements)
        assertEquals(0, incorrectEliminations)
        assertEquals(0, nondeterministicTraces)
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
            else -> throw AssertionError("Unsupported PR3 evidence: $evidence")
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
}
