package com.twitchplayer.phone.feature.player

import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.staticCompositionLocalOf
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
    val STRIP_PICTURE_WIDTH = 100.dp

    /** The text and the picture under the stage are gone once the sheet is this far down. */
    private const val BODY_FADE_END = 0.5f
    private const val STRIP_FADE_START = 0.5f
    private const val CONTROLS_FADE_END = 0.15f

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
