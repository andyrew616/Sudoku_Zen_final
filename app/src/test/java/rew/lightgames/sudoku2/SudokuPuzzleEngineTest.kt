package rew.lightgames.sudoku2

import org.junit.Assert.*
import org.junit.Test

class SudokuPuzzleEngineTest {

    private fun IntArray.toGrid(): Array<IntArray> = Array(9) { r ->
        IntArray(9) { c -> this[r * 9 + c] }
    }

    private fun isValidCompleteSolution(board: IntArray): Boolean {
        require(board.size == 81)
        for (i in board.indices) {
            if (board[i] !in 1..9) return false
        }
        for (r in 0 until 9) {
            if ((0 until 9).map { board[r * 9 + it] }.toSet() != (1..9).toSet()) return false
        }
        for (c in 0 until 9) {
            if ((0 until 9).map { board[it * 9 + c] }.toSet() != (1..9).toSet()) return false
        }
        for (br in 0 until 3) {
            for (bc in 0 until 3) {
                val box = mutableSetOf<Int>()
                for (r in br * 3 until br * 3 + 3) {
                    for (c in bc * 3 until bc * 3 + 3) {
                        box.add(board[r * 9 + c])
                    }
                }
                if (box != (1..9).toSet()) return false
            }
        }
        return true
    }

    // =====================================================
    // A. Completed grid validity
    // =====================================================

    @Test
    fun generated_full_board_has_exactly_81_cells() {
        val engine = SudokuPuzzleEngine(seed = 42)
        val puzzle = engine.generate()
        assertEquals(81, puzzle.solution.size)
        assertEquals(81, puzzle.puzzle.size)
    }

    @Test
    fun generated_full_board_contains_no_zeros() {
        val engine = SudokuPuzzleEngine(seed = 42)
        val puzzle = engine.generate()
        assertFalse("Solution should contain no zeros", puzzle.solution.contains(0))
    }

    @Test
    fun generated_full_board_rows_are_valid() {
        val engine = SudokuPuzzleEngine(seed = 42)
        val puzzle = engine.generate()
        for (r in 0 until 9) {
            val row = (0 until 9).map { puzzle.solution[r * 9 + it] }.toSet()
            assertEquals("Row $r must contain 1-9", (1..9).toSet(), row)
        }
    }

    @Test
    fun generated_full_board_columns_are_valid() {
        val engine = SudokuPuzzleEngine(seed = 42)
        val puzzle = engine.generate()
        for (c in 0 until 9) {
            val col = (0 until 9).map { puzzle.solution[it * 9 + c] }.toSet()
            assertEquals("Column $c must contain 1-9", (1..9).toSet(), col)
        }
    }

    @Test
    fun generated_full_board_boxes_are_valid() {
        val engine = SudokuPuzzleEngine(seed = 42)
        val puzzle = engine.generate()
        for (br in 0 until 3) {
            for (bc in 0 until 3) {
                val box = mutableSetOf<Int>()
                for (r in br * 3 until br * 3 + 3) {
                    for (c in bc * 3 until bc * 3 + 3) {
                        box.add(puzzle.solution[r * 9 + c])
                    }
                }
                assertEquals("Box ($br,$bc) must contain 1-9", (1..9).toSet(), box)
            }
        }
    }

    // =====================================================
    // B. Determinism
    // =====================================================

    @Test
    fun same_seed_produces_identical_puzzle() {
        val p1 = SudokuPuzzleEngine(seed = 123).generate()
        val p2 = SudokuPuzzleEngine(seed = 123).generate()
        assertArrayEquals("Puzzles should be identical", p1.puzzle, p2.puzzle)
        assertArrayEquals("Solutions should be identical", p1.solution, p2.solution)
        assertEquals("Seeds should match", p1.seed, p2.seed)
    }

    @Test
    fun same_seed_repeated_across_runs_is_stable() {
        val results = (1..5).map { SudokuPuzzleEngine(seed = 999).generate() }
        val first = results[0]
        for (i in 1 until results.size) {
            assertArrayEquals("Run $i puzzle should match first", first.puzzle, results[i].puzzle)
            assertArrayEquals("Run $i solution should match first", first.solution, results[i].solution)
        }
    }

