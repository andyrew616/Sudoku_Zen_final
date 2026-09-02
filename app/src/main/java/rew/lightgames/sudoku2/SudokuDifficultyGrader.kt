package rew.lightgames.sudoku2

import java.util.Collections
import java.util.LinkedHashMap

enum class SudokuDifficulty {
    EASY,
    MEDIUM,
    HARD,
    UNSUPPORTED
}

/**
 * Version of the deterministic logical-solving contract consumed by difficulty grading.
 *
 * Increment this when the supported technique set, a technique definition, or technique
 * precedence changes. This version is deliberately independent of the Android app version.
 */
object LogicalSolverVersion {
    const val CURRENT = 1
}

/**
 * Version of the published difficulty-grading contract.
 *
 * Increment this after v1 ships whenever a weight, scoring formula, score threshold, or
 * classification rule changes. This version is deliberately independent of the Android app
 * version.
 */
object DifficultyGraderVersion {
    const val CURRENT = 1
}

@ConsistentCopyVisibility
data class DifficultyRating private constructor(
    val difficulty: SudokuDifficulty,
    val totalScore: Int,
    val hardestTechnique: SudokuTechnique?,
    val stepCount: Int,
    val placements: Int,
    val candidateEliminations: Int,
    val techniqueCounts: Map<SudokuTechnique, Int>,
    val clueCount: Int,
    val graderVersion: Int,
    val logicalSolverVersion: Int
) {
    init {
        require(totalScore >= 0) { "Difficulty score must not be negative" }
        require(stepCount >= 0) { "Step count must not be negative" }
        require(placements >= 0) { "Placement count must not be negative" }
        require(candidateEliminations >= 0) {
            "Candidate-elimination count must not be negative"
        }
        require(clueCount in 0..81) { "Clue count must be in 0..81" }
        require(graderVersion > 0) { "Grader version must be positive" }
        require(logicalSolverVersion > 0) { "Logical-solver version must be positive" }
        require(techniqueCounts is CanonicalTechniqueCountMap) {
            "Technique counts must use the immutable canonical representation"
        }
        require(techniqueCounts.keys.toList() == SudokuTechnique.entries) {
            "Technique counts must contain every technique in canonical order"
        }
        require(techniqueCounts.values.all { it >= 0 }) {
            "Technique counts must not be negative"
        }
        require(techniqueCounts.values.sum() == stepCount) {
            "Technique counts must sum to the step count"
        }
        require(hardestTechnique == DifficultyGradingV1.hardestTechnique(techniqueCounts)) {
            "Hardest technique must agree with technique counts"
        }
    }

    internal companion object {
        fun create(
            difficulty: SudokuDifficulty,
            totalScore: Int,
            hardestTechnique: SudokuTechnique?,
            stepCount: Int,
            placements: Int,
            candidateEliminations: Int,
            techniqueCounts: IntArray,
            clueCount: Int
        ): DifficultyRating = DifficultyRating(
            difficulty = difficulty,
            totalScore = totalScore,
            hardestTechnique = hardestTechnique,
            stepCount = stepCount,
            placements = placements,
            candidateEliminations = candidateEliminations,
            techniqueCounts = CanonicalTechniqueCountMap(techniqueCounts),
            clueCount = clueCount,
            graderVersion = DifficultyGraderVersion.CURRENT,
            logicalSolverVersion = LogicalSolverVersion.CURRENT
        )
    }
}

