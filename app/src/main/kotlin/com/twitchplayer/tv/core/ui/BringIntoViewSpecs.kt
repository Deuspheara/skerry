package com.twitchplayer.tv.core.ui

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.twitchplayer.tv.core.designsystem.theme.LocalMotion

/**
 * Vertical lists: the focused row's top is always pinned at [anchor] from the viewport top, so rows
 * land on the same line every time (the validated "same anchor" scroll).
 */
@Composable
fun rememberAnchoredSpec(anchor: Dp): BringIntoViewSpec {
    val motion = LocalMotion.current
    val density = LocalDensity.current
    return remember(anchor, motion, density) {
        val anchorPx = with(density) { anchor.toPx() }
        object : BringIntoViewSpec {
            @Suppress("OVERRIDE_DEPRECATION")
            override val scrollAnimationSpec: AnimationSpec<Float> = motion.medium()
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = offset - anchorPx
        }
    }
}

/**
 * Horizontal rows: a card that is already fully on screen (with room for its focus scale and ring) never scrolls the
 * row, so moving focus between visible cards moves nothing but the focused card. Only a clipped card scrolls the row,
 * and then it lands with [start] breathing room or a [peek] of the next card (so its LIVE tag stays whole).
 */
@Composable
fun rememberRowSpec(start: Dp = 16.dp, peek: Dp = 68.dp): BringIntoViewSpec {
    val motion = LocalMotion.current
    val density = LocalDensity.current
    return remember(start, peek, motion, density) {
        val startPx = with(density) { start.toPx() }
        val peekPx = with(density) { peek.toPx() }
        // Focus scale (4 % of a 200 dp card each side) plus the ring drawn outside the bounds.
        val ringPx = with(density) { 8.dp.toPx() }
        object : BringIntoViewSpec {
            @Suppress("OVERRIDE_DEPRECATION")
            override val scrollAnimationSpec: AnimationSpec<Float> = motion.medium()
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
                val end = offset + size
                if (offset >= ringPx && end <= containerSize - ringPx) return 0f
                val limit = containerSize - peekPx
                return when {
                    offset < startPx -> offset - startPx
                    end > limit -> end - limit
                    else -> 0f
                }
            }
        }
    }
}
