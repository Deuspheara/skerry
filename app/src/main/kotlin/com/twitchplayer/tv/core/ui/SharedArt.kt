package com.twitchplayer.tv.core.ui

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.twitchplayer.tv.core.designsystem.theme.LocalMotion

/** The shared-transition scope around the NavDisplay (null where there is none: dialogs, previews). */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransition = compositionLocalOf<SharedTransitionScope?> { null }

/**
 * Marks an image that travels between two screens: the poster in a row becomes the header art of its page, an avatar
 * becomes the channel header. Both ends use the same [key]; the picture flies between their bounds while the screens
 * cross over. A no-op without a scope, and with motion off (the screens just change).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedArt(key: String): Modifier {
    val scope = LocalSharedTransition.current ?: return this
    if (!LocalMotion.current.enabled) return this
    val visibility = LocalNavAnimatedContentScope.current
    return with(scope) {
        this@sharedArt.sharedElement(rememberSharedContentState(key), animatedVisibilityScope = visibility)
    }
}
