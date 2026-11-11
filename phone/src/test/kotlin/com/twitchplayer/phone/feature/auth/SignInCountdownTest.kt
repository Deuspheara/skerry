package com.twitchplayer.phone.feature.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignInCountdownTest {
    private class FakeClock(var now: Long = 10_000L) : ElapsedClock {
        override fun elapsedMillis(): Long = now
    }

    private val clock = FakeClock()

    private fun countdown(expiresInSeconds: Int = 1800) = SignInCountdown(SignInCountdown.deadlineFor(expiresInSeconds, clock), clock)

    @Test fun startsWithTheFullLifeOfTheCode() {
        val countdown = countdown(1800)

        assertEquals(1800, countdown.remainingSeconds())
        assertFalse(countdown.expired)
    }

    @Test fun roundsUpSoTheLastSecondReadsOneThenZeroAtTheDeadline() {
        val countdown = countdown(10)

        clock.now += 9_001
        assertEquals(1, countdown.remainingSeconds())
        clock.now += 998
        assertEquals(1, countdown.remainingSeconds())
        clock.now += 1
        assertEquals(0, countdown.remainingSeconds())
        assertTrue(countdown.expired)
    }

    @Test fun aLongPauseIsAccountedForAtTheNextRead() {
        val countdown = countdown(1800)

        // The app sat in the background for 25 minutes: no timer ticked, but the next read is right.
        clock.now += 25 * 60 * 1000L
        assertEquals(300, countdown.remainingSeconds())
        assertEquals("5:00", countdownText(countdown.remainingSeconds()))
    }

    @Test fun staysAtZeroOncePast() {
        val countdown = countdown(5)

        clock.now += 3_600_000L

        assertEquals(0, countdown.remainingSeconds())
        assertTrue(countdown.expired)
    }

    @Test fun theDeadlineCanBeKeptAndRebuiltAfterARecreation() {
        val first = countdown(60)
        clock.now += 20_000

        val rebuilt = SignInCountdown(first.deadlineMillis, clock)

        assertEquals(40, rebuilt.remainingSeconds())
        assertEquals(first.remainingSeconds(), rebuilt.remainingSeconds())
    }

    @Test fun aNegativeLifeIsAnAlreadyExpiredCode() {
        assertTrue(SignInCountdown(SignInCountdown.deadlineFor(-5, clock), clock).expired)
    }

    @Test fun countdownIsMinutesAndPaddedSeconds() {
        assertEquals("30:00", countdownText(1800))
        assertEquals("29:57", countdownText(1797))
        assertEquals("1:00", countdownText(60))
        assertEquals("0:59", countdownText(59))
        assertEquals("0:05", countdownText(5))
        assertEquals("0:00", countdownText(0))
        assertEquals("0:00", countdownText(-3))
    }
}