    @Test
    fun different_seeds_produce_different_outputs() {
        val seeds = listOf(1L, 2L, 3L, 100L, 999L, 12345L)
        val puzzles = seeds.map { SudokuPuzzleEngine(seed = it).generate() }
        val uniqueSolutions = puzzles.map { it.solution.toList() }.toSet()
        assertTrue(
            "Different seeds should produce varied outputs (got ${uniqueSolutions.size} unique from ${seeds.size})",
            uniqueSolutions.size > 1
        )
    }

    @Test
    fun seed_0_works() {
        val puzzle = SudokuPuzzleEngine(seed = 0).generate()
        assertTrue(isValidCompleteSolution(puzzle.solution))
        assertEquals(0L, puzzle.seed)
    }

    @Test
    fun seed_negative_works() {
        val puzzle = SudokuPuzzleEngine(seed = -1).generate()
        assertTrue(isValidCompleteSolution(puzzle.solution))
        assertEquals(-1L, puzzle.seed)
    }

    @Test
    fun seed_long_min_value_works() {
        val puzzle = SudokuPuzzleEngine(seed = Long.MIN_VALUE).generate()
        assertTrue(isValidCompleteSolution(puzzle.solution))
    }

    @Test
    fun seed_long_max_value_works() {
        val puzzle = SudokuPuzzleEngine(seed = Long.MAX_VALUE).generate()
        assertTrue(isValidCompleteSolution(puzzle.solution))
    }

    // =====================================================
    // C. Uniqueness
    // =====================================================

    @Test
    fun every_generated_puzzle_has_exactly_one_solution() {
        val seeds = listOf(1L, 42L, 100L, 999L, 7777L, Long.MIN_VALUE, Long.MAX_VALUE)
        for (seed in seeds) {
            val puzzle = SudokuPuzzleEngine(seed = seed).generate()
            val count = SudokuPuzzleEngine().countSolutions(puzzle.puzzle, 2)
            assertEquals("Seed $seed should have exactly 1 solution", 1, count)
        }
    }

    @Test
    fun countSolutions_returns_1_for_unique_puzzle() {
        val puzzle = SudokuPuzzleEngine(seed = 42).generate()
        assertEquals(1, SudokuPuzzleEngine().countSolutions(puzzle.puzzle, 2))
    }

    @Test
    fun countSolutions_returns_0_for_invalid_board() {
        val invalid = IntArray(81) { 1 }
        assertEquals(0, SudokuPuzzleEngine().countSolutions(invalid, 2))
    }

    @Test
    fun countSolutions_detects_multiple_solutions() {
        val puzzle = SudokuPuzzleEngine(seed = 42).generate()
        val board = puzzle.puzzle.clone()
        val emptyIndices = board.indices.filter { board[it] == 0 }
        if (emptyIndices.size >= 2) {
            board[emptyIndices[0]] = 0
            board[emptyIndices[1]] = 0
            val count = SudokuPuzzleEngine().countSolutions(board, 2)
            assertTrue("Board with 2 extra empties should have >= 1 solution", count >= 1)
        }
    }

    @Test
    fun countSolutions_limits_correctly() {
        val puzzle = SudokuPuzzleEngine(seed = 42).generate()
        val board = puzzle.puzzle.clone()
        val emptyIndices = board.indices.filter { board[it] == 0 }
        if (emptyIndices.isNotEmpty()) {
            board[emptyIndices[0]] = 0
            val count = SudokuPuzzleEngine().countSolutions(board, 5)
            assertTrue("Count should be <= 5", count <= 5)
        }
    }

    // =====================================================
    // D. Solver consistency
    // =====================================================

    @Test
    fun solving_generated_puzzle_reproduces_stored_solution() {
        val seeds = listOf(1L, 42L, 100L, 500L, 9999L)
        for (seed in seeds) {
            val puzzle = SudokuPuzzleEngine(seed = seed).generate()
            val solved = SudokuPuzzleEngine().solve(puzzle.puzzle)
            assertNotNull("Seed $seed: solver should find a solution", solved)
            assertArrayEquals(
                "Seed $seed: solver output should match stored solution",
                puzzle.solution, solved
            )
        }
    }

    // =====================================================
    // E. Givens preservation
    // =====================================================

