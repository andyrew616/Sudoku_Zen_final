package rew.lightgames.sudoku2

import android.content.Intent
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.navigation.fragment.NavHostFragment
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhysicalDeviceGameplaySmokeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val targetContext = instrumentation.targetContext

    @Test
    fun easyMediumAndHard_launchPlayableBoardsAndSavedBoardResumesExactly() {
        val loadedPuzzles = linkedMapOf<SudokuDifficulty, List<Int>>()

        playableDifficulties.forEach { difficulty ->
            val activity = launch(difficulty)
            val viewModel = viewModel(activity)
            waitUntilReady(viewModel)
            val state = viewModel.gameplayLoadState.value
            assertTrue("$difficulty did not become ready: $state", state is GameplayLoadState.Ready)
            state as GameplayLoadState.Ready
            assertEquals(difficulty, state.requestedDifficulty)
            val puzzle = boardPuzzle(viewModel)
            loadedPuzzles[difficulty] = puzzle.toList()
            assertTrue(puzzle.any { it == 0 })
            assertTrue(puzzle.any { it != 0 })
            auditBoard(viewModel, difficulty)
            close(activity)
        }

        assertEquals(3, loadedPuzzles.values.toSet().size)

        val activity = launch(SudokuDifficulty.EASY)
        val originalViewModel = viewModel(activity)
        waitUntilReady(originalViewModel)
        val originalBoard = requireNotNull(originalViewModel.sudokuBoard.value)
        val blanks = buildList {
            for (row in 0 until 9) {
                for (column in 0 until 9) {
                    if (originalBoard.getCell(row, column).original_number == 0) add(row to column)
                }
            }
        }
        assertTrue(blanks.size >= 2)
        val filledCell = blanks[0]
        val notedCell = blanks[1]
        onMain {
            originalViewModel.selectCell(filledCell.first, filledCell.second)
            originalViewModel.updateSelectedCellValue(
                originalBoard.solution[filledCell.first][filledCell.second]
            )
            originalViewModel.selectCell(notedCell.first, notedCell.second)
            originalViewModel.toggleNotesMode()
            originalViewModel.updateSelectedCellValue(3)
            originalViewModel.setHintsUsed(2)
        }
        val expectedBoard = requireNotNull(originalViewModel.sudokuBoard.value).copy()
        onMain { activity.finish() }
        instrumentation.waitForIdleSync()

        val resumed = launchResume()
        val resumedViewModel = viewModel(resumed)
        waitUntilReady(resumedViewModel)
        val resumedState = resumedViewModel.gameplayLoadState.value
        assertEquals(
            GameplayLoadState.Ready(SudokuDifficulty.EASY, GameplayPuzzleSource.RESUMED),
            resumedState
        )
        assertEquals(2, resumedViewModel.hintsUsed.value)
        val restoredBoard = requireNotNull(resumedViewModel.sudokuBoard.value)
        assertBoardsExactlyEqual(expectedBoard, restoredBoard)
        auditBoard(resumedViewModel, SudokuDifficulty.EASY)
        close(resumed)
    }

    @Test
    fun realDeviceProviderLatency_multipleRunsPerDifficulty() {
        val fallback = CompactFallbackPuzzleProvider {
            targetContext.assets.open(GRADED_FALLBACK_ASSET)
                .bufferedReader()
                .use { it.readText() }
        }
        val provider = GameplayPuzzleProvider(fallback)

        playableDifficulties.forEachIndexed { difficultyIndex, difficulty ->
            val samples = ArrayList<Long>()
            var generated = 0
            var fallbackCount = 0
            val puzzles = HashSet<List<Int>>()
            repeat(10) { sampleIndex ->
                val seed = 50_000L + difficultyIndex * 1_000L + sampleIndex
                val started = SystemClock.elapsedRealtimeNanos()
                val result = provider.createPuzzle(difficulty, seed)
                samples += SystemClock.elapsedRealtimeNanos() - started
                assertTrue("provider failed for $difficulty seed=$seed: $result", result is PuzzleLoadResult.Ready)
                result as PuzzleLoadResult.Ready
                assertEquals(difficulty, result.actualRating.difficulty)
                when (result.source) {
                    GameplayPuzzleSource.GENERATED -> generated++
                    GameplayPuzzleSource.FALLBACK -> fallbackCount++
                    GameplayPuzzleSource.RESUMED -> error("Provider cannot return RESUMED")
                }
                puzzles += IntArray(81) { index ->
                    result.board.getCell(index / 9, index % 9).original_number
                }.toList()
            }
            val ordered = samples.sorted()
            println(
                "PR9 DEVICE LATENCY $difficulty samples=${samples.size} generated=$generated " +
                    "fallback=$fallbackCount medianMs=${millis(ordered[ordered.size / 2])} " +
                    "p95Ms=${millis(ordered[9])} slowestMs=${millis(ordered.last())}"
            )
            assertTrue("expected varied $difficulty puzzles", puzzles.size > 1)
        }
    }

    @Test
    fun gradeLessLegacyNextRoute_opensDifficultySelection() {
        val intent = Intent(targetContext, MenuHostActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(MenuHostActivity.EXTRA_OPEN_DIFFICULTY, true)
        val activity = instrumentation.startActivitySync(intent) as MenuHostActivity
        instrumentation.waitForIdleSync()

        val navHost = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
            as NavHostFragment
        assertEquals(R.id.SecondFragment, navHost.navController.currentDestination?.id)

        onMain { activity.finish() }
        instrumentation.waitForIdleSync()
    }

    private fun launch(difficulty: SudokuDifficulty): MainActivity {
        val intent = Intent(targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(
                GameplayDifficultyAdapter.INTENT_EXTRA,
                GameplayDifficultyAdapter.toExternalValue(difficulty)
            )
        return instrumentation.startActivitySync(intent) as MainActivity
    }

    private fun launchResume(): MainActivity {
        val intent = Intent(targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("Resume", true)
        return instrumentation.startActivitySync(intent) as MainActivity
    }

    private fun waitUntilReady(viewModel: SudokuViewModel) {
        val deadline = SystemClock.elapsedRealtime() + 30_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            instrumentation.waitForIdleSync()
            when (val state = viewModel.gameplayLoadState.value) {
                is GameplayLoadState.Ready -> return
                is GameplayLoadState.Failure -> error("Gameplay failed: $state")
                else -> Thread.sleep(20L)
            }
        }
        error("Timed out waiting for gameplay: ${viewModel.gameplayLoadState.value}")
    }

    private fun auditBoard(viewModel: SudokuViewModel, difficulty: SudokuDifficulty) {
        val board = requireNotNull(viewModel.sudokuBoard.value)
        val puzzle = IntArray(81) { index ->
            board.getCell(index / 9, index % 9).original_number
        }
        val logical = SudokuLogicalSolver().solve(puzzle)
        assertEquals(LogicalSolveStatus.SOLVED, logical.status)
        assertEquals(difficulty, SudokuDifficultyGrader().grade(puzzle, logical).difficulty)
        assertEquals(1, SudokuPuzzleEngine(0L).countSolutions(puzzle, 2))
    }

    private fun assertBoardsExactlyEqual(expected: SudokuBoard, actual: SudokuBoard) {
        for (row in 0 until 9) {
            assertTrue(expected.solution[row].contentEquals(actual.solution[row]))
            for (column in 0 until 9) {
                assertEquals(expected.getCell(row, column), actual.getCell(row, column))
            }
        }
    }

    private fun boardPuzzle(viewModel: SudokuViewModel): IntArray {
        val board = requireNotNull(viewModel.sudokuBoard.value)
        return IntArray(81) { index -> board.getCell(index / 9, index % 9).original_number }
    }

    private fun viewModel(activity: MainActivity): SudokuViewModel {
        val field = MainActivity::class.java.getDeclaredField("viewModel")
        field.isAccessible = true
        return field.get(activity) as SudokuViewModel
    }

    private fun close(activity: MainActivity) {
        onMain { activity.finish() }
        instrumentation.waitForIdleSync()
    }

    private fun onMain(block: () -> Unit) {
        instrumentation.runOnMainSync(block)
    }

    private fun millis(nanos: Long): String =
        String.format(Locale.US, "%.3f", nanos / 1_000_000.0)

    private companion object {
        val playableDifficulties = listOf(
            SudokuDifficulty.EASY,
            SudokuDifficulty.MEDIUM,
            SudokuDifficulty.HARD
        )
    }
}
