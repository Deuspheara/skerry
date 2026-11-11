package com.twitchplayer.tv.core.ui

import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.twitchplayer.tv.core.designsystem.theme.LocalMotion
import com.twitchplayer.tv.core.designsystem.theme.TvMotion

/**
 * When a list's data changes (a cached copy replaced by the fresh one, a filter or sort switched, a stream going live or
 * offline): new cards fade in, cards that left fade out and the rest slide to their new place. Placement and alpha only,
 * run by the lazy layout; no motion with animations off.
 */
@Composable
fun Modifier.itemMotion(scope: LazyItemScope): Modifier {
    if (!LocalMotion.current.enabled) return this
    return with(scope) {
        animateItem(
            fadeInSpec = tween(TvMotion.MEDIUM_MS, easing = TvMotion.EaseOut),
            placementSpec = tween(TvMotion.MEDIUM_MS, easing = TvMotion.EaseOut),
            fadeOutSpec = tween(TvMotion.FAST_MS, easing = TvMotion.EaseIn),
        )
    }
}

/** Grid counterpart of [itemMotion]. */
@Composable
fun Modifier.itemMotion(scope: LazyGridItemScope): Modifier {
    if (!LocalMotion.current.enabled) return this
    return with(scope) {
        animateItem(
            fadeInSpec = tween(TvMotion.MEDIUM_MS, easing = TvMotion.EaseOut),
            placementSpec = tween(TvMotion.MEDIUM_MS, easing = TvMotion.EaseOut),
            fadeOutSpec = tween(TvMotion.FAST_MS, easing = TvMotion.EaseIn),
        )
    }
}
