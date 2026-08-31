package rew.lightgames.sudoku2

import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SudokuViewModelIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun logicalHintProgression_needsNoSelectionCountsOnceAndNeverMutatesBoard() {
        val targeted = targeted(0L, SudokuDifficulty.EASY)
        val board = GameplaySudokuBoardAdapter.fromTargetedPuzzle(targeted)
        val before = board.playerValues()
        lateinit var viewModel: SudokuViewModel

        onMain {
            viewModel = SudokuViewModel(
                shouldGenerateNewGame = false,
                requestedDifficulty = null,
                puzzleLoader = GameplayPuzzleLoader { _, _ -> error("Must not generate") },
                seedSource = GameplaySeedSource { error("Must not request a seed") },
                generationDispatcher = Dispatchers.Default
            )
            viewModel.setBoard(board)

            viewModel.provideHint()
            assertEquals(
                HintDetailLevel.TECHNIQUE,
                (viewModel.logicalHintResult.value as LogicalHintResult.Available).hint.detailLevel
            )
            viewModel.provideHint()
            assertEquals(
                HintDetailLevel.EVIDENCE,
                (viewModel.logicalHintResult.value as LogicalHintResult.Available).hint.detailLevel
            )
            viewModel.provideHint()
            assertEquals(
                HintDetailLevel.ACTION,
                (viewModel.logicalHintResult.value as LogicalHintResult.Available).hint.detailLevel
            )
            viewModel.provideHint()
        }

        assertEquals(1, viewModel.hintsUsed.value)
        assertArrayEquals(before, requireNotNull(viewModel.sudokuBoard.value).playerValues())
    }

    @Test
    fun incorrectPlayerEntry_blocksHintWithoutIdentifyingOrChangingCell() {
        val targeted = targeted(1L, SudokuDifficulty.EASY)
        val board = GameplaySudokuBoardAdapter.fromTargetedPuzzle(targeted)
        val editableIndex = targeted.puzzle.indexOfFirst { it == 0 }
        val wrongDigit = targeted.solution[editableIndex] % 9 + 1
        lateinit var viewModel: SudokuViewModel

        onMain {
            viewModel = SudokuViewModel(
                shouldGenerateNewGame = false,
                requestedDifficulty = null,
                puzzleLoader = GameplayPuzzleLoader { _, _ -> error("Must not generate") },
                seedSource = GameplaySeedSource { error("Must not request a seed") },
                generationDispatcher = Dispatchers.Default
            )
            viewModel.setBoard(board)
            viewModel.selectCell(editableIndex / 9, editableIndex % 9)
            viewModel.updateSelectedCellValue(wrongDigit)
            viewModel.provideHint()
        }

        assertEquals(LogicalHintResult.INCORRECT_VALUE_PRESENT, viewModel.logicalHintResult.value)
        assertEquals(0, viewModel.hintsUsed.value)
        assertEquals(
            wrongDigit,
            requireNotNull(viewModel.sudokuBoard.value)
                .getCell(editableIndex / 9, editableIndex % 9).number
        )
    }

    @Test
    fun newGame_invokesExactDifficultyOnceAndGeneratesOffMainThread() {
        val calls = AtomicInteger()
        val calledOnMain = AtomicBoolean(true)
        val completed = CountDownLatch(1)
        val targeted = targeted(7L, SudokuDifficulty.EASY)
        val loader = GameplayPuzzleLoader { difficulty, seed ->
            calls.incrementAndGet()
            calledOnMain.set(Looper.myLooper() == Looper.getMainLooper())
            assertEquals(SudokuDifficulty.EASY, difficulty)
            assertEquals(55L, seed)
            ready(targeted, seed).also { completed.countDown() }
        }
        lateinit var viewModel: SudokuViewModel

        onMain {
            viewModel = SudokuViewModel(
                shouldGenerateNewGame = true,
                requestedDifficulty = SudokuDifficulty.EASY,
                puzzleLoader = loader,
                seedSource = GameplaySeedSource { 55L },
                generationDispatcher = Dispatchers.Default
            )
        }

        assertTrue(completed.await(10, TimeUnit.SECONDS))
        instrumentation.waitForIdleSync()
        assertEquals(1, calls.get())
        assertFalse("target generation must not run on main", calledOnMain.get())
        assertTrue(viewModel.gameplayLoadState.value is GameplayLoadState.Ready)
        assertEquals(targeted.puzzle.toList(), boardPuzzle(viewModel).toList())
    }

    @Test
    fun resumeBypassesGenerationAndInstallsExistingBoard() {
        val calls = AtomicInteger()
        val targeted = targeted(0L, SudokuDifficulty.MEDIUM)
        val board = GameplaySudokuBoardAdapter.fromTargetedPuzzle(targeted)
        lateinit var viewModel: SudokuViewModel

        onMain {
            viewModel = SudokuViewModel(
                shouldGenerateNewGame = false,
                requestedDifficulty = null,
                puzzleLoader = GameplayPuzzleLoader { _, _ ->
                    calls.incrementAndGet()
                    error("Resume must not generate")
                },
                seedSource = GameplaySeedSource { error("Resume must not request a seed") },
                generationDispatcher = Dispatchers.Default
            )
            assertEquals(GameplayLoadState.Idle, viewModel.gameplayLoadState.value)
            viewModel.setBoard(board)
        }

        assertEquals(0, calls.get())
        assertEquals(board, viewModel.sudokuBoard.value)
        assertEquals(
            GameplayLoadState.Ready(null, GameplayPuzzleSource.RESUMED),
            viewModel.gameplayLoadState.value
        )
        assertFalse(viewModel.canGenerateNextPuzzle())
        onMain { viewModel.loadNextPuzzle() }
        assertEquals(
            GameplayLoadState.Ready(null, GameplayPuzzleSource.RESUMED),
            viewModel.gameplayLoadState.value
        )
    }

    @Test
    fun interruptedProcessRecreation_exposesRetryWithoutInstallingOldBoard() {
        val calls = AtomicInteger()
        lateinit var viewModel: SudokuViewModel
        onMain {
            viewModel = SudokuViewModel(
                shouldGenerateNewGame = false,
                requestedDifficulty = SudokuDifficulty.HARD,
                puzzleLoader = GameplayPuzzleLoader { _, _ ->
                    calls.incrementAndGet()
                    error("Interrupted recreation must wait for explicit retry")
                },
                seedSource = GameplaySeedSource { 1L },
                generationDispatcher = Dispatchers.Default
            )
            viewModel.reportGenerationInterrupted()
        }

        assertEquals(0, calls.get())
        assertEquals(null, viewModel.sudokuBoard.value)
        assertEquals(
            GameplayLoadState.Failure(
                SudokuDifficulty.HARD,
                GameplayPuzzleFailureReason.PUZZLE_LOAD_FAILED
            ),
            viewModel.gameplayLoadState.value
        )
        assertTrue(viewModel.canGenerateNextPuzzle())
    }

    @Test
    fun providerFailureBecomesTerminalFailureWithoutBrokenBoard() {
        lateinit var viewModel: SudokuViewModel
        val completed = CountDownLatch(1)
        onMain {
            viewModel = SudokuViewModel(
                shouldGenerateNewGame = true,
                requestedDifficulty = SudokuDifficulty.HARD,
                puzzleLoader = GameplayPuzzleLoader { difficulty, seed ->
                    PuzzleLoadResult.Failure(
                        requestedDifficulty = difficulty,
                        seed = seed,
                        reason = GameplayPuzzleFailureReason.FALLBACK_MISSING,
                        targetFailureReason = TargetGenerationFailureReason.ATTEMPT_BUDGET_EXHAUSTED
                    ).also { completed.countDown() }
                },
                seedSource = GameplaySeedSource { 9L },
                generationDispatcher = Dispatchers.Default
            )
        }

        assertTrue(completed.await(10, TimeUnit.SECONDS))
        instrumentation.waitForIdleSync()
        assertEquals(null, viewModel.sudokuBoard.value)
        assertEquals(
            GameplayLoadState.Failure(
                SudokuDifficulty.HARD,
                GameplayPuzzleFailureReason.FALLBACK_MISSING
            ),
            viewModel.gameplayLoadState.value
        )
    }

    @Test
    fun survivingViewModelStore_doesNotGenerateAgain() {
        val calls = AtomicInteger()
        val completed = CountDownLatch(1)
        val targeted = targeted(0L, SudokuDifficulty.MEDIUM)
        val store = ViewModelStore()
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = SudokuViewModel(
                shouldGenerateNewGame = true,
                requestedDifficulty = SudokuDifficulty.MEDIUM,
                puzzleLoader = GameplayPuzzleLoader { _, seed ->
                    calls.incrementAndGet()
                    ready(targeted, seed).also { completed.countDown() }
                },
                seedSource = GameplaySeedSource { 101L },
                generationDispatcher = Dispatchers.Default
            ) as T
        }
        lateinit var first: SudokuViewModel
        lateinit var recreated: SudokuViewModel

        onMain {
            val provider = ViewModelProvider(store, factory)
            first = provider[SudokuViewModel::class.java]
            recreated = provider[SudokuViewModel::class.java]
        }

        assertTrue(completed.await(10, TimeUnit.SECONDS))
        instrumentation.waitForIdleSync()
        assertSame(first, recreated)
        assertEquals(1, calls.get())
        onMain { store.clear() }
    }

    @Test
    fun supersededGenerationCannotInstallItsStaleBoard() {
        val calls = AtomicInteger()
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val secondCompleted = CountDownLatch(1)
        val firstTarget = targeted(7L, SudokuDifficulty.EASY)
        val secondTarget = targeted(8L, SudokuDifficulty.EASY)
        val loader = GameplayPuzzleLoader { _, seed ->
            when (calls.getAndIncrement()) {
                0 -> {
                    firstStarted.countDown()
                    releaseFirst.await(10, TimeUnit.SECONDS)
                    ready(firstTarget, seed)
                }
                else -> ready(secondTarget, seed).also { secondCompleted.countDown() }
            }
        }
        val seeds = AtomicInteger()
        lateinit var viewModel: SudokuViewModel

        onMain {
            viewModel = SudokuViewModel(
                shouldGenerateNewGame = true,
                requestedDifficulty = SudokuDifficulty.EASY,
                puzzleLoader = loader,
                seedSource = GameplaySeedSource { seeds.getAndIncrement().toLong() },
                generationDispatcher = Dispatchers.Default
            )
        }
        assertTrue(firstStarted.await(10, TimeUnit.SECONDS))
        onMain { viewModel.loadNextPuzzle() }
        assertTrue(secondCompleted.await(10, TimeUnit.SECONDS))
        instrumentation.waitForIdleSync()
        releaseFirst.countDown()
        instrumentation.waitForIdleSync()

        assertEquals(2, calls.get())
        assertNotEquals(firstTarget.puzzle.toList(), secondTarget.puzzle.toList())
        assertEquals(secondTarget.puzzle.toList(), boardPuzzle(viewModel).toList())
    }

    private fun ready(targeted: TargetedPuzzle, seed: Long): PuzzleLoadResult.Ready =
        PuzzleLoadResult.Ready(
            board = GameplaySudokuBoardAdapter.fromTargetedPuzzle(targeted),
            requestedDifficulty = targeted.requestedDifficulty,
            actualRating = targeted.rating,
            source = GameplayPuzzleSource.GENERATED,
            seed = seed
        )

    private fun targeted(seed: Long, difficulty: SudokuDifficulty): TargetedPuzzle {
        val result = DifficultyTargetGenerator().generate(seed, difficulty)
        assertTrue("target generation failed: $result", result is TargetGenerationResult.Success)
        return (result as TargetGenerationResult.Success).targetedPuzzle
    }

    private fun boardPuzzle(viewModel: SudokuViewModel): IntArray {
        val board = requireNotNull(viewModel.sudokuBoard.value)
        return IntArray(81) { index -> board.getCell(index / 9, index % 9).original_number }
    }

    private fun onMain(block: () -> Unit) {
        instrumentation.runOnMainSync(block)
    }
}
