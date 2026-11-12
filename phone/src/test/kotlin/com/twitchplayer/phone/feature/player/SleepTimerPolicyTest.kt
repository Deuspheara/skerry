package com.twitchplayer.phone.feature.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepTimerPolicyTest {
    @Test fun theChoicesAreFifteenToNinetyMinutes() {
        assertEquals(listOf(15, 30, 45, 60, 90), SleepTimerPolicy.PRESETS_MINUTES)
    }

    @Test fun theEndIsTheStartPlusTheMinutes() {
        assertEquals(1_000L + 30 * 60_000L, SleepTimerPolicy.endAt(1_000L, 30))
    }

    @Test fun timeLeftNeverGoesNegativeAndZeroMeansNoTimer() {
        assertEquals(5_000L, SleepTimerPolicy.remainingMs(10_000L, 5_000L))
        assertEquals(0L, SleepTimerPolicy.remainingMs(10_000L, 12_000L))
        assertEquals(0L, SleepTimerPolicy.remainingMs(0L, 12_000L))
    }

    @Test fun theCountdownReadsMinutesAndSecondsThenHours() {
        assertEquals("15:00", SleepTimerPolicy.format(15 * 60_000L))
        assertEquals("0:59", SleepTimerPolicy.format(59_000L))
        assertEquals("1:30:00", SleepTimerPolicy.format(90 * 60_000L))
        assertEquals("1:05:09", SleepTimerPolicy.format((3_600 + 5 * 60 + 9) * 1_000L))
    }

    @Test fun aPartialSecondRoundsUpSoTheDisplayNeverShowsZeroBeforeItStops() {
        assertEquals("0:01", SleepTimerPolicy.format(1L))
        assertEquals("0:01", SleepTimerPolicy.format(999L))
        assertEquals("0:00", SleepTimerPolicy.format(0L))
        assertEquals("0:00", SleepTimerPolicy.format(-5L))
    }

    @Test fun theNextTickWaitsForTheNextWholeSecond() {
        assertEquals(1_000L, SleepTimerPolicy.nextTickMs(60_000L))
        assertEquals(250L, SleepTimerPolicy.nextTickMs(60_250L))
        assertTrue(SleepTimerPolicy.nextTickMs(1_999L) <= 1_000L)
        assertFalse(SleepTimerPolicy.nextTickMs(0L) <= 0L)
    }
}
