package com.twitchplayer.tv.core.designsystem.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The app's only motion tokens. Durations stay in 150-250 ms; one curve per direction: [EaseOut] for anything
 * entering or changing state, [EaseIn] for anything leaving ([exit]). Animate transform / alpha only (graphicsLayer),
 * never size or position through layout (the one exception: the native video, whose frame follows [glide] in the layout
 * phase, see PlayerScreen). [enabled] is false when the app setting or the system "remove animations"
 * (animator scale 0) turns motion off: every spec then snaps.
 */
@Immutable
class TvMotion(val enabled: Boolean) {
    /** Small state changes: focus, toggles, pills, short scrolls. */
    fun <T> fast(): FiniteAnimationSpec<T> = if (enabled) tween(FAST_MS, easing = EaseOut) else snap()
    /** Default for something appearing: overlays, panels, list scrolls. */
    fun <T> medium(): FiniteAnimationSpec<T> = if (enabled) tween(MEDIUM_MS, easing = EaseOut) else snap()
    /** Large surfaces travelling across the screen (side panels). */
    fun <T> slow(): FiniteAnimationSpec<T> = if (enabled) tween(SLOW_MS, easing = EaseOut) else snap()
    /** Anything leaving: shorter than its entrance, accelerating out. */
    fun <T> exit(): FiniteAnimationSpec<T> = if (enabled) tween(FAST_MS, easing = EaseIn) else snap()
    /** Surfaces that resize or travel with the native video (side chat + picture, mini <-> full screen): long, soft landing. */
    fun <T> glide(): FiniteAnimationSpec<T> = if (enabled) tween(GLIDE_MS, easing = Glide) else snap()
    /** Leaving counterpart of [glide]: shorter, accelerating. */
    fun <T> glideOut(): FiniteAnimationSpec<T> = if (enabled) tween(GLIDE_OUT_MS, easing = GlideIn) else snap()
    /** Pressed-state response of a button or chip: quick in and out, so a tap reads as a tap. */
    fun <T> press(): FiniteAnimationSpec<T> = if (enabled) tween(PRESS_MS, easing = EaseOut) else snap()
    /** A screen pushed or popped over another: [Glide] settle over [SCREEN_MS]. */
    fun <T> screen(): FiniteAnimationSpec<T> = if (enabled) tween(SCREEN_MS, easing = Glide) else snap()
    /** Leaving counterpart of [screen]. */
    fun <T> screenOut(): FiniteAnimationSpec<T> = if (enabled) tween(SCREEN_MS, easing = GlideIn) else snap()
    /** [medium] when [entering], [exit] otherwise: for one value that animates both ways (alpha of an overlay...). */
    fun <T> toward(entering: Boolean): FiniteAnimationSpec<T> = if (entering) medium() else exit()
    val fastMs: Int get() = if (enabled) FAST_MS else 0
    val mediumMs: Int get() = if (enabled) MEDIUM_MS else 0

    companion object {
        const val FAST_MS = 150
        const val MEDIUM_MS = 200
        const val SLOW_MS = 250
        const val GLIDE_MS = 380
        const val GLIDE_OUT_MS = 280
        const val SCREEN_MS = 240
        const val PRESS_MS = 80
        /** Delay between items that enter together; keep the whole sequence under ~100 ms. */
        const val STAGGER_MS = 40
        val EaseOut = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
        val EaseIn = CubicBezierEasing(0.4f, 0f, 1f, 1f)
        /** Emphasized decelerate / accelerate: quick start, long soft settle. */
        val Glide = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
        val GlideIn = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    }
}

val LocalMotion = staticCompositionLocalOf { TvMotion(enabled = true) }

/** True when the system animator scale is 0 (accessibility "remove animations"). */
fun systemAnimationsDisabled(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
