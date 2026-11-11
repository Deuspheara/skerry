package com.twitchplayer.tv.app

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import com.twitchplayer.tv.core.designsystem.theme.TvColors
import com.twitchplayer.tv.core.designsystem.theme.TvMotion

/** Entry metadata: how a destination takes part in screen transitions. */
internal const val NAV_KIND = "twitchplayer.navKind"
internal const val KIND_TAB = "tab"
internal const val KIND_PLAYER = "player"

private fun Scene<NavKey>.kind(): Any? = entries.lastOrNull()?.metadata?.get(NAV_KIND)

/**
 * Screen transitions of the NavDisplay. Deeper (push): the new screen grows in from 96 % with a fade while the old one
 * recedes to 97 % and dims. Back (pop): the mirror, so the direction reads as depth. A rail tab switch, and anything
 * involving the player (which has its own glide in PlayerHost), stays a plain fade. All transform / alpha, so
 * it runs on the render thread; with motion off every spec snaps.
 */
internal fun screenTransition(
    scope: AnimatedContentTransitionScope<Scene<NavKey>>,
    motion: TvMotion,
    pop: Boolean,
): ContentTransform = with(scope) {
    val from = initialState.kind()
    val to = targetState.kind()
    val fadeOnly = from == KIND_PLAYER || to == KIND_PLAYER || (!pop && to == KIND_TAB)
    when {
        fadeOnly -> ContentTransform(fadeIn(motion.medium()), ExitTransition.None, targetContentZIndex = 1f)

        !pop -> ContentTransform(
            // The fade is quick (the screen is opaque, see [screenFill]): the old screen is not seen through the new one for long.
            fadeIn(motion.fast()) + scaleIn(motion.screen(), initialScale = 0.96f),
            fadeOut(motion.screenOut(), targetAlpha = 0.5f) + scaleOut(motion.screenOut(), targetScale = 0.97f),
            targetContentZIndex = 1f,
        )

        // The screen being left stays on top while it grows away and fades; the one below recovers from its dim.
        else -> ContentTransform(
            fadeIn(motion.screen(), initialAlpha = 0.5f) + scaleIn(motion.screen(), initialScale = 0.97f),
            fadeOut(motion.screenOut()) + scaleOut(motion.screenOut(), targetScale = 1.04f),
            targetContentZIndex = 0f,
        )
    }
}

/**
 * Every screen paints its own opaque Body fill, so while one fades in the screen it replaces (or the poster wall it came
 * from) is never seen through it as a transparent ghost. The player entry stays unfilled: the video is drawn behind it.
 */
@Composable
internal fun rememberScreenFill(): NavEntryDecorator<NavKey> = remember {
    NavEntryDecorator(decorate = { entry ->
        if (entry.metadata[NAV_KIND] == KIND_PLAYER) {
            entry.Content()
        } else {
            Box(Modifier.fillMaxSize().background(TvColors.Body)) { entry.Content() }
        }
    })
}
