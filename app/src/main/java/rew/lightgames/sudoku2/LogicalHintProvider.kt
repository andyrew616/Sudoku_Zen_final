package rew.lightgames.sudoku2

/**
 * Builds hints from the player's placed values. Notes are deliberately absent from this API.
 * The solution is used only to reject incorrect entries before logical deduction begins.
 */
class LogicalHintProvider(
    private val solver: SudokuLogicalSolver = SudokuLogicalSolver(),
    private val mapper: LogicalHintMapper = LogicalHintMapper()
) {
    fun hintFor(
        playerValues: IntArray,
        authoritativeSolution: IntArray,
        detailLevel: HintDetailLevel
    ): LogicalHintResult {
        if (!isValidSolution(authoritativeSolution)) {
            return LogicalHintResult.INVALID_PLAYER_STATE
        }
        if (playerValues.size != BOARD_CELL_COUNT || playerValues.any { it !in 0..9 }) {
            return LogicalHintResult.INVALID_PLAYER_STATE
        }
        if (playerValues.indices.any { index ->
                playerValues[index] != 0 && playerValues[index] != authoritativeSolution[index]
            }
        ) {
            return LogicalHintResult.INCORRECT_VALUE_PRESENT
        }

        val creation = CandidateGrid.create(playerValues)
        if (creation !is CandidateGridCreationResult.Success) {
            return LogicalHintResult.INVALID_PLAYER_STATE
        }
        return hintFor(creation.grid, detailLevel)
    }

    /** Maps the next step from an already-authoritative logical state, primarily for simulations. */
    internal fun hintFor(grid: CandidateGrid, detailLevel: HintDetailLevel): LogicalHintResult {
        if (grid.snapshot().values.none { it == 0 }) return LogicalHintResult.SOLVED
        val step = solver.nextStep(grid)
            ?: return LogicalHintResult.NO_SUPPORTED_LOGICAL_HINT
        return LogicalHintResult.Available(mapper.map(step, detailLevel))
    }

    private fun isValidSolution(solution: IntArray): Boolean {
        if (solution.size != BOARD_CELL_COUNT || solution.any { it !in 1..9 }) return false
        return CandidateGrid.create(solution) is CandidateGridCreationResult.Success
    }

    private companion object {
        const val BOARD_CELL_COUNT = 81
    }
}
