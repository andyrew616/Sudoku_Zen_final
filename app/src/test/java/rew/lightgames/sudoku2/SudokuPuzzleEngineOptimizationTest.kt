package rew.lightgames.sudoku2

import java.util.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SudokuPuzzleEngineOptimizationTest {
    private val engine = SudokuPuzzleEngine(0L)

    @Test
    fun countSolutions_stopsAtTheConfiguredLimitForAnAmbiguousBoard() {
        val emptyBoard = IntArray(81)

        assertEquals(1, engine.countSolutions(emptyBoard, 1))
        assertEquals(2, engine.countSolutions(emptyBoard, 2))
        assertEquals(5, engine.countSolutions(emptyBoard, 5))
    }

    @Test
    fun countSolutions_nonPositiveLimitReturnsZeroWithoutMutation() {
        val board = SudokuPuzzleEngine(42L).generate().puzzle
        val original = board.clone()

        assertEquals(0, engine.countSolutions(board, 0))
        assertEquals(0, engine.countSolutions(board, -1))
        assertArrayEquals(original, board)
    }

    @Test
    fun boundedCounter_matchesIndependentReferenceAcrossTenThousandBoards() {
        var boardsChecked = 0
        repeat(100) { solutionSeed ->
            val completed = SudokuPuzzleEngine(solutionSeed.toLong()).generateCompleteGrid(
                Random(solutionSeed.toLong())
            )
            repeat(100) { variant ->
                val order = IntArray(81) { it }
                val rng = Random(solutionSeed * 10_000L + variant)
                shuffle(order, rng)
                val clueCount = 24 + variant % 32
                val board = IntArray(81)
                repeat(clueCount) { offset ->
                    val cell = order[offset]
                    board[cell] = completed[cell]
                }
                val limit = 1 + variant % 3

                assertEquals(
                    "solutionSeed=$solutionSeed variant=$variant limit=$limit",
                    referenceCountSolutions(board, limit),
                    engine.countSolutions(board, limit)
                )
                boardsChecked++
            }
        }

        assertEquals(10_000, boardsChecked)
    }

    @Test
    fun knownSlowHardSeeds_preserveTargetingV1OutcomesAndPuzzles() {
        val expectedSuccesses = listOf(
            SlowSuccess(
                seed = 365L,
                attemptIndex = 11,
                cluesRestored = 0,
                puzzle = "000120350000904000040037010000000600090400070200003001000098000010000098003000067",
                solution = "879126354321954786546837219438719625195462873267583941754698132612375498983241567"
            ),
            SlowSuccess(
                seed = 682L,
                attemptIndex = 17,
                cluesRestored = 1,
                puzzle = "205900000960070000008050001300060102080007009600800300000000040790000500000100203",
                solution = "275981634961473825438652791347569182582317469619824357126735948793248516854196273"
            ),
            SlowSuccess(
                seed = 800L,
                attemptIndex = 18,
                cluesRestored = 0,
                puzzle = "000000000631000080000108605800000406000602100040070030092700000000500020000001300",
                solution = "985426713631957284274138695827319456359642178146875932492783561713564829568291347"
            )
        )

        expectedSuccesses.forEach { expected ->
            val result = DifficultyTargetGenerator().generate(expected.seed, SudokuDifficulty.HARD)
            assertTrue("seed=${expected.seed}: $result", result is TargetGenerationResult.Success)
            val targeted = (result as TargetGenerationResult.Success).targetedPuzzle
            assertEquals(expected.attemptIndex, targeted.attemptIndex)
            assertEquals(expected.cluesRestored, targeted.cluesRestored)
            assertArrayEquals(expected.puzzle.toBoard(), targeted.puzzle)
            assertArrayEquals(expected.solution.toBoard(), targeted.solution)
            assertEquals(SudokuDifficulty.HARD, targeted.rating.difficulty)
        }

        listOf(319L, 476L).forEach { seed ->
            val result = DifficultyTargetGenerator().generate(seed, SudokuDifficulty.HARD)
            assertTrue("seed=$seed: $result", result is TargetGenerationResult.Failure)
            val failure = (result as TargetGenerationResult.Failure).failure
            assertEquals(TargetGenerationFailureReason.ATTEMPT_BUDGET_EXHAUSTED, failure.reason)
            assertEquals(DifficultyTargetingV1.HARD_ATTEMPT_BUDGET, failure.attemptsUsed)
        }
    }

    private fun referenceCountSolutions(board: IntArray, limit: Int): Int {
        if (limit <= 0) return 0
        return try {
            engine.validateBoard(board)
            val count = intArrayOf(0)
            referenceSearch(board.clone(), limit, count)
            count[0]
        } catch (_: IllegalArgumentException) {
            0
        }
    }

    private fun referenceSearch(grid: IntArray, limit: Int, count: IntArray): Boolean {
        val cell = referenceBestEmptyCell(grid)
        if (cell == -1) {
            count[0]++
            return count[0] >= limit
        }
        val row = cell / 9
        val column = cell % 9
        for (digit in 1..9) {
            if (!engine.canPlace(grid, row, column, digit)) continue
            grid[cell] = digit
            if (referenceSearch(grid, limit, count)) {
                grid[cell] = 0
                return true
            }
            grid[cell] = 0
        }
        return false
    }

    private fun referenceBestEmptyCell(board: IntArray): Int {
        var bestCell = -1
        var bestCount = 10
        for (cell in board.indices) {
            if (board[cell] != 0) continue
            val row = cell / 9
            val column = cell % 9
            var candidateCount = 0
            for (digit in 1..9) {
                if (engine.canPlace(board, row, column, digit)) candidateCount++
            }
            if (candidateCount == 0) return cell
            if (candidateCount < bestCount) {
                bestCell = cell
                bestCount = candidateCount
                if (candidateCount == 1) return cell
            }
        }
        return bestCell
    }

    private fun shuffle(values: IntArray, rng: Random) {
        for (index in values.lastIndex downTo 1) {
            val swapIndex = rng.nextInt(index + 1)
            val value = values[index]
            values[index] = values[swapIndex]
            values[swapIndex] = value
        }
    }

    private fun String.toBoard(): IntArray = IntArray(length) { index -> this[index].digitToInt() }

    private data class SlowSuccess(
        val seed: Long,
        val attemptIndex: Int,
        val cluesRestored: Int,
        val puzzle: String,
        val solution: String
    )
}