/** Pure, deterministic grading of an existing logical-solver trace. */
class SudokuDifficultyGrader {
    fun grade(puzzle: IntArray, solveResult: LogicalSolveResult): DifficultyRating {
        require(puzzle.size == BOARD_CELL_COUNT) {
            "Puzzle must have exactly $BOARD_CELL_COUNT cells, got ${puzzle.size}"
        }

        validateTraceShape(puzzle, solveResult)

        val techniqueCounts = IntArray(SudokuTechnique.entries.size)
        var totalScore = 0
        var placements = 0
        var candidateEliminations = 0

        for (step in solveResult.steps) {
            val techniqueIndex = step.technique.ordinal
            totalScore = Math.addExact(
                totalScore,
                DifficultyGradingV1.stepScore(step, techniqueCounts[techniqueIndex])
            )
            for (action in step.actions) {
                when (action) {
                    is SolveAction.PlaceValue -> placements = Math.addExact(placements, 1)
                    is SolveAction.EliminateCandidates -> candidateEliminations = Math.addExact(
                        candidateEliminations,
                        action.digits.size
                    )
                }
            }
            techniqueCounts[techniqueIndex] = Math.addExact(techniqueCounts[techniqueIndex], 1)
        }

        val canonicalCounts = SudokuTechnique.entries.associateWith { technique ->
            techniqueCounts[technique.ordinal]
        }
        val hardestTechnique = DifficultyGradingV1.hardestTechnique(canonicalCounts)
        val hasMediumTechnique = SudokuTechnique.entries.any { technique ->
            techniqueCounts[technique.ordinal] > 0 &&
                DifficultyGradingV1.tierOf(technique) == DifficultyTechniqueTier.MEDIUM
        }
        val hasHardTechnique = SudokuTechnique.entries.any { technique ->
            techniqueCounts[technique.ordinal] > 0 &&
                DifficultyGradingV1.tierOf(technique) == DifficultyTechniqueTier.HARD
        }

        return DifficultyRating.create(
            difficulty = DifficultyGradingV1.classify(
                status = solveResult.status,
                totalScore = totalScore,
                hasMediumTechnique = hasMediumTechnique,
                hasHardTechnique = hasHardTechnique
            ),
            totalScore = totalScore,
            hardestTechnique = hardestTechnique,
            stepCount = solveResult.steps.size,
            placements = placements,
            candidateEliminations = candidateEliminations,
            techniqueCounts = techniqueCounts,
            clueCount = puzzle.count { it != 0 }
        )
    }

    private fun validateTraceShape(puzzle: IntArray, solveResult: LogicalSolveResult) {
        solveResult.steps.forEachIndexed { index, step -> validateStepShape(index, step) }

        val creation = CandidateGrid.create(puzzle)
        if (creation is CandidateGridCreationResult.Failure) {
            require(solveResult.status == LogicalSolveStatus.INVALID) {
                "An invalid input puzzle requires an INVALID solve result"
            }
            require(solveResult.steps.isEmpty()) {
                "An invalid input puzzle cannot have a logical trace"
            }
            return
        }
        val replayGrid = (creation as CandidateGridCreationResult.Success).grid

        val finalBoard = solveResult.finalBoard
        val remainingValues = solveResult.remainingCandidates.values
        val remainingMasks = solveResult.remainingCandidates.candidateMasks
        require(finalBoard.size == BOARD_CELL_COUNT) {
            "Final board must have exactly $BOARD_CELL_COUNT cells"
        }
        require(remainingValues.size == BOARD_CELL_COUNT && remainingMasks.size == BOARD_CELL_COUNT) {
            "Remaining-candidate snapshot must have exactly $BOARD_CELL_COUNT cells"
        }
        require(finalBoard.contentEquals(remainingValues)) {
            "Final board must agree with the remaining-candidate snapshot"
        }
        require(finalBoard.all { it in 0..9 }) { "Final board contains an invalid value" }
        require(puzzle.indices.all { puzzle[it] == 0 || puzzle[it] == finalBoard[it] }) {
            "Final board must preserve every original clue"
        }

        solveResult.steps.forEachIndexed { index, step ->
            step.actions.filterIsInstance<SolveAction.EliminateCandidates>().forEach { action ->
                require(action.digits.digitsAscending().all { digit ->
                    digit in replayGrid.candidatesAt(action.cell)
                }) {
                    "Logical step $index requests a candidate that is not present"
                }
            }
            val mutation = replayGrid.applyActions(step.actions)
            require(mutation == CandidateGridMutationResult.Success) {
                "Logical step $index cannot be replayed: $mutation"
            }
        }
        require(replayGrid.snapshot() == solveResult.remainingCandidates) {
            "Logical trace must reproduce the remaining-candidate snapshot"
        }

        when (solveResult.status) {
            LogicalSolveStatus.SOLVED -> {
                require(finalBoard.all { it in 1..9 }) {
                    "A SOLVED result must contain a complete final board"
                }
                require(CandidateGrid.create(finalBoard) is CandidateGridCreationResult.Success) {
                    "A SOLVED result must contain a valid final board"
                }
                require(remainingMasks.all { it == 0 }) {
                    "A SOLVED result must not retain candidates"
                }
            }
            LogicalSolveStatus.STALLED -> require(finalBoard.any { it == 0 }) {
                "A STALLED result must contain an incomplete final board"
            }
            LogicalSolveStatus.INVALID -> Unit
        }
    }

