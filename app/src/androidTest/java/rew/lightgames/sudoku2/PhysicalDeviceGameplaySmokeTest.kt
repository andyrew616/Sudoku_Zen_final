package rew.lightgames.sudoku2

import android.content.Intent
import android.os.SystemClock
import android.widget.TextView
import com.google.android.material.snackbar.Snackbar
import androidx.lifecycle.Lifecycle
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
    fun progressiveEasyHint_rendersLocalizedTextAndAccessibleActionHighlightsWithoutMutation() {
        val activity = launch(SudokuDifficulty.EASY)
        val viewModel = viewModel(activity)
        try {
            waitUntilReady(viewModel)
            val before = requireNotNull(viewModel.sudokuBoard.value).playerValues()

            onMain { viewModel.provideHint() }
            val technique = (viewModel.logicalHintResult.value as LogicalHintResult.Available).hint
            assertEquals(HintDetailLevel.TECHNIQUE, technique.detailLevel)
            assertEquals(
                LogicalHintTextFormatter(activity).format(technique),
                waitForSnackbarText(activity)
            )

            onMain { viewModel.provideHint() }
            instrumentation.waitForIdleSync()
            val evidence = (viewModel.logicalHintResult.value as LogicalHintResult.Available).hint
            assertEquals(HintDetailLevel.EVIDENCE, evidence.detailLevel)
            assertTrue(evidence.highlights.isNotEmpty())

            onMain { viewModel.provideHint() }
            val action = (viewModel.logicalHintResult.value as LogicalHintResult.Available).hint
            assertEquals(HintDetailLevel.ACTION, action.detailLevel)
            assertEquals(
                LogicalHintTextFormatter(activity).format(action),
                waitForSnackbarText(activity)
            )
            val boardView = activity.findViewById<SudokuBoardView>(R.id.sudokuBoardView)
            action.targetCells.forEach { target ->
                val description = boardView.getCellView(target.row, target.column)
                    ?.contentDescription
                    ?.toString()
                assertTrue(
                    "Missing accessible target description for $target",
                    !description.isNullOrBlank()
                )
            }

            assertEquals(1, viewModel.hintsUsed.value)
            assertTrue(before.contentEquals(requireNotNull(viewModel.sudokuBoard.value).playerValues()))
        } finally {
            close(activity)
        }
    }

    @Test
    fun logicalHintLatencyAndTechniqueCoverage_onPhysicalDevice() {
        val fallback = CompactFallbackPuzzleProvider {
            targetContext.assets.open(GRADED_FALLBACK_ASSET)
                .bufferedReader()
                .use { it.readText() }
        }
        val puzzleProvider = GameplayPuzzleProvider(fallback)
        val nextNanos = ArrayList<Long>()
        val mappingNanos = ArrayList<Long>()

        playableDifficulties.forEachIndexed { index, difficulty ->
            val loaded = puzzleProvider.createPuzzle(difficulty, 90_000L + index)
            assertTrue(loaded is PuzzleLoadResult.Ready)
            loaded as PuzzleLoadResult.Ready
            val puzzle = loaded.board.playerValues()
            val grid = (CandidateGrid.create(puzzle) as CandidateGridCreationResult.Success).grid
            val solver = SudokuLogicalSolver()
            val mapper = LogicalHintMapper()
            val techniques = LinkedHashSet<SudokuTechnique>()

            while (grid.snapshot().values.any { it == 0 }) {
                val nextStarted = SystemClock.elapsedRealtimeNanos()
                val step = requireNotNull(solver.nextStep(grid))
                nextNanos += SystemClock.elapsedRealtimeNanos() - nextStarted
                val mappingStarted = SystemClock.elapsedRealtimeNanos()
                val hint = mapper.map(step, HintDetailLevel.ACTION)
                mappingNanos += SystemClock.elapsedRealtimeNanos() - mappingStarted
                assertEquals(step.actions, hint.actions)
                assertEquals(CandidateGridMutationResult.Success, grid.applyActions(step.actions))
                techniques += step.technique
            }

            when (difficulty) {
                SudokuDifficulty.EASY -> assertTrue(techniques.isNotEmpty())
                // A Medium rating may come from cumulative score without a Medium-tier step.
                // Medium techniques are exercised by the controlled all-technique device fixture.
                SudokuDifficulty.MEDIUM -> assertTrue(techniques.isNotEmpty())
                SudokuDifficulty.HARD -> assertTrue(techniques.any { it in HARD_TECHNIQUES })
                SudokuDifficulty.UNSUPPORTED -> error("Not playable")
            }
        }

        val nextP95 = percentileMillis(nextNanos)
        val mappingP95 = percentileMillis(mappingNanos)
        println(
            "PR11 DEVICE HINT PERFORMANCE nextP95Ms=${decimal(nextP95)} " +
                "mappingP95Ms=${decimal(mappingP95)} samples=${nextNanos.size}"
        )
        assertTrue("Device next-step p95 exceeded 50 ms", nextP95 < 50.0)
        assertTrue("Device hint-mapping p95 exceeded 50 ms", mappingP95 < 50.0)
    }

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
            originalViewModel.provideHint()
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
        assertEquals(1, resumedViewModel.hintsUsed.value)
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
        return (instrumentation.startActivitySync(intent) as MainActivity).also {
            waitUntilResumed(it)
        }
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

    private fun waitForSnackbarText(activity: MainActivity): String {
        val field = MainActivity::class.java.getDeclaredField("hintSnackbar")
        field.isAccessible = true
        val deadline = SystemClock.elapsedRealtime() + 3_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            instrumentation.waitForIdleSync()
            val snackbar = field.get(activity) as Snackbar?
            if (snackbar != null) {
                return snackbar.view.findViewById<TextView>(
                    com.google.android.material.R.id.snackbar_text
                ).text.toString()
            }
            Thread.sleep(20L)
        }
        error("Hint snackbar was not rendered; lifecycle=${activity.lifecycle.currentState}")
    }

    private fun waitUntilResumed(activity: MainActivity) {
        val deadline = SystemClock.elapsedRealtime() + 3_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            instrumentation.waitForIdleSync()
            if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
            Thread.sleep(20L)
        }
        error("Activity did not resume: ${activity.lifecycle.currentState}")
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

    private fun percentileMillis(values: List<Long>): Double {
        val sorted = values.sorted()
        return sorted[((sorted.size - 1) * 0.95).toInt()] / 1_000_000.0
    }

    private fun decimal(value: Double): String = String.format(Locale.US, "%.3f", value)

    private companion object {
        val playableDifficulties = listOf(
            SudokuDifficulty.EASY,
            SudokuDifficulty.MEDIUM,
            SudokuDifficulty.HARD
        )
        val HARD_TECHNIQUES = setOf(
            SudokuTechnique.NAKED_TRIPLE,
            SudokuTechnique.HIDDEN_TRIPLE,
            SudokuTechnique.X_WING,
            SudokuTechnique.XY_WING,
            SudokuTechnique.SKYSCRAPER,
            SudokuTechnique.TWO_STRING_KITE
        )
    }
}