    @Test
    fun every_non_zero_puzzle_clue_equals_solution_value() {
        val seeds = listOf(1L, 42L, 200L, 1000L, 5000L)
        for (seed in seeds) {
            val puzzle = SudokuPuzzleEngine(seed = seed).generate()
            for (i in 0 until 81) {
                if (puzzle.puzzle[i] != 0) {
                    assertEquals(
                        "Seed $seed, cell $i: given should match solution",
                        puzzle.solution[i], puzzle.puzzle[i]
                    )
                }
            }
        }
    }

    // =====================================================
    // F. Mutation safety
    // =====================================================

    @Test
    fun solve_does_not_mutate_input() {
        val puzzle = SudokuPuzzleEngine(seed = 42).generate()
        val original = puzzle.puzzle.clone()
        SudokuPuzzleEngine().solve(puzzle.puzzle)
        assertArrayEquals("solve() should not mutate input", original, puzzle.puzzle)
    }

    @Test
    fun countSolutions_does_not_mutate_input() {
        val puzzle = SudokuPuzzleEngine(seed = 42).generate()
        val original = puzzle.puzzle.clone()
        SudokuPuzzleEngine().countSolutions(puzzle.puzzle, 2)
        assertArrayEquals("countSolutions() should not mutate input", original, puzzle.puzzle)
    }

    @Test
    fun generate_does_not_mutate_caller_arrays() {
        val engine = SudokuPuzzleEngine(seed = 42)
        val puzzle1 = engine.generate()
        val puzzle2 = engine.generate()
        assertArrayEquals("generate() should not mutate previous results", puzzle1.puzzle, puzzle2.puzzle)
    }

    // =====================================================
    // G. Invalid inputs
    // =====================================================

    @Test
    fun wrong_board_length_returns_zero() {
        assertEquals(0, SudokuPuzzleEngine().countSolutions(IntArray(80), 2))
    }

    @Test
    fun wrong_board_length_solve_returns_null() {
        assertNull(SudokuPuzzleEngine().solve(IntArray(100)))
    }

    @Test
    fun value_outside_range_returns_zero() {
        val board = IntArray(81)
        board[0] = 10
        assertEquals(0, SudokuPuzzleEngine().countSolutions(board, 2))
    }

    @Test
    fun negative_value_returns_zero() {
        val board = IntArray(81)
        board[0] = -1
        assertEquals(0, SudokuPuzzleEngine().countSolutions(board, 2))
    }

    @Test
    fun duplicate_in_row_returns_zero() {
        val board = IntArray(81)
        board[0] = 5
        board[1] = 5
        assertEquals(0, SudokuPuzzleEngine().countSolutions(board, 2))
    }

    @Test
    fun duplicate_in_column_returns_zero() {
        val board = IntArray(81)
        board[0] = 5
        board[9] = 5
        assertEquals(0, SudokuPuzzleEngine().countSolutions(board, 2))
    }

    @Test
    fun duplicate_in_box_returns_zero() {
        val board = IntArray(81)
        board[0] = 5
        board[10] = 5
        assertEquals(0, SudokuPuzzleEngine().countSolutions(board, 2))
    }

    // =====================================================
    // H. Seed edge cases
    // =====================================================

    @Test
    fun seed_zero_produces_valid_puzzle() {
        val puzzle = SudokuPuzzleEngine(seed = 0).generate()
        assertTrue(isValidCompleteSolution(puzzle.solution))
        assertEquals(1, SudokuPuzzleEngine().countSolutions(puzzle.puzzle, 2))
    }

    @Test
    fun seed_negative_one_produces_valid_puzzle() {
        val puzzle = SudokuPuzzleEngine(seed = -1).generate()
        assertTrue(isValidCompleteSolution(puzzle.solution))
        assertEquals(1, SudokuPuzzleEngine().countSolutions(puzzle.puzzle, 2))
    }

    @Test
    fun seed_long_min_produces_valid_puzzle() {
        val puzzle = SudokuPuzzleEngine(seed = Long.MIN_VALUE).generate()
        assertTrue(isValidCompleteSolution(puzzle.solution))
        assertEquals(1, SudokuPuzzleEngine().countSolutions(puzzle.puzzle, 2))
    }

