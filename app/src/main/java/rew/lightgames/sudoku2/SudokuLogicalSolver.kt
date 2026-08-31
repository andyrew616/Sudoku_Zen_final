package rew.lightgames.sudoku2

import java.util.Collections

enum class LogicalSolveStatus {
    SOLVED,
    STALLED,
    INVALID
}

typealias CandidateSnapshot = CandidateGridSnapshot

/** Immutable result of applying only the supported human-logical techniques. */
class LogicalSolveResult(
    val status: LogicalSolveStatus,
    finalBoard: IntArray,
    val remainingCandidates: CandidateSnapshot,
    steps: Collection<LogicalStep>
) {
    private val finalBoardState: IntArray = finalBoard.clone()

    val finalBoard: IntArray
        get() = finalBoardState.clone()

    val steps: List<LogicalStep> = Collections.unmodifiableList(ArrayList(steps))

    override fun equals(other: Any?): Boolean =
        other is LogicalSolveResult &&
            status == other.status &&
            finalBoardState.contentEquals(other.finalBoardState) &&
            remainingCandidates == other.remainingCandidates &&
            steps == other.steps

    override fun hashCode(): Int {
        var result = status.hashCode()
        result = 31 * result + finalBoardState.contentHashCode()
        result = 31 * result + remainingCandidates.hashCode()
        result = 31 * result + steps.hashCode()
        return result
    }

    override fun toString(): String =
        "LogicalSolveResult(status=$status, finalBoard=${finalBoardState.contentToString()}, " +
            "remainingCandidates=$remainingCandidates, steps=$steps)"
}

/** Deterministic singles-only logical solver. It never guesses, branches, or backtracks. */
class SudokuLogicalSolver {
    fun nextStep(grid: CandidateGrid): LogicalStep? =
        findNakedSingle(grid) ?: findHiddenSingle(grid)

    fun solve(board: IntArray): LogicalSolveResult {
        val creation = CandidateGrid.create(board)
        if (creation is CandidateGridCreationResult.Failure) {
            return invalidResult(board)
        }

        val grid = (creation as CandidateGridCreationResult.Success).grid
        val steps = ArrayList<LogicalStep>()

        // Every supported step places one value, so the loop can succeed at most 81 times.
        repeat(81) {
            if (isSolved(grid)) return result(LogicalSolveStatus.SOLVED, grid, steps)

            val step = nextStep(grid)
                ?: return result(LogicalSolveStatus.STALLED, grid, steps)
            val action = step.actions.single() as SolveAction.PlaceValue
            if (grid.place(action.cell, action.digit) != CandidateGridMutationResult.Success) {
                // A detected forced placement that contradicts the grid proves the state invalid.
                return result(LogicalSolveStatus.INVALID, grid, steps)
            }
            steps.add(step)
        }

        return if (isSolved(grid)) {
            result(LogicalSolveStatus.SOLVED, grid, steps)
        } else {
            // Defensive invariant: 81 successful placements must fill an initially valid grid.
            result(LogicalSolveStatus.INVALID, grid, steps)
        }
    }

    internal fun findNakedSingle(grid: CandidateGrid): LogicalStep? {
        for (cell in grid.cellsRowMajor()) {
            if (grid.valueAt(cell) != 0) continue
            val candidates = grid.candidatesAt(cell)
            if (candidates.size != 1) continue
            val digit = candidates.digitsAscending().single()
            return singleStep(
                technique = SudokuTechnique.NAKED_SINGLE,
                cell = cell,
                digit = digit,
                candidates = candidates,
                uniqueIn = null
            )
        }
        return null
    }

    internal fun findHiddenSingle(grid: CandidateGrid): LogicalStep? {
        for (type in HouseType.entries) {
            for (houseIndex in 0..8) {
                val house = HouseRef(type, houseIndex)
                for (digit in 1..9) {
                    if (grid.cellsIn(house).any { grid.valueAt(it) == digit }) continue
                    val positions = grid.candidatePositions(house, digit)
                    if (positions.size != 1) continue
                    val cell = positions.single()
                    return singleStep(
                        technique = SudokuTechnique.HIDDEN_SINGLE,
                        cell = cell,
                        digit = digit,
                        candidates = grid.candidatesAt(cell),
                        uniqueIn = house
                    )
                }
            }
        }
        return null
    }

    private fun singleStep(
        technique: SudokuTechnique,
        cell: CellRef,
        digit: Int,
        candidates: DigitSet,
        uniqueIn: HouseRef?
    ): LogicalStep = LogicalStep(
        technique = technique,
        actions = listOf(SolveAction.PlaceValue(cell, digit)),
        evidence = StepEvidence.Single(cell, digit, candidates, uniqueIn)
    )

    private fun isSolved(grid: CandidateGrid): Boolean =
        grid.cellsRowMajor().all { grid.valueAt(it) != 0 }

    private fun result(
        status: LogicalSolveStatus,
        grid: CandidateGrid,
        steps: Collection<LogicalStep>
    ): LogicalSolveResult {
        val snapshot = grid.snapshot()
        return LogicalSolveResult(status, snapshot.values, snapshot, steps)
    }

    private fun invalidResult(board: IntArray): LogicalSolveResult = LogicalSolveResult(
        status = LogicalSolveStatus.INVALID,
        finalBoard = board,
        // No authoritative candidate state exists when CandidateGrid creation fails.
        remainingCandidates = CandidateGridSnapshot(IntArray(81), IntArray(81)),
        steps = emptyList()
    )
}
