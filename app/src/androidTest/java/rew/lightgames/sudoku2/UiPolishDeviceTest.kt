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
    fun gameplaySpacingKeepsBoardControlsAndKeypadInOneRhythm() {
        val activity = launchGame()
        try {
            waitUntilReady(viewModel(activity))
            val header = activity.findViewById<View>(R.id.gameplayHeader)
            val board = activity.findViewById<View>(R.id.sudokuBoardView)
            val controls = activity.findViewById<View>(R.id.sudokuControlView)
            val actions = activity.findViewById<View>(R.id.gameplayNotesAction)
            listOf(header, board, controls, actions).forEach(::waitUntilLaidOut)

            assertEquals(dp(activity, 90), header.height)
            assertEquals(dp(activity, 16), board.top - header.bottom)
            assertEquals(dp(activity, 16), controls.top - board.bottom)

            val numberKeys = numberKeys(activity)
            assertEquals(9, numberKeys.size)
            val firstNumberRowTop = numberKeys.minOf { it.topOnScreen() }
            val actionRowBottom = actions.bottomOnScreen()
            assertTrue(firstNumberRowTop - actionRowBottom >= dp(activity, 12))
            assertTrue(firstNumberRowTop - actionRowBottom <= dp(activity, 16))

            val minimumTouchTarget = dp(activity, 48)
            numberKeys.forEach { key ->
                assertTrue(key.height >= minimumTouchTarget)
                assertTrue(key.height <= dp(activity, 64))
            }

            val timeLabel = activity.findViewById<TextView>(R.id.difficultyTimeLabel)
            val timer = activity.findViewById<TextView>(R.id.timerTextView)
            val hints = activity.findViewById<TextView>(R.id.hintsCountTextView)
            val mode = activity.findViewById<View>(R.id.modePill)
            val pause = activity.findViewById<View>(R.id.pauseButton)
            onMain {
                listOf(timeLabel, timer, hints).forEach { text ->
                    text.setTextSize(TypedValue.COMPLEX_UNIT_PX, text.textSize * 1.7f)
                }
            }
            waitForCondition("Header did not expand for enlarged status text") {
                header.height > dp(activity, 90) &&
                    mode.top >= timeLabel.parentView().bottom &&
                    mode.top >= hints.parentView().bottom &&
                    mode.top >= pause.bottom
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
            val ordinaryHintShouldFitWithoutScrolling =
                activity.resources.configuration.screenHeightDp >= 700
            val originalPositions = gameplayAnchorPositions(activity)
            val utilityActions = utilityActions(activity)
            val originalAccessibility = utilityActions.associateWith {
                it.importantForAccessibility
            }
            val originalFocusable = utilityActions.associateWith { it.isFocusable }
            onMain { viewModel.provideHint() }

            val result = viewModel.logicalHintResult.value as LogicalHintResult.Available
            val expectedText = LogicalHintTextFormatter(activity).format(result.hint)
            val snackbar = waitForSnackbar(activity)
            waitForHintOverlay(activity, snackbar)
            assertGameplayAnchorsUnchanged(activity, originalPositions)
            val message = snackbar.view.findViewById<TextView>(
                com.google.android.material.R.id.snackbar_text
            )
            waitForHintTextLayout(message, expectedText)
            assertEquals(expectedText, message.text.toString())
            assertEquals(-1, message.maxLines)
            if (ordinaryHintShouldFitWithoutScrolling) {
                assertFalse(
                    "Technique hint scrolls at normal text: height=${message.height} " +
                        "layoutHeight=${message.layout?.height} lines=${message.lineCount} " +
                        "padding=${message.totalPaddingTop}+${message.totalPaddingBottom} " +
                        "scrollY=${message.scrollY} overlay=${snackbar.view.height}",
                    message.canScrollVertically(1)
                )
            }

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
            assertTrue(snackbar.view.isClickable)
            utilityActions.forEach { action ->
                assertFalse(action.isClickable)
                assertFalse(action.isFocusable)
                assertEquals(
                    View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
                    action.importantForAccessibility
                )
                assertTrue(snackbar.view.screenBounds().intersectedBy(action.screenBounds()))
            }
            val originalOverlayBounds = snackbar.view.screenBounds()
            val hintsUsedAfterTechnique = viewModel.hintsUsed.value

            onMain { message.performClick() }
            waitForCondition("Hint did not progress to evidence") {
                (viewModel.logicalHintResult.value as? LogicalHintResult.Available)
                    ?.hint?.detailLevel == HintDetailLevel.EVIDENCE
            }
            val evidenceSnackbar = waitForSnackbar(activity)
            assertSame(snackbar, evidenceSnackbar)
            val evidenceResult = viewModel.logicalHintResult.value as LogicalHintResult.Available
            val evidenceText = LogicalHintTextFormatter(activity).format(evidenceResult.hint)
            waitForHintTextLayout(message, evidenceText)
            waitForHintOverlay(activity, evidenceSnackbar)
            assertEquals(originalOverlayBounds, evidenceSnackbar.view.screenBounds())
            assertGameplayAnchorsUnchanged(activity, originalPositions)
            if (ordinaryHintShouldFitWithoutScrolling) {
                assertFalse(
                    "Evidence hint scrolls at normal text: height=${message.height} " +
                        "layoutHeight=${message.layout?.height} lines=${message.lineCount} " +
                        "padding=${message.totalPaddingTop}+${message.totalPaddingBottom}",
                    message.canScrollVertically(1)
                )
            }

            onMain { message.performClick() }
            waitForCondition("Hint did not progress to action") {
                (viewModel.logicalHintResult.value as? LogicalHintResult.Available)
                    ?.hint?.detailLevel == HintDetailLevel.ACTION
            }
            val actionResult = viewModel.logicalHintResult.value as LogicalHintResult.Available
            assertEquals(HintDetailLevel.ACTION, actionResult.hint.detailLevel)
            val longestText = LogicalHintTextFormatter(activity).format(actionResult.hint)
            val actionSnackbar = waitForSnackbar(activity)
            assertSame(snackbar, actionSnackbar)
            waitForHintOverlay(activity, actionSnackbar)
            assertEquals(originalOverlayBounds, actionSnackbar.view.screenBounds())
            assertGameplayAnchorsUnchanged(activity, originalPositions)
            assertEquals(hintsUsedAfterTechnique, viewModel.hintsUsed.value)
            val actionMessage = actionSnackbar.view.findViewById<TextView>(
                com.google.android.material.R.id.snackbar_text
            )
            val actionButton = actionSnackbar.view.findViewById<TextView>(
                com.google.android.material.R.id.snackbar_action
            )
            val actionStage = descendants(actionSnackbar.view)
                .filterIsInstance<TextView>()
                .first {
                    it.text.toString() == activity.getString(
                        R.string.gameplay_hint_stage_action
                    )
            }
            waitForHintTextLayout(actionMessage, longestText)
            assertEquals(longestText, actionMessage.text.toString())
            if (ordinaryHintShouldFitWithoutScrolling) {
                assertFalse(actionMessage.canScrollVertically(1))
            }
            onMain {
                listOf(actionMessage, actionStage, actionButton).forEach { hintText ->
                    hintText.setTextSize(
                        TypedValue.COMPLEX_UNIT_PX,
                        hintText.textSize * 1.7f
                    )
                }
                actionSnackbar.view.requestLayout()
            }
            waitForCondition("Hint did not complete its enlarged-text layout") {
                actionMessage.layout != null &&
                    actionMessage.lineCount > 1 &&
                    !actionMessage.screenBounds().intersectedBy(actionButton.screenBounds()) &&
                    !actionStage.screenBounds().intersectedBy(actionButton.screenBounds()) &&
                    !actionStage.screenBounds().intersectedBy(actionMessage.screenBounds())
            }
            waitForHintOverlay(activity, actionSnackbar)
            assertEquals(originalOverlayBounds, actionSnackbar.view.screenBounds())
            assertGameplayAnchorsUnchanged(activity, originalPositions)
            assertEquals(-1, actionMessage.maxLines)
            assertTrue(actionMessage.lineCount > 1)
            for (line in 0 until actionMessage.lineCount) {
                assertEquals(0, actionMessage.layout.getEllipsisCount(line))
            }
            waitForCondition("Compact hint did not settle fully inside the root viewport") {
                val root = activity.findViewById<View>(R.id.bg)
                val rootBounds = Rect()
                val hintBounds = Rect()
                root.getGlobalVisibleRect(rootBounds) &&
                    actionSnackbar.view.getGlobalVisibleRect(hintBounds) &&
                    hintBounds.height() == actionSnackbar.view.height &&
                    rootBounds.contains(hintBounds)
            }
            val rootBounds = activity.findViewById<View>(R.id.bg).visibleBounds()
            val hintBounds = actionSnackbar.view.visibleBounds()
            val actionBounds = actionButton.visibleBounds()
            val stageBounds = actionStage.visibleBounds()
            val messageBounds = actionMessage.visibleBounds()
            assertEquals(actionSnackbar.view.height, hintBounds.height())
            assertTrue(rootBounds.contains(hintBounds))
            assertTrue(hintBounds.contains(actionBounds))
            assertTrue(hintBounds.contains(stageBounds))
            assertTrue(hintBounds.contains(messageBounds))
            assertFalse(messageBounds.intersectedBy(actionBounds))
            assertFalse(stageBounds.intersectedBy(actionBounds))
            assertFalse(stageBounds.intersectedBy(messageBounds))
            assertTrue(actionButton.height >= dp(activity, 48))

            onMain { actionButton.performClick() }
            waitForCondition("Hint dismissal did not restore covered controls") {
                hintSnackbar(activity) == null && utilityActions.all { it.isClickable }
            }
            assertGameplayAnchorsUnchanged(activity, originalPositions)
            utilityActions.forEach { action ->
                assertEquals(originalAccessibility.getValue(action), action.importantForAccessibility)
                assertEquals(originalFocusable.getValue(action), action.isFocusable)
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
        val deadline = SystemClock.elapsedRealtime() + 3_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            instrumentation.waitForIdleSync()
            hintSnackbar(activity)?.let { return it }
            Thread.sleep(20L)
        }
        error("Hint surface was not rendered")
    }

    private fun hintSnackbar(activity: MainActivity): Snackbar? =
        MainActivity::class.java.getDeclaredField("hintSnackbar")
            .apply { isAccessible = true }
            .get(activity) as Snackbar?

    private fun waitForHintTextLayout(message: TextView, expectedText: String) {
        waitForCondition("Hint text did not complete its layout pass") {
            message.text.toString() == expectedText &&
                message.layout?.text?.toString() == expectedText
        }
    }

    private data class GameplayAnchorPositions(
        val header: Rect,
        val board: Rect,
        val controls: Rect,
        val numberKeys: List<Rect>,
        val scrollY: Int,
        val contentPaddingBottom: Int
    )

    private fun gameplayAnchorPositions(activity: MainActivity): GameplayAnchorPositions =
        GameplayAnchorPositions(
            header = activity.findViewById<View>(R.id.gameplayHeader).screenBounds(),
            board = activity.findViewById<View>(R.id.sudokuBoardView).screenBounds(),
            controls = activity.findViewById<View>(R.id.sudokuControlView).screenBounds(),
            numberKeys = numberKeys(activity).map { it.screenBounds() },
            scrollY = activity.findViewById<NestedScrollView>(R.id.gameplayScroll).scrollY,
            contentPaddingBottom = activity.findViewById<View>(R.id.gameplayContent).paddingBottom
        )

    private fun assertGameplayAnchorsUnchanged(
        activity: MainActivity,
        expected: GameplayAnchorPositions
    ) {
        assertEquals(expected, gameplayAnchorPositions(activity))
    }

    private fun utilityActions(activity: MainActivity): List<View> = listOf(
        activity.findViewById(R.id.gameplayNotesAction),
        activity.findViewById(R.id.gameplayHintAction),
        activity.findViewById(R.id.gameplayEraseAction)
    )

    private fun waitForHintOverlay(activity: MainActivity, snackbar: Snackbar) {
        val deadline = SystemClock.elapsedRealtime() + 3_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            instrumentation.waitForIdleSync()
            val boardBounds = activity.findViewById<View>(R.id.sudokuBoardView).screenBounds()
            val keypadTop = numberKeys(activity).minOfOrNull { it.topOnScreen() }
            val hintBounds = snackbar.view.screenBounds()
            if (
                keypadTop != null &&
                hintBounds.top >= boardBounds.bottom &&
                hintBounds.bottom <= keypadTop &&
                hintBounds.width() == boardBounds.width() &&
                snackbar.view.alpha == 1f &&
                snackbar.view.scaleX == 1f &&
                snackbar.view.scaleY == 1f
            ) return
            Thread.sleep(20L)
        }
        val boardBounds = activity.findViewById<View>(R.id.sudokuBoardView).screenBounds()
        val keypadTop = numberKeys(activity).minOfOrNull { it.topOnScreen() }
        val scroll = activity.findViewById<NestedScrollView>(R.id.gameplayScroll)
        error(
            "Hint is not fixed between board and keypad: board=$boardBounds " +
                "hint=${snackbar.view.screenBounds()} keypadTop=$keypadTop " +
                "scrollY=${scroll.scrollY}"
        )
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

    private fun waitForCondition(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 3_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            instrumentation.waitForIdleSync()
            if (condition()) return
            Thread.sleep(20L)
        }
        error(message)
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

    private fun numberKeys(activity: MainActivity): List<Button> {
        val descriptions = (1..9).map {
            activity.getString(R.string.gameplay_number_key, it)
        }.toSet()
        return descendants(activity.findViewById(android.R.id.content))
            .filterIsInstance<Button>()
            .filter { it.contentDescription?.toString() in descriptions }
            .toList()
    }

    private fun View.topOnScreen(): Int {
        val location = IntArray(2)
        getLocationOnScreen(location)
        return location[1]
    }

    private fun View.bottomOnScreen(): Int = topOnScreen() + height

    private fun View.screenBounds(): Rect {
        val location = IntArray(2)
        getLocationOnScreen(location)
        return Rect(location[0], location[1], location[0] + width, location[1] + height)
    }

    private fun Rect.intersectedBy(other: Rect): Boolean = Rect.intersects(this, other)

    private fun View.parentView(): View = parent as View

    private fun View.visibleBounds(): Rect = Rect().also { bounds ->
        assertTrue(getGlobalVisibleRect(bounds))
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
