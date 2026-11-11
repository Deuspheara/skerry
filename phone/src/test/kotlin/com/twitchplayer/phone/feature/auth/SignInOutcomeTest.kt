package com.twitchplayer.phone.feature.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class SignInOutcomeTest {
    @Test fun aSessionMeansSuccessWhateverElseHappened() {
        assertEquals(SignInOutcome.Success, signInOutcome(cancelledByViewer = false, signedInAfterwards = true))
        assertEquals(SignInOutcome.Success, signInOutcome(cancelledByViewer = true, signedInAfterwards = true))
    }

    @Test fun cancellingIsNotAnError() {
        assertEquals(SignInOutcome.Cancelled, signInOutcome(cancelledByViewer = true, signedInAfterwards = false))
    }

    @Test fun anythingElseIsAFailure() {
        assertEquals(SignInOutcome.Failed, signInOutcome(cancelledByViewer = false, signedInAfterwards = false))
    }

    @Test fun countdownIsMinutesAndPaddedSeconds() {
        assertEquals("30:00", countdownText(1800))
        assertEquals("29:57", countdownText(1797))
        assertEquals("1:00", countdownText(60))
        assertEquals("0:59", countdownText(59))
        assertEquals("0:05", countdownText(5))
        assertEquals("0:00", countdownText(0))
    }

    @Test fun countdownNeverGoesNegative() {
        assertEquals("0:00", countdownText(-3))
    }
}
