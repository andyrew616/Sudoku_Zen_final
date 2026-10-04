package rew.lightgames.sudoku2

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Structural guard: navigation must not depend on an SDK ad callback or load an exit ad. */
class ReleaseNavigationTest {
    @Test fun saveAndReturnHasNoInterstitialPath() {
        val source = File("src/main/java/rew/lightgames/sudoku2/MainActivity.kt").readText()
        val handler = source.substringAfter("exitBtn.setOnClickListener {").substringBefore("\n        }")
        assertEquals("saveGame()\n            dialog.dismiss()\n            exit()", handler.trim())
        assertFalse("Exit placement must not be loaded", source.contains("2976818750"))
        assertFalse(source.contains("mInterstitialAdOnExit"))
        assertTrue("Completion placement remains", source.contains("mInterstitialAdCompletion?.show(this)"))
    }

    @Test fun privacyPolicyRouteIsAlwaysPresent() {
        val layout = File("src/main/res/layout/activity_options.xml").readText()
        assertTrue("Settings needs a separate policy route", layout.contains("@+id/privacyPolicyButton"))
    }
}
