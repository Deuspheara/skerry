package com.twitchplayer.tv.core.presentation

import com.twitchplayer.tv.core.platform.WallClock

/**
 * When a refreshed list may replace the one on screen. On a TV the viewer moves a focus ring with the D-pad: a list that is swapped under it
 * (cards inserted, reordered or dropped) moves what the ring points at, and a press lands on something else. So while focus is on a row and
 * the viewer pressed a key less than [quietMs] ago, the new list waits; once they stop (or focus leaves the rows) it is applied. A screen
 * nobody touches, or one without a focus ring (a phone), never waits.
 */
class RefreshHold(
    private val clock: WallClock = WallClock.System,
    private val quietMs: Long = QUIET_MS,
) {
    private var lastInteractionMs = NEVER

    /** The viewer pressed a key (any key that reached the screen). */
    fun interacted() {
        lastInteractionMs = clock.nowMillis()
    }

    /** How much longer the viewer must stay still before a refreshed list is applied: 0 when they already have. */
    fun remainingMs(): Long = if (lastInteractionMs == NEVER) 0L else (quietMs - (clock.nowMillis() - lastInteractionMs)).coerceIn(0L, quietMs)

    /** True while a refreshed list must wait: [focusedOnRow] and the viewer is still active. */
    fun shouldHold(focusedOnRow: Boolean): Boolean = focusedOnRow && remainingMs() > 0L

    companion object {
        /** A few seconds without a key press means the viewer has stopped to look. */
        const val QUIET_MS = 4_000L
        private const val NEVER = Long.MIN_VALUE
    }
}
