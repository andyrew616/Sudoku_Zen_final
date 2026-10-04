package rew.lightgames.sudoku2

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReleaseNavigationDeviceTest {
    @Test fun saveReturnAndResumePreservesPuzzle() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
        val previousSave = preferences.getString("saved_game", null)
        val previousDifficulty = preferences.getString("saved_game_difficulty", null)
        try {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(GameplayDifficultyAdapter.INTENT_EXTRA, GameplayDifficultyAdapter.EASY_VALUE)
        ActivityScenario.launch<MainActivity>(intent).use {
            waitForReadyGame()
            onView(withId(R.id.pauseButton)).perform(click())
            val saved = preferences.getString("saved_game", null)
            assertNotNull(saved)
            onView(withId(R.id.exit_bttn)).perform(click())
            onView(withId(R.id.Resume)).check(matches(isDisplayed()))
            assertEquals(saved, preferences.getString("saved_game", null))
            onView(withId(R.id.Resume)).perform(click())
            waitForReadyGame()
            onView(withId(R.id.pauseButton)).perform(click())
            val restored = com.google.gson.Gson().fromJson(preferences.getString("saved_game", null), SavedGameState::class.java)
            val original = com.google.gson.Gson().fromJson(saved, SavedGameState::class.java)
            assertEquals(com.google.gson.Gson().toJson(original.board), com.google.gson.Gson().toJson(restored.board))
            assertEquals(original.hintsUsed, restored.hintsUsed)
            onView(withId(R.id.exit_bttn)).perform(click())
        }
        } finally {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            val edit = preferences.edit()
            if (previousSave == null) edit.remove("saved_game") else edit.putString("saved_game", previousSave)
            if (previousDifficulty == null) edit.remove("saved_game_difficulty")
            else edit.putString("saved_game_difficulty", previousDifficulty)
            edit.commit()
        }
    }

    // Current mainline generates puzzles asynchronously; Espresso alone does not await it.
    private fun waitForReadyGame() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val deadline = SystemClock.elapsedRealtime() + 30_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            var ready = false
            instrumentation.runOnMainSync {
                val activity = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().firstOrNull()
                if (activity != null) {
                    val state = ViewModelProvider(activity)[SudokuViewModel::class.java]
                        .gameplayLoadState.value
                    if (state is GameplayLoadState.Failure) error("Gameplay failed: $state")
                    ready = state is GameplayLoadState.Ready
                }
            }
            if (ready) return
            Thread.sleep(20L)
        }
        error("Timed out waiting for resumed, ready gameplay")
    }

    @Test fun policyIsAccessibleWithoutRequiredConsentOptions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch<OptionsActivity>(Intent(context, OptionsActivity::class.java)).use {
            onView(withId(R.id.privacyPolicyButton)).check(matches(isDisplayed()))
                .check(matches(isFocusable())).check(matches(isClickable()))
            assertEquals("https://andyrew616.github.io/Sudoku_Zen_final/privacy.html",
                context.getString(R.string.privacy_policy_url))
            // Real external-browser navigation is validated on the exact AAB-derived release.
            // This assertion works whether UMP currently requires privacy options or not.
        }
    }
}
