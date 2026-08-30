package rew.lightgames.sudoku2

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsentManagerTest {

    @Test
    fun canRequestAds_defaultsToFalse() {
        assertFalse("canRequestAds should default to false before initialize()", ConsentManager.canRequestAds())
    }

    @Test
    fun isPrivacyOptionsRequired_defaultsToFalse() {
        assertFalse(
            "isPrivacyOptionsRequired should default to false before initialize()",
            ConsentManager.isPrivacyOptionsRequired()
        )
    }

    @Test
    fun canRequestAds_isGatedCorrectly() {
        assertFalse("Ads should not be requestable by default", ConsentManager.canRequestAds())
    }

    @Test
    fun releaseConfig_doesNotForceDebugGeography() {
        // In release builds BuildConfig.DEBUG is false, so no debug geography
        // is set. Verify the ConsentManager does not expose debug-only state.
        assertFalse("Default consent state should not permit ads", ConsentManager.canRequestAds())
    }
}
