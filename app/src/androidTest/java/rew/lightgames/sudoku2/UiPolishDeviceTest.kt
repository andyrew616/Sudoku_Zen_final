package rew.lightgames.sudoku2

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.google.android.material.snackbar.Snackbar
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UiPolishDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val targetContext = instrumentation.targetContext
    private val preferences = targetContext.getSharedPreferences(
        MainActivity.PREF_NAME,
        Context.MODE_PRIVATE
    )

    @Before
    fun startWithManualNotesAvailable() {
        preferences.edit().remove(MainActivity.PREF_AUTO_NOTES).commit()
    }

    @After
    fun restoreAutoNotesDefault() {
        preferences.edit().remove(MainActivity.PREF_AUTO_NOTES).commit()
    }

    @Test
    fun boardCellsAndGameControlsExposeReadableNonColourState() {
        val activity = launchGame()
        try {
            val viewModel = viewModel(activity)
            waitUntilReady(viewModel)
            val board = requireNotNull(viewModel.sudokuBoard.value)
            val boardView = activity.findViewById<SudokuBoardView>(R.id.sudokuBoardView)

            for (row in 0 until 9) {
                for (column in 0 until 9) {
                    val cell = requireNotNull(boardView.getCellView(row, column))
                    val node = cell.createAccessibilityNodeInfo()
                    assertTrue(cell.contentDescription?.isNotBlank() == true)
                    if (board.getCell(row, column).isEditable) {
                        assertEquals(Button::class.java.name, node.className.toString())
                        assertTrue(node.isClickable)
                    } else {
                        assertEquals(TextView::class.java.name, node.className.toString())
                        assertFalse(node.isClickable)
                    }
                }
            }

            val editable = firstEditable(board)
            val editableView = requireNotNull(boardView.getCellView(editable.first, editable.second))
            onMain { editableView.performClick() }
            assertEquals(editable, viewModel.selectedCell.value)
            assertEquals(
                activity.getString(R.string.sudoku_cell_selected_state),
                ViewCompat.getStateDescription(editableView)?.toString()
            )

            val minimumTouchTarget = dp(activity, 48)
            val notes = activity.findViewById<View>(R.id.gameplayNotesAction)
            val hint = activity.findViewById<View>(R.id.gameplayHintAction)
            val erase = activity.findViewById<View>(R.id.gameplayEraseAction)
            listOf(notes, hint, erase).forEach { action ->
                assertTrue(action.height >= minimumTouchTarget)
                assertEquals(Button::class.java.name, action.createAccessibilityNodeInfo().className)
            }
            assertEquals(
                activity.getString(R.string.settings_switch_off),
                ViewCompat.getStateDescription(notes)?.toString()
            )
            onMain { notes.performClick() }
            assertEquals(
                activity.getString(R.string.settings_switch_on),
                ViewCompat.getStateDescription(notes)?.toString()
            )
            assertEquals(
                activity.getString(R.string.gameplay_notes_mode_on),
                notes.contentDescription.toString()
            )
            val mode = activity.findViewById<TextView>(R.id.modeTextView)
            assertEquals(activity.getString(R.string.gameplay_notes_mode), mode.text.toString())
            assertEquals(mode.text.toString(), mode.contentDescription.toString())

            val numberKeyDescriptions = (1..9).map {
                activity.getString(R.string.gameplay_number_key, it)
            }.toSet()
            val numberKeys = descendants(activity.findViewById(android.R.id.content))
                .filterIsInstance<Button>()
                .filter { it.contentDescription?.toString() in numberKeyDescriptions }
                .toList()
            assertEquals(9, numberKeys.size)
            numberKeys.forEach { key ->
                assertTrue(key.width >= minimumTouchTarget)
                assertTrue(key.height >= minimumTouchTarget)
            }
        } finally {
            close(activity)
        }
    }

    @Test
    fun gameplayUsesImmersiveViewportWithoutIncidentalNativeScroll() {
        val activity = launchGame()
        try {
            waitUntilReady(viewModel(activity))
            val root = activity.findViewById<View>(R.id.bg)
            val scroll = activity.findViewById<NestedScrollView>(R.id.gameplayScroll)
            waitUntilLaidOut(scroll)
            val insets = waitForSystemBarsHidden(root)

            assertFalse(insets.isVisible(WindowInsetsCompat.Type.statusBars()))
            assertFalse(insets.isVisible(WindowInsetsCompat.Type.navigationBars()))
            assertEquals(0, scroll.scrollY)
            val configuration = activity.resources.configuration
            val measuredReferenceProfile = configuration.screenWidthDp == 384 &&
                configuration.screenHeightDp == 823 &&
                configuration.fontScale <= 1.01f
            if (measuredReferenceProfile) {
                assertFalse(
                    "Gameplay content ${scroll.getChildAt(0).height}px exceeds " +
                        "the ${scroll.height}px immersive viewport",
                    scroll.canScrollVertically(1)
                )
                assertFalse(scroll.canScrollVertically(-1))
            } else {
                val controls = activity.findViewById<View>(R.id.sudokuControlView)
                onMain { scroll.fullScroll(View.FOCUS_DOWN) }
                instrumentation.waitForIdleSync()
                assertFalse(scroll.canScrollVertically(1))
                assertTrue(controls.getGlobalVisibleRect(Rect()))
            }
        } finally {
            close(activity)
        }
    }

    @Test
    fun pauseRemainsModalAndTimerStaysStoppedAcrossOptions() {
        val activity = launchGame()
        try {
            waitUntilReady(viewModel(activity))
            onMain { activity.onBackPressedDispatcher.onBackPressed() }

            val pause = requireNotNull(dialog(activity, "pauseDialog"))
            waitUntilLaidOut(pause.findViewById(R.id.pauseCard))
            assertTrue(pause.isShowing)
            waitForSystemBarsHidden(requireNotNull(pause.window).decorView)
            assertFalse(timerIsRunning(activity))
            assertNotNull(pause.findViewById<View>(R.id.pauseDialogRoot).background)
            assertTrue(pause.findViewById<View>(R.id.pauseDialogScroll) is NestedScrollView)

            val minimumTouchTarget = dp(activity, 48)
            listOf(R.id.resume_bttn, R.id.options_bttn, R.id.exit_bttn).forEach { id ->
                val button = pause.findViewById<Button>(id)
                assertTrue(button.height >= minimumTouchTarget)
            }

            onMain { pause.findViewById<Button>(R.id.options_bttn).performClick() }
            val options = waitForResumedActivity<OptionsActivity>()
            assertFalse(timerIsRunning(activity))
            assertOptionsAccessibility(options, minimumTouchTarget)
            val optionsRoot = options.findViewById<View>(android.R.id.content)
            waitForSystemBarsVisible(optionsRoot)
            assertTrue(optionsRoot.paddingTop > 0)
            assertTrue(optionsRoot.paddingBottom > 0)

            onMain { options.finish() }
            waitUntilResumed(activity)
            assertTrue(pause.isShowing)
            waitForSystemBarsHidden(requireNotNull(pause.window).decorView)
            assertFalse(timerIsRunning(activity))

            onMain { pause.findViewById<Button>(R.id.resume_bttn).performClick() }
            assertFalse(pause.isShowing)
            assertTrue(timerIsRunning(activity))
        } finally {
            close(activity)
        }
    }

    @Test
    fun progressiveHintSurfaceAddsStageWithoutChangingFormatterText() {
        val activity = launchGame()
        try {
            val viewModel = viewModel(activity)
            waitUntilReady(viewModel)
            onMain { viewModel.provideHint() }

            val result = viewModel.logicalHintResult.value as LogicalHintResult.Available
            val expectedText = LogicalHintTextFormatter(activity).format(result.hint)
            val snackbar = waitForSnackbar(activity)
            val message = snackbar.view.findViewById<TextView>(
                com.google.android.material.R.id.snackbar_text
            )
            assertEquals(expectedText, message.text.toString())
            assertEquals(Int.MAX_VALUE, message.maxLines)

            val stage = activity.getString(R.string.gameplay_hint_stage_technique)
            assertEquals(
                "$stage. $expectedText",
                message.contentDescription.toString()
            )
            assertTrue(
                descendants(snackbar.view)
                    .filterIsInstance<TextView>()
                    .any { it.text.toString() == stage }
            )
            assertEquals(
                activity.getString(R.string.gameplay_hint_dismiss),
                snackbar.view.findViewById<TextView>(
                    com.google.android.material.R.id.snackbar_action
                ).text.toString()
            )

            onMain {
                viewModel.provideHint()
                viewModel.provideHint()
            }
            val actionResult = viewModel.logicalHintResult.value as LogicalHintResult.Available
            assertEquals(HintDetailLevel.ACTION, actionResult.hint.detailLevel)
            val longestText = LogicalHintTextFormatter(activity).format(actionResult.hint)
            val actionSnackbar = waitForSnackbar(activity)
            val actionMessage = actionSnackbar.view.findViewById<TextView>(
                com.google.android.material.R.id.snackbar_text
            )
            assertEquals(longestText, actionMessage.text.toString())
            onMain {
                actionMessage.setTextSize(
                    TypedValue.COMPLEX_UNIT_PX,
                    actionMessage.textSize * 1.3f
                )
                actionSnackbar.view.layoutParams = actionSnackbar.view.layoutParams.apply {
                    width = dp(activity, 240)
                }
                actionSnackbar.view.requestLayout()
            }
            waitUntilTextLaidOut(actionMessage)
            assertEquals(Int.MAX_VALUE, actionMessage.maxLines)
            assertTrue(actionMessage.lineCount > 1)
            for (line in 0 until actionMessage.lineCount) {
                assertEquals(0, actionMessage.layout.getEllipsisCount(line))
            }
        } finally {
            close(activity)
        }
    }

    @Test
    fun activityRecreationKeepsBoardAndElapsedTime() {
        val activity = launchGame()
        var recreated: MainActivity? = null
        try {
            val originalViewModel = viewModel(activity)
            waitUntilReady(originalViewModel)
            val originalBoard = requireNotNull(originalViewModel.sudokuBoard.value).copy()
            onMain {
                setTimerSeconds(activity, 754)
                activity.onTimerUpdate(754, "12:34")
                activity.recreate()
            }

            val recreatedActivity = waitForRecreatedActivity(activity)
            recreated = recreatedActivity
            waitUntilReady(viewModel(recreatedActivity))
            waitForSystemBarsHidden(recreatedActivity.findViewById(R.id.bg))
            assertEquals(originalBoard, viewModel(recreatedActivity).sudokuBoard.value)
            val restoredSeconds = timerSeconds(recreatedActivity)
            assertTrue(restoredSeconds in 754..759)
            assertEquals(
                String.format("%02d:%02d", restoredSeconds / 60, restoredSeconds % 60),
                recreatedActivity.findViewById<TextView>(R.id.timerTextView).text.toString()
            )
        } finally {
            recreated?.let(::close)
            if (!activity.isFinishing) close(activity)
        }
    }

    @Test
    fun pausedGameRemainsPausedAcrossActivityRecreation() {
        val activity = launchGame()
        var recreated: MainActivity? = null
        try {
            waitUntilReady(viewModel(activity))
            onMain {
                activity.onBackPressedDispatcher.onBackPressed()
                activity.recreate()
            }

            val recreatedActivity = waitForRecreatedActivity(activity)
            recreated = recreatedActivity
            waitUntilReady(viewModel(recreatedActivity))
            val restoredPause = requireNotNull(dialog(recreatedActivity, "pauseDialog"))
            waitUntilLaidOut(restoredPause.findViewById(R.id.pauseCard))
            assertTrue(restoredPause.isShowing)
            waitForSystemBarsHidden(requireNotNull(restoredPause.window).decorView)
            assertFalse(timerIsRunning(recreatedActivity))

            onMain {
                restoredPause.findViewById<Button>(R.id.resume_bttn).performClick()
            }
            assertFalse(restoredPause.isShowing)
            assertTrue(timerIsRunning(recreatedActivity))
        } finally {
            recreated?.let(::close)
            if (!activity.isFinishing) close(activity)
        }
    }

    @Test
    fun completedPuzzleAndSavedResumeKeepOneSurfaceWithExistingGameMetrics() {
        val activity = launchGame()
        var resumed: MainActivity? = null
        try {
            val viewModel = viewModel(activity)
            waitUntilReady(viewModel)
            val board = requireNotNull(viewModel.sudokuBoard.value)
            onMain {
                setTimerSeconds(activity, 754)
                activity.onTimerUpdate(754, "12:34")
                for (row in 0 until 9) {
                    for (column in 0 until 9) {
                        if (board.getCell(row, column).isEditable) {
                            viewModel.selectCell(row, column)
                            viewModel.updateSelectedCellValue(
                                requireNotNull(board.solutionValueAt(row, column))
                            )
                        }
                    }
                }
            }

            val completion = requireNotNull(dialog(activity, "completionDialog"))
            waitUntilLaidOut(completion.findViewById(R.id.completionCard))
            assertTrue(completion.isShowing)
            waitForSystemBarsHidden(requireNotNull(completion.window).decorView)
            assertTrue(
                completion.findViewById<View>(R.id.completionDialogScroll) is NestedScrollView
            )
            assertEquals(
                activity.getString(R.string.completion_time, "12:34"),
                completion.findViewById<TextView>(R.id.totalTimeTextView).text.toString()
            )
            assertEquals(
                activity.getString(R.string.completion_hints, 0),
                completion.findViewById<TextView>(R.id.hintsUsedTextView).text.toString()
            )
            assertEquals(
                activity.getString(
                    R.string.completion_difficulty,
                    activity.getString(R.string.menu_easy)
                ),
                completion.findViewById<TextView>(R.id.completionDifficultyTextView)
                    .text
                    .toString()
            )
            assertTrue(
                completion.findViewById<Button>(R.id.nextLevelButton).height >= dp(activity, 48)
            )

            onMain { viewModel.setBoard(board) }
            assertSame(completion, dialog(activity, "completionDialog"))

            close(activity)
            val restoredActivity = launchResume()
            resumed = restoredActivity
            waitUntilReady(viewModel(restoredActivity))
            val restoredCompletion = requireNotNull(
                dialog(restoredActivity, "completionDialog")
            )
            waitUntilLaidOut(restoredCompletion.findViewById(R.id.completionCard))
            waitForSystemBarsHidden(requireNotNull(restoredCompletion.window).decorView)
            assertEquals(
                restoredActivity.getString(R.string.completion_time, "12:34"),
                restoredCompletion.findViewById<TextView>(R.id.totalTimeTextView).text.toString()
            )
        } finally {
            resumed?.let(::close)
            if (!activity.isFinishing) close(activity)
        }
    }

    private fun assertOptionsAccessibility(activity: OptionsActivity, minimumTouchTarget: Int) {
        listOf(R.id.musicSwitch, R.id.soundEffectsSwitch, R.id.autoNotesSwitch).forEach { id ->
            val toggle = activity.findViewById<SwitchCompat>(id)
            assertTrue(toggle.height >= minimumTouchTarget)
            assertTrue(toggle.contentDescription.isNotBlank())
            assertEquals(
                activity.getString(
                    if (toggle.isChecked) R.string.settings_switch_on
                    else R.string.settings_switch_off
                ),
                ViewCompat.getStateDescription(toggle)?.toString()
            )
        }
        assertTrue(activity.findViewById<View>(R.id.optionsScroll) is NestedScrollView)
    }

    private fun launchGame(): MainActivity {
        val intent = Intent(targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(
                GameplayDifficultyAdapter.INTENT_EXTRA,
                GameplayDifficultyAdapter.toExternalValue(SudokuDifficulty.EASY)
            )
        return (instrumentation.startActivitySync(intent) as MainActivity).also {
            waitUntilResumed(it)
        }
    }

    private fun launchResume(): MainActivity {
        val intent = Intent(targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("Resume", true)
        return (instrumentation.startActivitySync(intent) as MainActivity).also {
            waitUntilResumed(it)
        }
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

    private fun waitUntilResumed(activity: Activity) {
        val deadline = SystemClock.elapsedRealtime() + 3_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            instrumentation.waitForIdleSync()
            if ((activity as? MainActivity)?.lifecycle?.currentState
                    ?.isAtLeast(Lifecycle.State.RESUMED) == true
            ) {
                return
            }
            Thread.sleep(20L)
        }
        error("Activity did not resume")
    }

    private inline fun <reified T : Activity> waitForResumedActivity(): T {
        val deadline = SystemClock.elapsedRealtime() + 3_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            instrumentation.waitForIdleSync()
            val activity = onMainResult {
                ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<T>()
                    .firstOrNull()
            }
            if (activity != null) return activity
            Thread.sleep(20L)
        }
        error("${T::class.java.simpleName} did not resume")
    }

    private fun waitForRecreatedActivity(previous: MainActivity): MainActivity {
        val deadline = SystemClock.elapsedRealtime() + 5_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            instrumentation.waitForIdleSync()
            val activity = onMainResult {
                ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<MainActivity>()
                    .firstOrNull { it !== previous }
            }
            if (activity != null) return activity
            Thread.sleep(20L)
        }
        error("MainActivity did not recreate")
    }

    private fun waitForSnackbar(activity: MainActivity): Snackbar {
        val field = MainActivity::class.java.getDeclaredField("hintSnackbar")
            .apply { isAccessible = true }
        val deadline = SystemClock.elapsedRealtime() + 3_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            instrumentation.waitForIdleSync()
            (field.get(activity) as Snackbar?)?.let { return it }
            Thread.sleep(20L)
        }
        error("Hint surface was not rendered")
    }

    private fun waitUntilLaidOut(view: View) {
        val deadline = SystemClock.elapsedRealtime() + 3_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            instrumentation.waitForIdleSync()
            if (view.isLaidOut && view.width > 0 && view.height > 0) return
            Thread.sleep(20L)
        }
        error("View did not complete its first layout pass")
    }

    private fun waitUntilTextLaidOut(view: TextView) {
        val deadline = SystemClock.elapsedRealtime() + 3_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            instrumentation.waitForIdleSync()
            if (view.isLaidOut && view.layout != null && view.lineCount > 0) return
            Thread.sleep(20L)
        }
        error("Text did not complete its layout pass")
    }

    private fun waitForSystemBarsHidden(view: View): WindowInsetsCompat {
        return waitForSystemBars(view, visible = false)
    }

    private fun waitForSystemBarsVisible(view: View): WindowInsetsCompat {
        return waitForSystemBars(view, visible = true)
    }

    private fun waitForSystemBars(view: View, visible: Boolean): WindowInsetsCompat {
        val deadline = SystemClock.elapsedRealtime() + 3_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            instrumentation.waitForIdleSync()
            val insets = ViewCompat.getRootWindowInsets(view)
            val statusBarsMatch =
                insets?.isVisible(WindowInsetsCompat.Type.statusBars()) == visible
            val navigationBarsMatch =
                insets?.isVisible(WindowInsetsCompat.Type.navigationBars()) == visible
            if (insets != null && statusBarsMatch && navigationBarsMatch) {
                return insets
            }
            Thread.sleep(20L)
        }
        error("System bars did not become ${if (visible) "visible" else "hidden"}")
    }

    private fun firstEditable(board: SudokuBoard): Pair<Int, Int> {
        for (row in 0 until 9) {
            for (column in 0 until 9) {
                if (board.getCell(row, column).isEditable) return row to column
            }
        }
        error("Puzzle did not contain an editable cell")
    }

    private fun viewModel(activity: MainActivity): SudokuViewModel =
        MainActivity::class.java.getDeclaredField("viewModel")
            .apply { isAccessible = true }
            .get(activity) as SudokuViewModel

    private fun dialog(activity: MainActivity, fieldName: String): Dialog? =
        MainActivity::class.java.getDeclaredField(fieldName)
            .apply { isAccessible = true }
            .get(activity) as Dialog?

    private fun timerIsRunning(activity: MainActivity): Boolean {
        val timer = MainActivity::class.java.getDeclaredField("timer")
            .apply { isAccessible = true }
            .get(activity)
        return Timer::class.java.getDeclaredField("isRunning")
            .apply { isAccessible = true }
            .getBoolean(timer)
    }

    private fun timerSeconds(activity: MainActivity): Int {
        val timer = MainActivity::class.java.getDeclaredField("timer")
            .apply { isAccessible = true }
            .get(activity) as Timer
        return timer.seconds
    }

    private fun setTimerSeconds(activity: MainActivity, seconds: Int) {
        val timer = MainActivity::class.java.getDeclaredField("timer")
            .apply { isAccessible = true }
            .get(activity) as Timer
        timer.seconds = seconds
    }

    private fun descendants(root: View): Sequence<View> = sequence {
        yield(root)
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                yieldAll(descendants(root.getChildAt(index)))
            }
        }
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun close(activity: MainActivity) {
        onMain { activity.finish() }
        instrumentation.waitForIdleSync()
    }

    private fun onMain(block: () -> Unit) {
        instrumentation.runOnMainSync(block)
    }

    private fun <T> onMainResult(block: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return requireNotNull(result).getOrThrow()
    }
}
