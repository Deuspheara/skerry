package com.twitchplayer.phone.feature.player

/** What the player asks the activity to do with the screen orientation. */
enum class OrientationLock { None, Landscape, Portrait }

/**
 * Fullscreen is landscape. The fullscreen button locks the orientation to the other one so the screen turns at once even when the
 * phone is held the other way; the lock is released as soon as the phone's own sensor agrees, so auto-rotate works again and the
 * screen does not flip straight back.
 */
class FullscreenPolicy {
    var lock: OrientationLock = OrientationLock.None
        private set

    /** The fullscreen button: leave landscape for portrait, or enter landscape from portrait. */
    fun toggle(isLandscape: Boolean) {
        lock = if (isLandscape) OrientationLock.Portrait else OrientationLock.Landscape
    }

    /** The player left the screen: orientation is the system's again. */
    fun reset() {
        lock = OrientationLock.None
    }

    /**
     * Sensor reading in degrees (0..359, [UNKNOWN] when flat or unavailable). Returns true when the current lock was released
     * because the phone is now held the way the lock asked for.
     */
    fun onSensor(degrees: Int): Boolean {
        if (degrees == UNKNOWN) return false
        val agrees = when (lock) {
            OrientationLock.None -> false
            OrientationLock.Landscape -> degrees in LANDSCAPE_LEFT || degrees in LANDSCAPE_RIGHT
            OrientationLock.Portrait -> degrees in PORTRAIT_UP || degrees >= PORTRAIT_UP_WRAP || degrees in PORTRAIT_DOWN
        }
        if (agrees) lock = OrientationLock.None
        return agrees
    }

    companion object {
        /** `OrientationEventListener.ORIENTATION_UNKNOWN`. */
        const val UNKNOWN = -1
        private val LANDSCAPE_LEFT = 60..120
        private val LANDSCAPE_RIGHT = 240..300
        private val PORTRAIT_UP = 0..30
        private const val PORTRAIT_UP_WRAP = 330
        private val PORTRAIT_DOWN = 150..210
    }
}
