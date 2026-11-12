package com.twitchplayer.tv.core.data.repository

import com.twitchplayer.tv.core.platform.WallClock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Time that keeps running while the device sleeps. The monotonic clock of the JVM and of Android stops in deep sleep, so a list cached
 * "for a minute" before the phone went in a pocket for an hour would still look fresh when it comes out: elapsed time here is measured
 * on the wall clock. A clock set backwards makes every mark look infinitely old (stale), never fresh.
 */
internal class WallClockTimeSource(private val clock: WallClock = WallClock.System) : TimeSource {
    override fun markNow(): TimeMark = object : TimeMark {
        private val startedAt = clock.nowMillis()

        override fun elapsedNow(): Duration {
            val elapsed = clock.nowMillis() - startedAt
            return if (elapsed < 0) Duration.INFINITE else elapsed.milliseconds
        }
    }
}
