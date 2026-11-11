package com.twitchplayer.phone.feature.auth

import android.os.SystemClock

/**
 * Milliseconds since boot, including time the device slept ([SystemClock.elapsedRealtime]); injectable so the countdown can be
 * tested. Not the wall clock: the viewer changing the date must not move a deadline.
 */
fun interface ElapsedClock {
    fun elapsedMillis(): Long

    companion object {
        val Real = ElapsedClock { SystemClock.elapsedRealtime() }
    }
}

/**
 * The life left of a device code. It is a **deadline** on [clock], read whenever the screen asks, never a counter decremented once a
 * second: a screen that was in the background (timers paused), rotated or recomposed shows the true time left on its next frame.
 * The deadline is a plain number so it can be kept across a configuration change.
 */
class SignInCountdown(val deadlineMillis: Long, private val clock: ElapsedClock) {
    /** Whole seconds left, rounded up (the last second reads 0:01, then 0:00 exactly at the deadline); never negative. */
    fun remainingSeconds(): Int {
        val left = deadlineMillis - clock.elapsedMillis()
        return if (left <= 0L) 0 else ((left + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND).toInt()
    }

    val expired: Boolean get() = remainingSeconds() == 0

    companion object {
        /** A code that lives [expiresInSeconds] from now. */
        fun deadlineFor(expiresInSeconds: Int, clock: ElapsedClock): Long = clock.elapsedMillis() + expiresInSeconds.coerceAtLeast(0) * MILLIS_PER_SECOND
    }
}

/** "m:ss" for the code's remaining life; never negative. */
fun countdownText(remainingSeconds: Int): String {
    val total = remainingSeconds.coerceAtLeast(0)
    val seconds = total % SECONDS_PER_MINUTE
    return "${total / SECONDS_PER_MINUTE}:${seconds.toString().padStart(2, '0')}"
}

private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60