    private fun validateStepShape(index: Int, step: LogicalStep) {
        val shapeIsValid = when (step.technique) {
            SudokuTechnique.NAKED_SINGLE ->
                step.actions.singleOrNull() is SolveAction.PlaceValue &&
                    step.evidence is StepEvidence.Single &&
                    step.evidence.uniqueIn == null
            SudokuTechnique.HIDDEN_SINGLE ->
                step.actions.singleOrNull() is SolveAction.PlaceValue &&
                    step.evidence is StepEvidence.Single &&
                    step.evidence.uniqueIn != null
            SudokuTechnique.LOCKED_CANDIDATES_POINTING,
            SudokuTechnique.LOCKED_CANDIDATES_CLAIMING ->
                step.actions.all { it is SolveAction.EliminateCandidates } &&
                    step.evidence is StepEvidence.LockedCandidates
            SudokuTechnique.NAKED_PAIR ->
                isSubsetStep(step, expectedSize = 2, expectedHidden = false)
            SudokuTechnique.HIDDEN_PAIR ->
                isSubsetStep(step, expectedSize = 2, expectedHidden = true)
            SudokuTechnique.NAKED_TRIPLE ->
                isSubsetStep(step, expectedSize = 3, expectedHidden = false)
            SudokuTechnique.HIDDEN_TRIPLE ->
                isSubsetStep(step, expectedSize = 3, expectedHidden = true)
            SudokuTechnique.X_WING ->
                step.actions.all { it is SolveAction.EliminateCandidates } &&
                    step.evidence is StepEvidence.Fish
            SudokuTechnique.XY_WING ->
                step.actions.all { it is SolveAction.EliminateCandidates } &&
                    step.evidence is StepEvidence.XYWing
            SudokuTechnique.SKYSCRAPER ->
                step.actions.all { it is SolveAction.EliminateCandidates } &&
                    step.evidence is StepEvidence.Skyscraper
            SudokuTechnique.TWO_STRING_KITE ->
                step.actions.all { it is SolveAction.EliminateCandidates } &&
                    step.evidence is StepEvidence.TwoStringKite
        }
        require(shapeIsValid) {
            "Logical step $index has actions or evidence inconsistent with ${step.technique}"
        }
    }

    private fun isSubsetStep(
        step: LogicalStep,
        expectedSize: Int,
        expectedHidden: Boolean
    ): Boolean {
        val evidence = step.evidence as? StepEvidence.Subset ?: return false
        return step.actions.all { it is SolveAction.EliminateCandidates } &&
            evidence.digits.size == expectedSize &&
            evidence.hidden == expectedHidden
    }

    private companion object {
        const val BOARD_CELL_COUNT = 81
    }
}

internal enum class DifficultyTechniqueTier {
    EASY,
    MEDIUM,
    HARD
}

/** The centralized, version-1 scoring and classification definition. */
internal object DifficultyGradingV1 {
    // Product-specific v1 calibration constants, not universal measures of human difficulty.
    const val EASY_MAX_SCORE = 65
    const val MEDIUM_MAX_SCORE = 160
    const val HARD_MAX_SCORE = 320

