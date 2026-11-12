@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.twitchplayer.phone.ui.components

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.twitchplayer.phone.ui.theme.LocalReducedMotion

/** The scope of the shared-element layout around the page display (see PhoneRoot); null in a test or preview that draws a page alone. */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

private const val MORPH_MS = 300
private val MorphEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/**
 * Marks the search pill of a header and the field of the Search page as one element: the pill grows into the field and shrinks back.
 * [key] names the pair (one per tab, so two headers fading into each other never claim the same one). It does nothing with reduced
 * motion, without a shared scope, or outside a page display.
 */
@Composable
fun Modifier.searchMorph(key: String): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    if (LocalReducedMotion.current) return this
    val page = LocalNavAnimatedContentScope.current
    return with(shared) {
        sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = page,
            enter = fadeIn(tween(MORPH_MS / 2)),
            exit = fadeOut(tween(MORPH_MS / 2)),
            boundsTransform = { _, _ -> tween(MORPH_MS, easing = MorphEasing) },
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
            clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(20.dp)),
        )
    }
}