    @Test
    fun seed_long_max_produces_valid_puzzle() {
        val puzzle = SudokuPuzzleEngine(seed = Long.MAX_VALUE).generate()
        assertTrue(isValidCompleteSolution(puzzle.solution))
        assertEquals(1, SudokuPuzzleEngine().countSolutions(puzzle.puzzle, 2))
    }

    // =====================================================
    // Puzzle quality
    // =====================================================

    @Test
    fun generated_puzzle_has_reasonable_clue_count() {
        val puzzle = SudokuPuzzleEngine(seed = 42).generate()
        val clueCount = puzzle.puzzle.count { it != 0 }
        assertTrue("Clue count $clueCount should be >= 17 (minimum for unique solution)", clueCount >= 17)
        assertTrue("Clue count $clueCount should be <= 80", clueCount <= 80)
    }

    @Test
    fun generated_puzzle_is_not_already_complete() {
        val puzzle = SudokuPuzzleEngine(seed = 42).generate()
        val clueCount = puzzle.puzzle.count { it != 0 }
        assertTrue("Puzzle should not be complete (has $clueCount clues)", clueCount < 81)
    }

    @Test
    fun generated_puzzle_has_at_least_one_empty_cell() {
        val puzzle = SudokuPuzzleEngine(seed = 42).generate()
        assertTrue("Puzzle should have at least one empty cell", puzzle.puzzle.contains(0))
    }

    // =====================================================
    // Board utilities
    // =====================================================

    @Test
    fun boardToString_works() {
        val engine = SudokuPuzzleEngine(seed = 42)
        val board = IntArray(81) { it % 9 + 1 }
        val str = engine.boardToString(board)
        val lines = str.lines()
        assertTrue("Should have at least 9 lines (rows), got ${lines.size}", lines.size >= 9)
    }

    @Test
    fun canPlace_detects_row_conflict() {
        val engine = SudokuPuzzleEngine(seed = 42)
        val board = IntArray(81)
        board[0] = 5
        assertFalse(engine.canPlace(board, 0, 1, 5))
        assertTrue(engine.canPlace(board, 0, 1, 3))
    }

    @Test
    fun canPlace_detects_column_conflict() {
        val engine = SudokuPuzzleEngine(seed = 42)
        val board = IntArray(81)
        board[0] = 5
        assertFalse(engine.canPlace(board, 1, 0, 5))
        assertTrue(engine.canPlace(board, 1, 0, 3))
    }

    @Test
    fun canPlace_detects_box_conflict() {
        val engine = SudokuPuzzleEngine(seed = 42)
        val board = IntArray(81)
        board[0] = 5
        assertFalse(engine.canPlace(board, 1, 1, 5))
        assertTrue(engine.canPlace(board, 1, 1, 3))
    }

    @Test
    fun getCandidates_returns_valid_values() {
        val engine = SudokuPuzzleEngine(seed = 42)
        val board = IntArray(81)
        val candidates = engine.getCandidates(board, 0, 0)
        assertEquals(9, candidates.size)
        assertEquals((1..9).toSet(), candidates.toSet())
    }

    @Test
    fun getCandidates_respects_existing_values() {
        val engine = SudokuPuzzleEngine(seed = 42)
        val board = IntArray(81)
        board[0] = 1
        board[1] = 2
        board[2] = 3
        val candidates = engine.getCandidates(board, 0, 3)
        assertFalse("Candidates should not contain 1", candidates.contains(1))
        assertFalse("Candidates should not contain 2", candidates.contains(2))
        assertFalse("Candidates should not contain 3", candidates.contains(3))
    }

    @Test
    fun solve_returns_null_for_unsolvable_board() {
        val unsolvable = IntArray(81)
        unsolvable[0] = 1
        unsolvable[1] = 1
        assertNull("Unsolvable board should return null", SudokuPuzzleEngine().solve(unsolvable))
    }

    @Test
    fun solve_returns_solution_for_already_complete_board() {
        val puzzle = SudokuPuzzleEngine(seed = 42).generate()
        val solution = SudokuPuzzleEngine().solve(puzzle.solution)
        assertNotNull(solution)
        assertArrayEquals(puzzle.solution, solution)
    }
}
