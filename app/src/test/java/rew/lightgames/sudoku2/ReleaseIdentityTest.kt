package rew.lightgames.sudoku2

import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseIdentityTest {
    @Test fun applicationIdentityMatchesNewPlayListing() {
        assertEquals("rew.lightgames.zensudoku", BuildConfig.APPLICATION_ID)
    }

    @Test fun firstReleaseVersionIsOnePointZero() {
        assertEquals("1.0", BuildConfig.VERSION_NAME)
    }

    @Test fun firstReleaseVersionCodeIsOne() {
        assertEquals(1, BuildConfig.VERSION_CODE)
    }
}
