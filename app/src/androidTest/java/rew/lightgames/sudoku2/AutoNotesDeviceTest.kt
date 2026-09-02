package rew.lightgames.sudoku2

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutoNotesDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val targetContext = instrumentation.targetContext
    private val preferences = targetContext.getSharedPreferences(
        MainActivity.PREF_NAME,
        Context.MODE_PRIVATE
    )

    @Before
    fun defaultAutoNotesOff() {
        preferences.edit().remove(MainActivity.PREF_AUTO_NOTES).commit()
    }

    @After
    fun clearAutoNotesPreference() {
        preferences.edit().remove(MainActivity.PREF_AUTO_NOTES).commit()
    }

    @Test
    fun autoNotesMode_preservesManualNotesAndMakesEditingContractExplicit() {
        val activity = launch(SudokuDifficulty.EASY)
        try {
            val viewModel = viewModel(activity)
            waitUntilReady(viewModel)
            val board = requireNotNull(viewModel.sudokuBoard.value)
            val blanks = editableBlanks(board)
            val manualCell = blanks.first()

            onMain {
                viewModel.selectCell(manualCell.row, manualCell.column)
                viewModel.setNotesMode(true)
                viewModel.updateSelectedCellValue(2)
                viewModel.updateSelectedCellValue(7)
            }
            assertEquals(listOf(2, 7), board.getCell(manualCell.row, manualCell.column).notes.sorted())
            assertEquals(
                manualDescription(activity, manualCell, listOf(2, 7)),
                cellDescription(activity, manualCell)
            )

            setAutoNotes(true)
            waitForMode(activity, activity.getString(R.string.gameplay_auto_notes_mode))
            assertEquals(listOf(2, 7), board.getCell(manualCell.row, manualCell.column).notes.sorted())
            val automatic = available(board)
            assertEquals(
                autoDescription(activity, manualCell, automatic.candidatesAt(manualCell.row, manualCell.column)),
                cellDescription(activity, manualCell)
            )

            val disabledDescription = activity.getString(R.string.gameplay_manual_notes_unavailable)
            val notesControl = findByContentDescription(
                activity.findViewById(android.R.id.content),
                disabledDescription
            )
            assertNotNull("Manual Notes control should explain why it is unavailable", notesControl)
            onMain { requireNotNull(notesControl).performClick() }

            val valueCell = blanks.first { it != manualCell }
            val value = requireNotNull(board.solutionValueAt(valueCell.row, valueCell.column))
            onMain {
                viewModel.selectCell(valueCell.row, valueCell.column)
                viewModel.updateSelectedCellValue(value)
            }
            assertEquals(value, board.getCell(valueCell.row, valueCell.column).number)
            assertTrue(board.getCell(valueCell.row, valueCell.column).notes.isEmpty())

            setAutoNotes(false)
            waitForMode(activity, activity.getString(R.string.gameplay_normal_mode))
            assertEquals(
                manualDescription(activity, manualCell, listOf(2, 7)),
                cellDescription(activity, manualCell)
            )
            assertEquals(listOf(2, 7), board.getCell(manualCell.row, manualCell.column).notes.sorted())
        } finally {
            close(activity)
        }
    }

    @Test
    fun placedValueRemovesPeerCandidateAndDeletionRestoresItWithoutInputLag() {
        setAutoNotes(true)
        val activity = launch(SudokuDifficulty.EASY)
        try {
            val viewModel = viewModel(activity)
            waitUntilReady(viewModel)
            val board = requireNotNull(viewModel.sudokuBoard.value)
            val before = available(board)
            val fixture = findPeerFixture(board, before)
            val peerBefore = before.candidatesAt(fixture.peer.row, fixture.peer.column)
            assertTrue(fixture.digit in peerBefore)
            assertEquals(
                autoDescription(activity, fixture.peer, peerBefore),
                cellDescription(activity, fixture.peer)
            )

            onMain {
                viewModel.selectCell(fixture.source.row, fixture.source.column)
                viewModel.updateSelectedCellValue(fixture.digit)
            }
            val afterPlacement = available(board)
            val peerAfterPlacement = afterPlacement.candidatesAt(fixture.peer.row, fixture.peer.column)
            assertFalse(fixture.digit in peerAfterPlacement)
            assertEquals(
                autoDescription(activity, fixture.peer, peerAfterPlacement),
                cellDescription(activity, fixture.peer)
            )

            onMain { viewModel.updateSelectedCellValue(0) }
            val afterDeletion = available(board)
            assertEquals(
                peerBefore,
                afterDeletion.candidatesAt(fixture.peer.row, fixture.peer.column)
            )
            assertEquals(
                autoDescription(activity, fixture.peer, peerBefore),
                cellDescription(activity, fixture.peer)
            )

            repeat(5) {
                onMain { viewModel.updateSelectedCellValue(fixture.digit) }
                onMain { viewModel.updateSelectedCellValue(0) }
            }
            val entrySamples = ArrayList<Long>()
            repeat(30) {
                val placeStarted = SystemClock.elapsedRealtimeNanos()
                onMain { viewModel.updateSelectedCellValue(fixture.digit) }
                entrySamples += SystemClock.elapsedRealtimeNanos() - placeStarted
                val deleteStarted = SystemClock.elapsedRealtimeNanos()
                onMain { viewModel.updateSelectedCellValue(0) }
                entrySamples += SystemClock.elapsedRealtimeNanos() - deleteStarted
            }
            val p95 = percentileMillis(entrySamples)
            println(
                "PR12 DEVICE AUTO NOTES valueEntryP95Ms=${decimal(p95)} " +
                    "samples=${entrySamples.size}"
            )
            assertTrue("Auto Notes value-entry p95 exceeded 50 ms: $p95", p95 < 50.0)
        } finally {
            close(activity)
        }
    }

    @Test
    fun autoNotesAcrossDifficultiesRemainBasicAndHintsStayIdentical() {
        setAutoNotes(true)
        for (difficulty in playableDifficulties) {
            val activity = launch(difficulty)
            try {
                val viewModel = viewModel(activity)
                waitUntilReady(viewModel)
                val board = requireNotNull(viewModel.sudokuBoard.value)
                val valuesBefore = board.playerValues()
                val candidates = available(board)
                val firstBlank = editableBlanks(board).first()
                assertEquals(
                    autoDescription(
                        activity,
                        firstBlank,
                        candidates.candidatesAt(firstBlank.row, firstBlank.column)
                    ),
                    cellDescription(activity, firstBlank)
                )

                val expectedHint = LogicalHintProvider().hintFor(
                    playerValues = valuesBefore,
                    authoritativeSolution = board.solutionValues(),
                    detailLevel = HintDetailLevel.TECHNIQUE
                )
                onMain { viewModel.provideHint() }

                assertEquals(expectedHint, viewModel.logicalHintResult.value)
                assertArrayEquals(valuesBefore, board.playerValues())
                assertEquals(1, viewModel.hintsUsed.value)
            } finally {
                close(activity)
            }
        }
    }

    @Test
    fun resumePreservesManualNotesAndSettingThenRecomputesAutomaticCandidates() {
        val original = launch(SudokuDifficulty.EASY)
        val originalViewModel = viewModel(original)
        waitUntilReady(originalViewModel)
        val originalBoard = requireNotNull(originalViewModel.sudokuBoard.value)
        val notedCell = editableBlanks(originalBoard).first()
        onMain {
            originalViewModel.selectCell(notedCell.row, notedCell.column)
            originalViewModel.setNotesMode(true)
            originalViewModel.updateSelectedCellValue(4)
            originalViewModel.setNotesMode(false)
        }
        setAutoNotes(true)
        close(original)

        val resumed = launchResume()
        try {
            val resumedViewModel = viewModel(resumed)
            waitUntilReady(resumedViewModel)
            val board = requireNotNull(resumedViewModel.sudokuBoard.value)
            assertTrue(preferences.getBoolean(MainActivity.PREF_AUTO_NOTES, false))
            assertEquals(listOf(4), board.getCell(notedCell.row, notedCell.column).notes)
            val candidates = available(board).candidatesAt(notedCell.row, notedCell.column)
            assertEquals(
                autoDescription(resumed, notedCell, candidates),
                cellDescription(resumed, notedCell)
            )

            setAutoNotes(false)
            waitForMode(resumed, resumed.getString(R.string.gameplay_normal_mode))
            assertEquals(
                manualDescription(resumed, notedCell, listOf(4)),
                cellDescription(resumed, notedCell)
            )
        } finally {
            close(resumed)
        }
    }

    private fun available(board: SudokuBoard): AutoNotesResult.Available {
        val result = AutoNotesCalculator.compute(board.playerValues(), board.editableCells())
        assertTrue("Expected valid automatic candidates but got $result", result is AutoNotesResult.Available)
        return result as AutoNotesResult.Available
    }

    private fun findPeerFixture(
        board: SudokuBoard,
        candidates: AutoNotesResult.Available
    ): PeerFixture {
        val blanks = editableBlanks(board)
        for (source in blanks) {
            val digit = requireNotNull(board.solutionValueAt(source.row, source.column))
            if (digit !in candidates.candidatesAt(source.row, source.column)) continue
            val peer = blanks.firstOrNull { candidate ->
                candidate != source &&
                    candidate.isPeerOf(source) &&
                    digit in candidates.candidatesAt(candidate.row, candidate.column)
            }
            if (peer != null) return PeerFixture(source, peer, digit)
        }
        error("No editable peer fixture was available")
    }

    private fun editableBlanks(board: SudokuBoard): List<CellRef> = buildList {
        for (row in 0 until 9) {
            for (column in 0 until 9) {
                val cell = board.getCell(row, column)
                if (cell.isEditable && cell.number == 0) add(CellRef(row, column))
            }
        }
    }

    private fun CellRef.isPeerOf(other: CellRef): Boolean =
        row == other.row ||
            column == other.column ||
            row / 3 == other.row / 3 && column / 3 == other.column / 3

    private fun autoDescription(
        activity: MainActivity,
        cell: CellRef,
        candidates: Collection<Int>
    ): String = activity.getString(
        R.string.auto_notes_accessibility_candidates,
        cell.row + 1,
        cell.column + 1,
        candidates.joinToString(activity.getString(R.string.hint_digit_separator))
    )

    private fun manualDescription(
        activity: MainActivity,
        cell: CellRef,
        notes: Collection<Int>
    ): String = activity.getString(
        R.string.manual_notes_accessibility_candidates,
        cell.row + 1,
        cell.column + 1,
        notes.joinToString(activity.getString(R.string.hint_digit_separator))
    )

    private fun cellDescription(activity: MainActivity, cell: CellRef): String? {
        instrumentation.waitForIdleSync()
        return activity.findViewById<SudokuBoardView>(R.id.sudokuBoardView)
            .getCellView(cell.row, cell.column)
            ?.contentDescription
            ?.toString()
    }

    private fun findByContentDescription(root: View, description: String): View? {
        if (root.contentDescription?.toString() == description) return root
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                findByContentDescription(root.getChildAt(index), description)?.let { return it }
            }
        }
        return null
    }

    private fun setAutoNotes(enabled: Boolean) {
        onMain {
            preferences.edit().putBoolean(MainActivity.PREF_AUTO_NOTES, enabled).commit()
        }
        instrumentation.waitForIdleSync()
    }

    private fun waitForMode(activity: MainActivity, expected: String) {
        val mode = activity.findViewById<TextView>(R.id.modeTextView)
        val deadline = SystemClock.elapsedRealtime() + 3_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            instrumentation.waitForIdleSync()
            if (mode.text.toString() == expected) return
            Thread.sleep(20L)
        }
        error("Expected mode '$expected' but was '${mode.text}'")
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

    private fun waitUntilResumed(activity: MainActivity) {
        val deadline = SystemClock.elapsedRealtime() + 3_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            instrumentation.waitForIdleSync()
            if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
            Thread.sleep(20L)
        }
        error("Activity did not resume: ${activity.lifecycle.currentState}")
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

    private fun percentileMillis(values: List<Long>): Double {
        val sorted = values.sorted()
        return sorted[((sorted.size - 1) * 0.95).toInt()] / 1_000_000.0
    }

    private fun decimal(value: Double): String = String.format(Locale.US, "%.3f", value)

    private data class PeerFixture(
        val source: CellRef,
        val peer: CellRef,
        val digit: Int
    )

    private companion object {
        val playableDifficulties = listOf(
            SudokuDifficulty.EASY,
            SudokuDifficulty.MEDIUM,
            SudokuDifficulty.HARD
        )
    }
}