    private val techniqueWeights = canonicalTechniqueMap(
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

    private val techniqueTiers = canonicalTechniqueMap(
        SudokuTechnique.NAKED_SINGLE to DifficultyTechniqueTier.EASY,
        SudokuTechnique.HIDDEN_SINGLE to DifficultyTechniqueTier.EASY,
        SudokuTechnique.LOCKED_CANDIDATES_POINTING to DifficultyTechniqueTier.MEDIUM,
        SudokuTechnique.LOCKED_CANDIDATES_CLAIMING to DifficultyTechniqueTier.MEDIUM,
        SudokuTechnique.NAKED_PAIR to DifficultyTechniqueTier.MEDIUM,
        SudokuTechnique.HIDDEN_PAIR to DifficultyTechniqueTier.MEDIUM,
        SudokuTechnique.NAKED_TRIPLE to DifficultyTechniqueTier.HARD,
        SudokuTechnique.HIDDEN_TRIPLE to DifficultyTechniqueTier.HARD,
        SudokuTechnique.X_WING to DifficultyTechniqueTier.HARD,
        SudokuTechnique.XY_WING to DifficultyTechniqueTier.HARD,
        SudokuTechnique.SKYSCRAPER to DifficultyTechniqueTier.HARD,
        SudokuTechnique.TWO_STRING_KITE to DifficultyTechniqueTier.HARD
    )

    init {
        require(techniqueWeights.keys.toList() == SudokuTechnique.entries) {
            "Every logical technique must have exactly one canonical v1 weight"
        }
        require(techniqueTiers.keys.toList() == SudokuTechnique.entries) {
            "Every logical technique must have exactly one canonical v1 tier"
        }
    }

    fun baseWeight(technique: SudokuTechnique): Int = techniqueWeights.getValue(technique)

    fun tierOf(technique: SudokuTechnique): DifficultyTechniqueTier =
        techniqueTiers.getValue(technique)

    fun stepScore(step: LogicalStep, previousUsesOfSameTechnique: Int): Int {
        require(previousUsesOfSameTechnique >= 0) {
            "Previous technique uses must not be negative"
        }
        val baseWeight = baseWeight(step.technique)
        val candidatesRemoved = step.actions.sumOf { action ->
            when (action) {
                is SolveAction.PlaceValue -> 0
                is SolveAction.EliminateCandidates -> action.digits.size
            }
        }
        val eliminationBreadth = if (candidatesRemoved == 0) {
            0
        } else {
            (candidatesRemoved - 1).coerceIn(0, 3)
        }
        val repetitionSurcharge = if (baseWeight < 6) {
            0
        } else {
            baseWeight * previousUsesOfSameTechnique.coerceAtMost(3) / 4
        }
        return Math.addExact(Math.addExact(baseWeight, eliminationBreadth), repetitionSurcharge)
    }

    fun classify(
        status: LogicalSolveStatus,
        totalScore: Int,
        hasMediumTechnique: Boolean,
        hasHardTechnique: Boolean
    ): SudokuDifficulty {
        require(totalScore >= 0) { "Difficulty score must not be negative" }
        if (status != LogicalSolveStatus.SOLVED) return SudokuDifficulty.UNSUPPORTED
        if (hasHardTechnique) {
            return if (totalScore <= HARD_MAX_SCORE) {
                SudokuDifficulty.HARD
            } else {
                SudokuDifficulty.UNSUPPORTED
            }
        }
        if (totalScore > MEDIUM_MAX_SCORE) return SudokuDifficulty.UNSUPPORTED
        return if (hasMediumTechnique || totalScore > EASY_MAX_SCORE) {
            SudokuDifficulty.MEDIUM
        } else {
            SudokuDifficulty.EASY
        }
    }

    /** Highest tier, then highest weight, then canonical solver order as a deterministic tie-break. */
    fun hardestTechnique(techniqueCounts: Map<SudokuTechnique, Int>): SudokuTechnique? =
        SudokuTechnique.entries
            .filter { techniqueCounts.getOrDefault(it, 0) > 0 }
            .maxWithOrNull(
                compareBy<SudokuTechnique>(
                    { tierOf(it).ordinal },
                    { baseWeight(it) },
                    { it.ordinal }
                )
            )

    private fun <V> canonicalTechniqueMap(
        vararg entries: Pair<SudokuTechnique, V>
    ): Map<SudokuTechnique, V> {
        val supplied = entries.toMap()
        require(supplied.size == SudokuTechnique.entries.size) {
            "Every logical technique must be supplied exactly once"
        }
        val canonical = LinkedHashMap<SudokuTechnique, V>()
        SudokuTechnique.entries.forEach { technique ->
            canonical[technique] = supplied.getValue(technique)
        }
        return Collections.unmodifiableMap(canonical)
    }
}

private class CanonicalTechniqueCountMap private constructor(
    private val delegate: Map<SudokuTechnique, Int>
) : Map<SudokuTechnique, Int> by delegate {
    constructor(counts: IntArray) : this(
        Collections.unmodifiableMap(
            LinkedHashMap<SudokuTechnique, Int>().apply {
                require(counts.size == SudokuTechnique.entries.size) {
                    "Technique-count array must match the technique set"
                }
                SudokuTechnique.entries.forEach { technique ->
                    put(technique, counts[technique.ordinal])
                }
            }
        )
    )

    override fun equals(other: Any?): Boolean = delegate == other

    override fun hashCode(): Int = delegate.hashCode()

    override fun toString(): String = delegate.toString()
}
