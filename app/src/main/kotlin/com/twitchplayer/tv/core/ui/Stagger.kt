package com.twitchplayer.tv.core.ui

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.twitchplayer.tv.core.designsystem.theme.LocalMotion
import com.twitchplayer.tv.core.designsystem.theme.TvMotion
import kotlinx.coroutines.delay

/**
 * One entrance of a screen or panel: the first [CARDS] cards of its rows and grids fade in and rise 8 dp, each
 * [TvMotion.STAGGER_MS] after the previous one, the whole sequence starting within [WINDOW_MS] of the gate's creation.
 * A card composed later (scrolled into view, recycled) finds the window closed and just appears.
 */
class StaggerGate {
    private val created = SystemClock.uptimeMillis()
    val open: Boolean get() = SystemClock.uptimeMillis() - created < WINDOW_MS

    companion object {
        const val CARDS = 4
        const val WINDOW_MS = 500L

        /** Last card starts here at the latest: the sequence stays inside ~100 ms of delay. */
        const val MAX_DELAY_MS = 100L
    }
}

/** Set by `LoadGate` when its content appears after a loading state; null otherwise (nothing to stagger). */
val LocalStagger = compositionLocalOf<StaggerGate?> { null }

/** Entrance of card [index] (0-based within its row or grid); a no-op beyond the first four, outside a window, or with motion off. */
@Composable
fun Modifier.staggerIn(index: Int, gate: StaggerGate? = LocalStagger.current): Modifier {
    val motion = LocalMotion.current
    val play = remember { gate != null && motion.enabled && index in 0 until StaggerGate.CARDS && gate.open }
    if (!play) return this
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay((index * TvMotion.STAGGER_MS).toLong().coerceAtMost(StaggerGate.MAX_DELAY_MS))
        progress.animateTo(1f, motion.medium())
    }
    return graphicsLayer {
        val p = progress.value
        alpha = p
        translationY = (1f - p) * 8.dp.toPx()
        // No offscreen layer: each card part fades on its own.
        compositingStrategy = CompositingStrategy.ModulateAlpha
    }
}
