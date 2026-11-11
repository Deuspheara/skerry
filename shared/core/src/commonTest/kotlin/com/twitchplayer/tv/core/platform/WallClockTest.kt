package com.twitchplayer.tv.core.platform

import kotlin.test.Test
import kotlin.test.assertTrue

class WallClockTest {
    @Test
    fun systemClockIsAfter2020() {
        assertTrue(WallClock.System.nowMillis() > EPOCH_2020_MS)
    }

    private companion object {
        const val EPOCH_2020_MS = 1_577_836_800_000L
    }
}
