package com.twitchplayer.phone.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene

/** Entry metadata: tab roots switch with a plain fade, detail pages slide. */
internal const val NAV_KIND = "twitchplayer.phone.navKind"
internal const val KIND_TAB = "tab"

private fun Scene<NavKey>.isTab(): Boolean = entries.lastOrNull()?.metadata?.get(NAV_KIND) == KIND_TAB

/**
 * Screen transitions of the NavDisplay: a detail page slides in a short way over a fade and back out the same way; switching
 * tabs is a fade. With reduced motion every transition snaps.
 */
internal fun phoneTransition(scope: AnimatedContentTransitionScope<Scene<NavKey>>, reducedMotion: Boolean, pop: Boolean): ContentTransform = with(scope) {
    when {
        reducedMotion -> ContentTransform(EnterTransition.None, ExitTransition.None)

        initialState.isTab() && targetState.isTab() -> ContentTransform(fadeIn(tween(FADE_MS)), fadeOut(tween(FADE_MS)))

        !pop -> ContentTransform(
            slideInHorizontally(tween(SLIDE_MS)) { it / SLIDE_DIVISOR } + fadeIn(tween(FADE_MS)),
            fadeOut(tween(FADE_MS), targetAlpha = DIM_ALPHA),
            targetContentZIndex = 1f,
        )

        else -> ContentTransform(
            fadeIn(tween(FADE_MS), initialAlpha = DIM_ALPHA),
            slideOutHorizontally(tween(SLIDE_MS)) { it / SLIDE_DIVISOR } + fadeOut(tween(FADE_MS)),
            targetContentZIndex = 0f,
        )
    }
}

/** Every page paints the opaque surface itself, so a page fading in never shows the one it replaces through it. */
@Composable
internal fun rememberScreenFill(): NavEntryDecorator<NavKey> {
    val color = MaterialTheme.colorScheme.background
    return remember(color) { NavEntryDecorator(decorate = { entry -> Box(Modifier.fillMaxSize().background(color)) { entry.Content() } }) }
}

private const val FADE_MS = 180
private const val SLIDE_MS = 260
private const val SLIDE_DIVISOR = 6
private const val DIM_ALPHA = 0.6f
