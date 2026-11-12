package com.twitchplayer.phone.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import com.twitchplayer.phone.ui.theme.LocalReducedMotion

private const val PLACE_MS = 280
private const val FADE_IN_MS = 240
private const val FADE_OUT_MS = 140
private val EaseOut = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val EaseIn = CubicBezierEasing(0.4f, 0f, 1f, 1f)

/**
 * What a list does when its data changes (the cached copy replaced by the fresh one, a filter or sort switched, a stream going live or
 * offline): new cards fade in, cards that left fade out and the others slide to their new place, the way the TV does it. Placement and
 * alpha only, run by the lazy layout (no recomposition per frame). Items need a stable `key`. Nothing moves with reduced motion.
 */
@Composable
@ReadOnlyComposable
fun Modifier.itemMotion(scope: LazyItemScope): Modifier {
    if (LocalReducedMotion.current) return this
    return with(scope) {
        animateItem(
            fadeInSpec = tween(FADE_IN_MS, easing = EaseOut),
            placementSpec = tween(PLACE_MS, easing = EaseOut),
            fadeOutSpec = tween(FADE_OUT_MS, easing = EaseIn),
        )
    }
}

/** Grid counterpart of [itemMotion]. */
@Composable
@ReadOnlyComposable
fun Modifier.itemMotion(scope: LazyGridItemScope): Modifier {
    if (LocalReducedMotion.current) return this
    return with(scope) {
        animateItem(
            fadeInSpec = tween(FADE_IN_MS, easing = EaseOut),
            placementSpec = tween(PLACE_MS, easing = EaseOut),
            fadeOutSpec = tween(FADE_OUT_MS, easing = EaseIn),
        )
    }
}
