package rew.lightgames.sudoku2

import android.content.Context
import android.content.Intent
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
        try {
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { _ ->
            onView(withId(R.id.pauseButton)).perform(click())
            val saved = preferences.getString("saved_game", null)
            assertNotNull(saved)
            onView(withId(R.id.exit_bttn)).perform(click())
            onView(withId(R.id.Resume)).check(matches(isDisplayed()))
            assertEquals(saved, preferences.getString("saved_game", null))
            onView(withId(R.id.Resume)).perform(click())
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
            edit.commit()
        }
    }

    @Test fun policyIsAccessibleWithoutRequiredConsentOptions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch<OptionsActivity>(Intent(context, OptionsActivity::class.java)).use {
            onView(withText("Privacy Policy")).check(matches(isDisplayed())).perform(click())
            onView(withText("The privacy policy link is not available yet.")).check(matches(isDisplayed()))
            onView(withText("OK")).perform(click())
        }
    }
}
