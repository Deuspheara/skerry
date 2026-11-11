package com.twitchplayer.phone.feature.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * Whether the player's control layer is showing: a tap toggles it and it hides by itself after [idleMs] without interaction,
 * unless something keeps it up (paused, ended, a drag, an open sheet). Plain state plus one suspending function, so the
 * timing is testable with virtual time.
 */
class ControlsVisibility(private val idleMs: Long = DEFAULT_IDLE_MS, initiallyVisible: Boolean = true) {
    var visible: Boolean by mutableStateOf(initiallyVisible)
        private set

    /** Counts interactions: a new one restarts the hide timer even when [visible] did not change. */
    var interactions: Int by mutableIntStateOf(0)
        private set

    fun show() {
        visible = true
        interactions++
    }

    fun hide() {
        visible = false
    }

    fun toggle() = if (visible) hide() else show()

    /** Keeps the bar alive on any interaction with it (a button press, a drag step). */
    fun touch() {
        interactions++
    }

    /**
     * Waits [idleMs] and hides the bar, unless it is hidden already or [pinned]. Call it from a `LaunchedEffect` keyed on
     * [visible], [interactions] and [pinned]: any change cancels the wait and starts a new one.
     */
    suspend fun autoHide(pinned: Boolean) {
        if (!visible || pinned) return
        delay(idleMs)
        visible = false
    }

    companion object {
        const val DEFAULT_IDLE_MS = 3_500L
    }
}
