package com.twitchplayer.phone.feature.player

import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * What a player page needs to know about the sheet that hosts it. [progress] is 0 while the sheet is expanded and 1 once it rests
 * as the strip; it is read **only** in layout, placement and draw lambdas, so a drag never recomposes a page.
 *
 * Without a host (a UI test or a preview that draws a page on its own) there is no sheet and the page behaves as expanded.
 */
@Stable
class PlayerSheet(
    val progress: () -> Float,
    private val stripState: State<Boolean>,
    private val bodyState: State<Boolean>,
    private val stripVisibleState: State<Boolean>,
    val minimise: () -> Unit,
    val expand: () -> Unit,
    val close: () -> Unit,
) {
    /** The sheet rests as the strip (or is on its way there): system bars, orientation and Picture-in-Picture belong to the app again. */
    val strip: Boolean get() = stripState.value

    /** The part under the picture (chat, details) is worth composing: not while the sheet rests as the strip. */
    val bodyVisible: Boolean get() = bodyState.value

    /** The strip's own content (title, play / pause, close) is worth composing: the sheet rests as the strip or is being dragged towards it. */
    val stripVisible: Boolean get() = stripVisibleState.value
}

val LocalPlayerSheet = staticCompositionLocalOf<PlayerSheet?> { null }

/** Pure arithmetic of the drag, so the numbers are testable without a device. */
object SheetGeometry {
    val STRIP_HEIGHT = 56.dp

    /** The strip grows with the system font (a 2-line title and subtitle must not be cut) up to this multiple of [STRIP_HEIGHT]. */
    private const val MAX_STRIP_FONT_SCALE = 1.6f
    private const val PICTURE_ASPECT = 16f / 9f

    /** The text and the picture under the stage are gone once the sheet is this far down. */
    private const val BODY_FADE_END = 0.5f
    private const val STRIP_FADE_START = 0.5f
    private const val CONTROLS_FADE_END = 0.15f

    /** The portrait picture is never taller than this share of the sheet (the chat, the stream info and the keyboard need the rest). */
    private const val MAX_PICTURE_HEIGHT_FRACTION = 0.42f

    /**
     * The expanded portrait picture's width / height. A 16:9 stream (and anything wider) keeps 16:9; a narrower one (4:3, or a vertical
     * broadcast) gets a taller picture so it is not a thin strip with black sides, up to [MAX_PICTURE_HEIGHT_FRACTION] of the
     * [containerHeightPx]. Unknown size (nothing decoded yet): 16:9.
     */
    fun pictureAspect(videoWidth: Int, videoHeight: Int, widthPx: Int, containerHeightPx: Int): Float {
        if (minOf(videoWidth, videoHeight) <= 0 || minOf(widthPx, containerHeightPx) <= 0) return PICTURE_ASPECT
        val natural = videoWidth.toFloat() / videoHeight
        val tallest = widthPx / (containerHeightPx * MAX_PICTURE_HEIGHT_FRACTION)
        return natural.coerceAtLeast(tallest).coerceAtMost(PICTURE_ASPECT)
    }

    fun stripHeight(fontScale: Float): Dp = STRIP_HEIGHT * fontScale.coerceIn(1f, MAX_STRIP_FONT_SCALE)

    /** The small picture of the strip is 16:9 and as tall as the strip. */
    fun stripPictureWidth(stripHeight: Dp): Dp = stripHeight * PICTURE_ASPECT

    fun progress(offsetPx: Float, collapsedPx: Float): Float = if (collapsedPx <= 0f) 1f else (offsetPx / collapsedPx).coerceIn(0f, 1f)

    fun lerp(from: Int, to: Int, progress: Float): Int = (from + (to - from) * progress).toInt()

    fun bodyAlpha(progress: Float): Float = (1f - progress / BODY_FADE_END).coerceIn(0f, 1f)

    fun stripAlpha(progress: Float): Float = ((progress - STRIP_FADE_START) / (1f - STRIP_FADE_START)).coerceIn(0f, 1f)

    /** The controls over the picture vanish at the very start of a drag: they are not drawn at strip size. */
    fun controlsAlpha(progress: Float): Float = (1f - progress / CONTROLS_FADE_END).coerceIn(0f, 1f)

    /** Where the sheet is placed for a drag [offsetPx]: the strip sits above the chrome; beyond that it slides off the bottom. */
    fun collapsedOffset(containerHeightPx: Int, bottomChromePx: Int, stripHeightPx: Int): Int = (containerHeightPx - bottomChromePx - stripHeightPx).coerceAtLeast(0)

    /** The sheet is [stripHeightPx] tall once collapsed, the whole [containerHeightPx] when expanded, linearly between. */
    fun sheetHeight(containerHeightPx: Int, stripHeightPx: Int, progress: Float): Int = lerp(containerHeightPx, stripHeightPx, progress)
}

/** The strip's height at the viewer's font size. */
@androidx.compose.runtime.Composable
@androidx.compose.runtime.ReadOnlyComposable
fun rememberStripHeight(): Dp = SheetGeometry.stripHeight(androidx.compose.ui.platform.LocalDensity.current.fontScale)

/**
 * What a downward swipe of the strip does. Closing the player by accident (a flick while scrolling the page under it, a thumb resting
 * on it) is worse than needing a firm gesture: a drag has to carry the strip most of the way off the screen, or the flick has to be fast.
 */
object DismissPolicy {
    /** A flick at least this fast (dp per second) closes the strip whatever the distance. */
    const val DELIBERATE_FLICK_DP_PER_SECOND = 2_000f

    /** Without a fast flick the strip has to be dragged this far toward the bottom edge. */
    const val DISMISS_DISTANCE_FRACTION = 0.65f

    /** The distance threshold for travelling [totalDistance] px between two anchors, with a container [containerHeight] px tall. */
    fun positionalThreshold(totalDistance: Float, containerHeight: Float, minimiseFraction: Float): Float = totalDistance * if (totalDistance < containerHeight / 2f) DISMISS_DISTANCE_FRACTION else minimiseFraction

    /** The fling velocity (px/s, positive = down) the settle should use: a slow downward flick from the strip counts as no fling at all. */
    fun settleVelocity(fromStrip: Boolean, velocityPxPerSecond: Float, density: Float): Float = if (fromStrip && velocityPxPerSecond > 0f && velocityPxPerSecond < DELIBERATE_FLICK_DP_PER_SECOND * density) 0f else velocityPxPerSecond
}
