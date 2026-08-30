package rew.lightgames.sudoku2

import org.junit.Assert.*
import org.junit.Test
import kotlin.system.measureTimeMillis

class SudokuGeneratorBulkTest {

    private companion object {
        const val SAMPLE_SIZE = 1000
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

    @Test
    fun bulk_generate_and_validate_1000_puzzles() {
        val times = mutableListOf<Long>()
        val clueCounts = mutableListOf<Int>()
        val puzzleStrings = mutableSetOf<String>()
        val solutionStrings = mutableSetOf<String>()
        var failures = 0
        var retries = 0

        val totalTime = measureTimeMillis {
            for (seed in 1L..SAMPLE_SIZE.toLong()) {
                val genTime = measureTimeMillis {
                    try {
                        val engine = SudokuPuzzleEngine(seed = seed)
                        val puzzle = engine.generate()

                        // A. Valid full solution
                        assertTrue("Seed $seed: invalid solution", isValidCompleteSolution(puzzle.solution))

                        // B. Valid givens
                        for (i in 0 until 81) {
                            if (puzzle.puzzle[i] != 0) {
                                assertEquals(
                                    "Seed $seed: given mismatch at $i",
                                    puzzle.solution[i], puzzle.puzzle[i]
                                )
                            }
                        }

                        // C. Exactly one solution
                        val solverEngine = SudokuPuzzleEngine()
                        val solutionCount = solverEngine.countSolutions(puzzle.puzzle, 2)
                        assertEquals("Seed $seed: should have exactly 1 solution", 1, solutionCount)

                        // D. Solver consistency
                        val solved = solverEngine.solve(puzzle.puzzle)
                        assertNotNull("Seed $seed: solver should find solution", solved)
                        assertArrayEquals(
                            "Seed $seed: solver output mismatch",
                            puzzle.solution, solved
                        )

                        // E. Clue count tracking
                        val clueCount = puzzle.puzzle.count { it != 0 }
                        clueCounts.add(clueCount)
                        assertTrue("Seed $seed: clue count $clueCount out of range", clueCount in 17..80)

                        // F. Deduplication tracking
                        puzzleStrings.add(puzzle.puzzle.toList().toString())
                        solutionStrings.add(puzzle.solution.toList().toString())

                        // G. Determinism check
                        val engine2 = SudokuPuzzleEngine(seed = seed)
                        val puzzle2 = engine2.generate()
                        assertArrayEquals(
                            "Seed $seed: non-deterministic puzzle",
                            puzzle.puzzle, puzzle2.puzzle
                        )
                        assertArrayEquals(
                            "Seed $seed: non-deterministic solution",
                            puzzle.solution, puzzle2.solution
                        )

                        // H. Mutation safety
                        val puzzleArray = puzzle.puzzle.clone()
                        solverEngine.solve(puzzle.puzzle)
                        assertArrayEquals("Seed $seed: solve mutated input", puzzleArray, puzzle.puzzle)

                    } catch (e: Exception) {
                        failures++
                        println("Seed $seed FAILED: ${e.message}")
                    }
                }
                times.add(genTime)
            }
        }

        // Report
        val sortedTimes = times.sorted()
        val avgTime = times.average()
        val medianTime = sortedTimes[sortedTimes.size / 2]
        val p95Index = (sortedTimes.size * 0.95).toInt().coerceAtMost(sortedTimes.size - 1)
        val p95Time = sortedTimes[p95Index]
        val slowestTime = sortedTimes.last()
        val fastestTime = sortedTimes.first()

        val avgClueCount = clueCounts.average()
        val minClues = clueCounts.minOrNull() ?: 0
        val maxClues = clueCounts.maxOrNull() ?: 0

        println("\n========== BULK GENERATION REPORT ==========")
        println("Total puzzles generated: $SAMPLE_SIZE")
        println("Total failures: $failures")
        println("Total retries: $retries")
        println("Total runtime: ${totalTime}ms")
        println("Average generation time: ${"%.1f".format(avgTime)}ms")
        println("Median generation time: ${medianTime}ms")
        println("P95 generation time: ${p95Time}ms")
        println("Fastest generation: ${fastestTime}ms")
        println("Slowest generation: ${slowestTime}ms")
        println("Average clue count: ${"%.1f".format(avgClueCount)}")
        println("Min clue count: $minClues")
        println("Max clue count: $maxClues")
        println("Unique puzzle patterns: ${puzzleStrings.size} / $SAMPLE_SIZE")
        println("Unique solution grids: ${solutionStrings.size} / $SAMPLE_SIZE")
        println("============================================")

        assertEquals("All puzzles should generate successfully", 0, failures)
        assertEquals(
            "All puzzle patterns should be unique for unique seeds",
            SAMPLE_SIZE, puzzleStrings.size
        )
    }

    @Test
    fun bulk_validate_performance_under_1_second_per_puzzle() {
        val maxAllowedMs = 1000L
        val slowSeeds = mutableListOf<Pair<Long, Long>>()

        for (seed in 1L..100L) {
            val time = measureTimeMillis {
                SudokuPuzzleEngine(seed = seed).generate()
            }
            if (time > maxAllowedMs) {
                slowSeeds.add(seed to time)
            }
        }

        assertTrue(
            "No puzzle should take > ${maxAllowedMs}ms. Slow seeds: $slowSeeds",
            slowSeeds.isEmpty()
        )
    }

    @Test
    fun bulk_validate_all_puzzles_pass_uniqueness() {
        val engine = SudokuPuzzleEngine()
        for (seed in 1L..500L) {
            val puzzle = SudokuPuzzleEngine(seed = seed).generate()
            val count = engine.countSolutions(puzzle.puzzle, 2)
            assertEquals("Seed $seed: uniqueness check failed (count=$count)", 1, count)
        }
    }

    @Test
    fun bulk_validate_solver_matches_stored_solution() {
        val engine = SudokuPuzzleEngine()
        for (seed in 1L..500L) {
            val puzzle = SudokuPuzzleEngine(seed = seed).generate()
            val solved = engine.solve(puzzle.puzzle)
            assertNotNull("Seed $seed: solver returned null", solved)
            assertArrayEquals(
                "Seed $seed: solver solution mismatch",
                puzzle.solution, solved
            )
        }
    }

    @Test
    fun clue_count_distribution_has_reasonable_range() {
        val clueCounts = mutableListOf<Int>()
        for (seed in 1L..500L) {
            val puzzle = SudokuPuzzleEngine(seed = seed).generate()
            clueCounts.add(puzzle.puzzle.count { it != 0 })
        }

        val avg = clueCounts.average()
        val min = clueCounts.minOrNull()!!
        val max = clueCounts.maxOrNull()!!

        println("Clue count distribution: avg=${"%.1f".format(avg)}, min=$min, max=$max")

        assertTrue("Average clue count $avg should be between 20 and 60", avg in 20.0..60.0)
        assertTrue("Min clue count $min should be >= 17", min >= 17)
        assertTrue("Max clue count $max should be <= 80", max <= 80)
    }

    @Test
    fun different_seeds_produce_varied_puzzles() {
        val puzzleHashes = mutableSetOf<Int>()
        for (seed in 1L..200L) {
            val puzzle = SudokuPuzzleEngine(seed = seed).generate()
            puzzleHashes.add(puzzle.puzzle.contentHashCode())
        }
        println("Unique puzzle hashes: ${puzzleHashes.size} / 200")
        assertTrue(
            "Should have good variation (got ${puzzleHashes.size} unique from 200)",
            puzzleHashes.size > 150
        )
    }

    @Test
    fun compatibility_with_existing_sudoku_board_representation() {
        val engine = SudokuPuzzleEngine(seed = 42)
        val generated = engine.generate()

        val puzzleGrid: Array<IntArray> = Array(9) { r ->
            IntArray(9) { c -> generated.puzzle[r * 9 + c] }
        }
        val solutionGrid: Array<IntArray> = Array(9) { r ->
            IntArray(9) { c -> generated.solution[r * 9 + c] }
        }

        assertEquals(9, puzzleGrid.size)
        assertEquals(9, solutionGrid.size)
        for (row in puzzleGrid) assertEquals(9, row.size)
        for (row in solutionGrid) assertEquals(9, row.size)

        for (r in 0 until 9) {
            for (c in 0 until 9) {
                if (puzzleGrid[r][c] != 0) {
                    assertEquals(
                        "Given at ($r,$c) should match solution",
                        solutionGrid[r][c], puzzleGrid[r][c]
                    )
                }
            }
        }

        val board = SudokuBoard(
            cells = Array(9) { r ->
                Array(9) { c ->
                    Cell(
                        number = puzzleGrid[r][c],
                        original_number = puzzleGrid[r][c],
                        notes = arrayListOf()
                    )
                }
            },
            solution = solutionGrid
        )

        for (r in 0 until 9) {
            for (c in 0 until 9) {
                assertEquals(solutionGrid[r][c], board.solution[r][c])
                val cell = board.getCell(r, c)
                assertEquals(puzzleGrid[r][c], cell.number)
                assertEquals(puzzleGrid[r][c], cell.original_number)
                assertEquals(puzzleGrid[r][c] == 0, cell.isEditable)
            }
        }
    }
}
