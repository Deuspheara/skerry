package com.twitchplayer.phone.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection

private enum class Slot { Fill, Stage, Side, Body, Strip }

private const val VIDEO_WIDTH_BY_HEIGHT = 16f / 9f

/**
 * The one layout of every player mode. The picture ([stage]) is always the same child in the same place of the composition, so
 * turning the phone or entering the Picture-in-Picture window changes where it is placed and how big it is, and never gives the
 * picture view a new parent (which would recreate it and flash). Only the optional children come and go.
 *
 * In portrait the sheet's [progress] moves it between expanded (picture 16:9 under the status bar, [body] below) and the strip
 * (a small picture at the start, [strip] beside it). The progress is read here, in measure and placement, never in composition.
 */
@Composable
internal fun PlayerSheetLayout(
    mode: SheetMode,
    progress: () -> Float,
    statusBarPx: Int,
    stripHeightPx: Int,
    stripPictureWidthPx: Int,
    stage: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    side: (@Composable () -> Unit)? = null,
    body: (@Composable () -> Unit)? = null,
    strip: (@Composable () -> Unit)? = null,
) {
    Layout(
        content = {
            Box(Modifier.layoutId(Slot.Fill).background(Color.Black))
            Box(Modifier.layoutId(Slot.Stage)) { stage() }
            if (side != null) Box(Modifier.layoutId(Slot.Side)) { side() }
            if (body != null) Box(Modifier.layoutId(Slot.Body)) { body() }
            if (strip != null) Box(Modifier.layoutId(Slot.Strip)) { strip() }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        fun slot(which: Slot) = measurables.firstOrNull { it.layoutId == which }

        val fillItem = slot(Slot.Fill)
        val stageItem = slot(Slot.Stage)
        val sideItem = slot(Slot.Side)
        val bodyItem = slot(Slot.Body)
        val stripItem = slot(Slot.Strip)

        when (mode) {
            SheetMode.Window -> {
                val picture = stageItem?.measure(Constraints.fixed(width, height))
                layout(width, height) { picture?.place(0, 0) }
            }

            SheetMode.Landscape -> {
                val sidePlaceable = sideItem?.measure(Constraints(maxWidth = width, maxHeight = height))
                val sideWidth = sidePlaceable?.width ?: 0
                val picture = stageItem?.measure(Constraints.fixed(width - sideWidth, height))
                val background = fillItem?.measure(Constraints.fixed(width, height))
                layout(width, height) {
                    background?.place(0, 0)
                    val rtl = layoutDirection == LayoutDirection.Rtl
                    picture?.place(if (rtl) sideWidth else 0, 0)
                    sidePlaceable?.place(if (rtl) 0 else width - sideWidth, 0)
                }
            }

            SheetMode.Portrait -> {
                val p = progress()
                val expandedPicture = (width / VIDEO_WIDTH_BY_HEIGHT).toInt()
                val pictureWidth = SheetGeometry.lerp(width, stripPictureWidthPx, p)
                val pictureHeight = SheetGeometry.lerp(expandedPicture, stripHeightPx, p)
                val top = SheetGeometry.lerp(statusBarPx, 0, p)
                val background = fillItem?.measure(Constraints.fixed(pictureWidth, top + pictureHeight))
                val picture = stageItem?.measure(Constraints.fixed(pictureWidth, pictureHeight))
                val bodyTop = top + pictureHeight
                val bodyAlpha = SheetGeometry.bodyAlpha(p)
                val bodyPlaceable = if (bodyItem != null && bodyAlpha > 0f) bodyItem.measure(Constraints.fixed(width, (height - bodyTop).coerceAtLeast(0))) else null
                val stripAlpha = SheetGeometry.stripAlpha(p)
                val stripPlaceable = if (stripItem != null && stripAlpha > 0f) stripItem.measure(Constraints.fixed((width - pictureWidth).coerceAtLeast(0), stripHeightPx)) else null
                // Relative placement: in a right-to-left language the picture sits at the right edge and the strip's text to its left.
                layout(width, height) {
                    background?.placeRelative(0, 0)
                    picture?.placeRelative(0, top)
                    bodyPlaceable?.placeRelativeWithLayer(0, bodyTop) { alpha = bodyAlpha }
                    stripPlaceable?.placeRelativeWithLayer(pictureWidth, 0) { alpha = stripAlpha }
                }
            }
        }
    }
}
