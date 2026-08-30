package rew.lightgames.sudoku2

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class PuzzleCorpusTest {

    private fun loadPuzzles(): List<String> {
        val file = File("src/main/assets/easy.csv")
        assertTrue("easy.csv should exist", file.exists())
        return file.readLines().filter { it.isNotBlank() }
    }

    private fun parsePuzzle(line: String): Array<IntArray> {
        require(line.length == 81) { "Puzzle line must be exactly 81 characters, got ${line.length}" }
        return Array(9) { row ->
            IntArray(9) { col ->
                val ch = line[row * 9 + col]
                ch.toString().toIntOrNull() ?: throw IllegalArgumentException("Invalid character '$ch' at position ${row * 9 + col}")
            }
        }
    }

    @Test
    fun puzzle_file_exists_and_is_nonempty() {
        val puzzles = loadPuzzles()
        assertTrue("Puzzle corpus should contain at least 1 puzzle", puzzles.isNotEmpty())
    }

    @Test
    fun puzzle_count_is_1000() {
        val puzzles = loadPuzzles()
        assertEquals("Should have 1000 puzzles in easy.csv", 1000, puzzles.size)
    }

    @Test
    fun every_puzzle_has_exactly_81_characters() {
        val puzzles = loadPuzzles()
        for ((index, puzzle) in puzzles.withIndex()) {
            assertEquals("Puzzle $index should have exactly 81 characters", 81, puzzle.length)
        }
    }

    @Test
    fun every_puzzle_contains_only_valid_digits() {
        val puzzles = loadPuzzles()
        for ((index, puzzle) in puzzles.withIndex()) {
            for ((pos, ch) in puzzle.withIndex()) {
                val digit = ch.toString().toIntOrNull()
                assertNotNull("Puzzle $index position $pos: '$ch' is not a valid digit", digit)
                assertTrue("Puzzle $index position $pos: digit $digit out of range", digit in 0..9)
            }
        }
    }

    @Test
    fun every_puzzle_parses_to_9x9_grid() {
        val puzzles = loadPuzzles()
        for ((index, line) in puzzles.withIndex()) {
            val grid = parsePuzzle(line)
            assertEquals("Parsed puzzle $index should be 9x9", 9, grid.size)
            for (row in grid) {
                assertEquals("Each row should have 9 elements", 9, row.size)
            }
        }
    }

    @Test
    fun first_puzzle_has_empty_cells() {
        val puzzles = loadPuzzles()
        val grid = parsePuzzle(puzzles[0])
        val emptyCount = grid.sumOf { row -> row.count { it == 0 } }
        assertTrue("First puzzle should have some empty cells (got $emptyCount)", emptyCount > 0)
    }

    @Test
    fun no_puzzle_is_already_complete() {
        val puzzles = loadPuzzles()
        for ((index, line) in puzzles.withIndex()) {
            val grid = parsePuzzle(line)
            val emptyCount = grid.sumOf { row -> row.count { it == 0 } }
            assertTrue("Puzzle $index should not be already complete (empty cells: $emptyCount)", emptyCount > 0)
        }
    }

    @Test
    fun every_puzzle_has_valid_digit_range() {
        val puzzles = loadPuzzles()
        for ((index, line) in puzzles.withIndex()) {
            for (ch in line) {
                val digit = ch - '0'
                assertTrue("Puzzle $index: digit $digit out of range 0-9", digit in 0..9)
            }
        }
    }

    @Test
    fun sample_puzzle_first_has_given_count_in_reasonable_range() {
        val puzzles = loadPuzzles()
        val grid = parsePuzzle(puzzles[0])
        val givenCount = grid.sumOf { row -> row.count { it != 0 } }
        assertTrue("First puzzle should have between 17 and 80 givens (got $givenCount)", givenCount in 17..80)
    }

    @Test
    fun all_puzzles_have_reasonable_given_counts() {
        val puzzles = loadPuzzles()
        for ((index, line) in puzzles.withIndex()) {
            val grid = parsePuzzle(line)
            val givenCount = grid.sumOf { row -> row.count { it != 0 } }
            assertTrue(
                "Puzzle $index: given count $givenCount outside reasonable range 17-80",
                givenCount in 17..80
            )
        }
    }
}
