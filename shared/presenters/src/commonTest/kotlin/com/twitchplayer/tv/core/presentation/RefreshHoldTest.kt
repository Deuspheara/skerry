package com.twitchplayer.tv.core.presentation

import com.twitchplayer.tv.core.platform.WallClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RefreshHoldTest {
    private var now = 1_000_000L
    private val hold = RefreshHold(WallClock { now }, quietMs = 4_000)

    @Test fun nobodyTouchedItSoNothingWaits() {
        assertFalse(hold.shouldHold(focusedOnRow = true))
        assertEquals(0L, hold.remainingMs())
    }

    @Test fun aRefreshWaitsWhileFocusIsOnARowAndTheViewerIsActive() {
        hold.interacted()
        now += 1_000
        assertTrue(hold.shouldHold(focusedOnRow = true))
        assertEquals(3_000L, hold.remainingMs())
    }

    @Test fun itIsAppliedOnceTheViewerHasBeenStillForTheQuietTime() {
        hold.interacted()
        now += 4_000
        assertFalse(hold.shouldHold(focusedOnRow = true))
        assertEquals(0L, hold.remainingMs())
    }

    @Test fun anotherKeyPressRestartsTheWait() {
        hold.interacted()
        now += 3_500
        hold.interacted()
        now += 3_500
        assertTrue(hold.shouldHold(focusedOnRow = true))
        assertEquals(500L, hold.remainingMs())
    }

    @Test fun withoutFocusOnARowAFreshListIsAppliedAtOnce() {
        hold.interacted()
        assertFalse(hold.shouldHold(focusedOnRow = false), "no focus ring to disturb")
    }

    @Test fun aClockSetBackwardsNeverHoldsLongerThanTheQuietTime() {
        hold.interacted()
        now -= 60_000
        assertEquals(4_000L, hold.remainingMs())
    }
}
