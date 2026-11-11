package com.twitchplayer.phone.feature.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PipPolicyTest {
    @Test fun onlyAPlayingPictureOnThePlayerScreenIsEligible() {
        assertTrue(PipPolicy.eligible(onPlayerScreen = true, hasPicture = true, playing = true, failed = false))
        assertFalse("paused", PipPolicy.eligible(true, true, playing = false, failed = false))
        assertFalse("failed", PipPolicy.eligible(true, true, true, failed = true))
        assertFalse("not started", PipPolicy.eligible(true, hasPicture = false, playing = true, failed = false))
        assertFalse("another screen", PipPolicy.eligible(onPlayerScreen = false, hasPicture = true, playing = true, failed = false))
    }

    @Test fun aspectKeepsNormalVideoAsIs() {
        assertEquals(1920 to 1080, PipPolicy.aspect(1920, 1080))
        assertEquals(1080 to 1920, PipPolicy.aspect(1080, 1920))
    }

    @Test fun unknownSizeIsSixteenByNine() {
        assertEquals(16 to 9, PipPolicy.aspect(0, 0))
        assertEquals(16 to 9, PipPolicy.aspect(-1, 720))
    }

    @Test fun extremeRatiosAreClampedToWhatAndroidAccepts() {
        assertEquals(239 to 100, PipPolicy.aspect(4000, 1000))
        assertEquals(100 to 239, PipPolicy.aspect(1000, 4000))
    }
}
