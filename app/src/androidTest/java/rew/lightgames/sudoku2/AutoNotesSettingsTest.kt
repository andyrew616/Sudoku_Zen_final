package rew.lightgames.sudoku2

import android.content.Context
import android.content.Intent
import androidx.appcompat.widget.SwitchCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutoNotesSettingsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val targetContext = instrumentation.targetContext
    private val preferences = targetContext.getSharedPreferences(
        MainActivity.PREF_NAME,
        Context.MODE_PRIVATE
    )

    @After
    fun clearAutoNotesPreference() {
        preferences.edit().remove(MainActivity.PREF_AUTO_NOTES).commit()
    }

    @Test
    fun missingPreferenceDefaultsOffAndTogglePersistsAcrossActivityLaunches() {
        preferences.edit().remove(MainActivity.PREF_AUTO_NOTES).commit()

        var activity = launch()
        var toggle = activity.findViewById<SwitchCompat>(R.id.autoNotesSwitch)
        assertFalse(toggle.isChecked)
        assertTrue(toggle.contentDescription.isNotBlank())

        onMain { toggle.isChecked = true }
        assertTrue(preferences.getBoolean(MainActivity.PREF_AUTO_NOTES, false))
        close(activity)

        activity = launch()
        toggle = activity.findViewById(R.id.autoNotesSwitch)
        assertTrue(toggle.isChecked)

        onMain { toggle.isChecked = false }
        assertFalse(preferences.getBoolean(MainActivity.PREF_AUTO_NOTES, true))
        close(activity)

        activity = launch()
        assertFalse(activity.findViewById<SwitchCompat>(R.id.autoNotesSwitch).isChecked)
        close(activity)
    }

    private fun launch(): OptionsActivity {
        val intent = Intent(targetContext, OptionsActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return instrumentation.startActivitySync(intent) as OptionsActivity
    }

    private fun close(activity: OptionsActivity) {
        onMain { activity.finish() }
        instrumentation.waitForIdleSync()
    }

    private fun onMain(block: () -> Unit) {
        instrumentation.runOnMainSync(block)
    }
}
