package com.twitchplayer.tv.core.ui

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.twitchplayer.tv.core.designsystem.theme.TvColors
import com.twitchplayer.tv.core.designsystem.theme.TvDimens

/**
 * Animated 0..1 focus amount. Read it only inside draw / layer lambdas so focus changes never
 * trigger recomposition or relayout.
 */
@Stable
class FocusProgress internal constructor(private val read: () -> Float, private val readPressed: () -> Float = { 0f }) {
    val value: Float get() = read()

    /** Animated 0..1 press amount (OK / center held down). */
    val pressed: Float get() = readPressed()
}

/**
 * The single focus ring of the app: a dark gap then a #BF94FF ring, drawn outside the bounds so it
 * never shifts layout. The ring fades with [progress]; [strength] < 1 dims it (disabled controls). Parents must leave
 * ~4 dp of room and must not clip or alpha-layer this node (an alpha layer is an offscreen buffer the size of the bounds).
 */
fun Modifier.focusRing(progress: FocusProgress, shape: CornerBasedShape, strength: Float = 1f, thick: Boolean = false): Modifier = drawWithContent {
    drawContent()
    val p = progress.value * strength
    if (p > 0f) drawFocusRing(shape, p, thick = thick)
}

/**
 * Reduced-motion focus cue: the element gets lighter (a white veil over it, clipped to [shape]) instead of growing.
 * Used with a [thick] ring when animations are off, so focus never depends on movement.
 */
fun Modifier.focusLift(progress: FocusProgress, shape: Shape): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    onDrawWithContent {
        drawContent()
        val p = progress.value
        if (p > 0f) drawOutline(outline, Color.White, alpha = FOCUS_LIFT * p)
    }
}

private const val FOCUS_LIFT = 0.10f

/**
 * Focus background: [color] faded in with [progress], filling [shape] (a plain rect would square off rounded corners).
 */
fun Modifier.focusFill(progress: FocusProgress, color: Color, shape: Shape): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    onDrawBehind {
        val p = progress.value
        if (p > 0f) drawOutline(outline, color, alpha = p)
    }
}

/** Tonal response to a press: [color] over the element, clipped to [shape], faded by [FocusProgress.pressed]. */
fun Modifier.pressTone(progress: FocusProgress, shape: Shape, color: Color): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    onDrawWithContent {
        drawContent()
        val p = progress.pressed
        if (p > 0f) drawOutline(outline, color, alpha = p)
    }
}

/**
 * The ring around a [width]-wide part of the bounds, from the start edge (the end edge when [fromEnd]): for elements
 * whose visible width animates in the draw phase while their layout size stays put.
 */
fun DrawScope.drawFocusRing(shape: CornerBasedShape, alpha: Float, width: Float = size.width, fromEnd: Boolean = false, thick: Boolean = false) {
    val gap = TvDimens.FocusGap.toPx()
    val ring = (TvDimens.FocusRing + if (thick) TvDimens.FocusRingReduced else 0.dp).toPx()
    val radius = shape.topStart.toPx(size, this)
    val left = if (fromEnd) size.width - width else 0f
    // Dark gap (body color) hugging the element, then the light-purple ring outside it.
    outline(left, width, radius, gap / 2f, gap, TvColors.Body, alpha)
    outline(left, width, radius, gap + ring / 2f, ring, TvColors.Focus, alpha)
}

private fun DrawScope.outline(left: Float, w: Float, radius: Float, offset: Float, width: Float, color: androidx.compose.ui.graphics.Color, alpha: Float) {
    drawRoundRect(
        color = color,
        topLeft = Offset(left - offset, -offset),
        size = Size(w + 2 * offset, size.height + 2 * offset),
        cornerRadius = CornerRadius(radius + offset),
        style = Stroke(width),
        alpha = alpha,
    )
}
