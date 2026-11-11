package com.twitchplayer.phone.feature.player

import android.graphics.Rect
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * What the player screens need from the activity: Picture-in-Picture, the screen orientation and immersive mode. An interface so the
 * screens (and their UI tests) do not depend on the activity; the real one is [ActivityPlayerHost].
 */
interface PlayerHost {
    /** True while the activity is shown in the Picture-in-Picture window: the screen draws the picture alone. */
    val inPictureInPicture: StateFlow<Boolean>

    /** The device and system allow Picture-in-Picture (Android 8+ and the feature flag). */
    val canPictureInPicture: Boolean

    /**
     * Tells the system what leaving the app should do: with [eligible] (see [PipPolicy]) the window is entered on its own (Android 12+
     * automatically, before that when the user leaves), shaped by [videoWidth] x [videoHeight] and animated from [sourceBounds] (the
     * picture's rectangle in the window, for the transition); [onTogglePlay] answers the window's play / pause button ([playing]
     * decides which one it shows).
     */
    fun updatePictureInPicture(eligible: Boolean, playing: Boolean, videoWidth: Int, videoHeight: Int, sourceBounds: Rect?, onTogglePlay: () -> Unit)

    /** The player left the screen: no automatic Picture-in-Picture any more. */
    fun clearPictureInPicture()

    /** The PiP button: enter the window now, when eligible. */
    fun enterPictureInPicture()

    fun lockOrientation(lock: OrientationLock)

    /**
     * The status bar sits over the black picture area while a player page is open: its icons are drawn light there whatever the theme
     * (dark icons on black disappear). `false` restores what the theme chose.
     */
    fun setVideoSystemBars(enabled: Boolean)

    /** Hides the system bars (swipe from an edge brings them back for a moment) and lets the picture use the display cutout. */
    fun setImmersive(enabled: Boolean)
}

/** Does nothing: previews, tests and any host without an activity. */
object NoPlayerHost : PlayerHost {
    override val inPictureInPicture: StateFlow<Boolean> = MutableStateFlow(false)
    override val canPictureInPicture: Boolean = false
    override fun updatePictureInPicture(eligible: Boolean, playing: Boolean, videoWidth: Int, videoHeight: Int, sourceBounds: Rect?, onTogglePlay: () -> Unit) = Unit
    override fun clearPictureInPicture() = Unit
    override fun enterPictureInPicture() = Unit
    override fun lockOrientation(lock: OrientationLock) = Unit
    override fun setVideoSystemBars(enabled: Boolean) = Unit
    override fun setImmersive(enabled: Boolean) = Unit
}

val LocalPlayerHost = staticCompositionLocalOf<PlayerHost> { NoPlayerHost }
