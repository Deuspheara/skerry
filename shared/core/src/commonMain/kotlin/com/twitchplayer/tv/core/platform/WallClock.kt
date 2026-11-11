package com.twitchplayer.tv.core.platform

import kotlin.time.Clock

/** Wall clock in epoch milliseconds, injectable so time-dependent code is testable. */
fun interface WallClock {
    fun nowMillis(): Long

    companion object {
        val System: WallClock = WallClock { Clock.System.now().toEpochMilliseconds() }
    }
}
