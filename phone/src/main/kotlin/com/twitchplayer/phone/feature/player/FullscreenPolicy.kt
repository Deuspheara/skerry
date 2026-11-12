package com.twitchplayer.phone.feature.player

/** What the player asks the activity to do with the screen orientation. */
enum class OrientationLock { None, Landscape, Portrait }

/**
 * Fullscreen is landscape. The fullscreen button locks the orientation to the other one so the screen turns at once even when the
 * phone is held the other way.
 *
 * Landscape lock: it is kept while the viewer is in fullscreen (the system still lets the picture turn between the two landscape
 * sides). Releasing it as soon as the sensor "agrees" made the screen flash back to portrait: the app's sensor reading crosses
 * its threshold earlier than the system's own rotation, so the system briefly rotated to portrait before landscape. It is now
 * released only after the phone has really been held sideways and is then held upright again, which is the viewer leaving
 * fullscreen by turning the phone back, the way video apps do it.
 *
 * Portrait lock (leaving fullscreen with the button while the phone is held sideways): released once the phone is upright.
 */
class FullscreenPolicy(initial: OrientationLock = OrientationLock.None) {
    var lock: OrientationLock = initial
        private set

    /** The phone has been held sideways since the landscape lock was set, so turning it upright again means leaving fullscreen. */
    private var seenSideways = false

    /** The fullscreen button: leave landscape for portrait, or enter landscape from portrait. */
    fun toggle(isLandscape: Boolean) {
        lock = if (isLandscape) OrientationLock.Portrait else OrientationLock.Landscape
        seenSideways = false
    }

    /** The player left the screen: orientation is the system's again. */
    fun reset() {
        lock = OrientationLock.None
        seenSideways = false
    }

    /**
     * Sensor reading in degrees (0..359, [UNKNOWN] when flat or unavailable). Returns true when the current lock was released
     * because the phone is now held the way the lock asks for (portrait lock) or was turned back upright (landscape lock).
     */
    fun onSensor(degrees: Int): Boolean {
        if (degrees == UNKNOWN) return false
        val upright = degrees <= UPRIGHT_MAX || degrees >= UPRIGHT_WRAP || degrees in UPSIDE_DOWN
        val released = when (lock) {
            OrientationLock.None -> false

            OrientationLock.Portrait -> upright

            OrientationLock.Landscape -> {
                if (degrees in SIDEWAYS_LEFT || degrees in SIDEWAYS_RIGHT) seenSideways = true
                seenSideways && upright
            }
        }
        if (released) {
            lock = OrientationLock.None
            seenSideways = false
        }
        return released
    }

    companion object {
        /** `OrientationEventListener.ORIENTATION_UNKNOWN`. */
        const val UNKNOWN = -1
        private const val UPRIGHT_MAX = 25
        private const val UPRIGHT_WRAP = 335
        private val UPSIDE_DOWN = 155..205
        private val SIDEWAYS_LEFT = 70..110
        private val SIDEWAYS_RIGHT = 250..290
    }
}
