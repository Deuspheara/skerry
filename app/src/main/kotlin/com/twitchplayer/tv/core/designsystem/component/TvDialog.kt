package com.twitchplayer.tv.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.twitchplayer.tv.core.designsystem.theme.LocalMotion
import com.twitchplayer.tv.core.designsystem.theme.TvColors
import com.twitchplayer.tv.core.designsystem.theme.TvShapes
import kotlinx.coroutines.launch

/**
 * Modal overlay in its own window: it traps D-pad focus, Back dismisses it, and focus returns to the
 * previously focused element afterwards. The backdrop is a flat veil (no blur, per the 144 Hz budget).
 */
@Composable
fun TvDialog(
    description: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 320.dp,
    scrim: Color = TvColors.Scrim,
    /** Visible heading at the top of the dialog (null: none, the dialog's content says what it is). */
    title: String? = null,
    /** Start inset of [title], matching the text of the dialog's rows. */
    titleInset: androidx.compose.ui.unit.Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    val motion = LocalMotion.current
    // Scrim and panel run on their own clocks: the veil settles first (fast in, medium out), the panel rises and scales in
    // over it (medium in, exit out), so the panel is gone before the veil finishes lifting.
    val scrimAlpha = remember { Animatable(0f) }
    val panel = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var leaving by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        launch { scrimAlpha.animateTo(1f, motion.fast()) }
        panel.animateTo(1f, motion.medium())
    }
    // Every way out goes through here: animate out, then tell the caller (which removes the dialog and its window).
    val close = remember(onDismiss, motion) {
        DialogClose { then ->
            if (!leaving) {
                leaving = true
                scope.launch {
                    launch { scrimAlpha.animateTo(0f, motion.medium()) }
                    panel.animateTo(0f, motion.exit())
                    then()
                }
            }
        }
    }
    Dialog(
        onDismissRequest = { close(onDismiss) },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            Modifier.fillMaxSize().graphicsLayer { this.alpha = scrimAlpha.value }.background(scrim),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier
                    .graphicsLayer {
                        val p = panel.value
                        alpha = p
                        scaleX = 0.96f + 0.04f * p
                        scaleY = scaleX
                        translationY = (1f - p) * 8.dp.toPx()
                        // Fade each part on its own instead of rendering the panel offscreen first.
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                    }
                    .width(width)
                    .background(TvColors.Base, TvShapes.Dialog)
                    .border(0.5.dp, TvColors.Divider, TvShapes.Dialog)
                    .padding(24.dp)
                    .semantics {
                        paneTitle = description
                        contentDescription = description
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (title != null) {
                    androidx.tv.material3.Text(title, style = com.twitchplayer.tv.core.designsystem.theme.TvType.Tile, color = TvColors.Text, modifier = Modifier.fillMaxWidth().padding(start = titleInset))
                }
                CompositionLocalProvider(LocalDialogClose provides close) { content() }
            }
        }
    }
}

/** Closes the dialog with its exit animation, then runs [then] (the caller's own dismiss / action). */
fun interface DialogClose {
    operator fun invoke(then: () -> Unit)
}

/** Read inside a [TvDialog]'s content: wrap actions that end the dialog (`close(onCancel)`). Outside a dialog it runs at once. */
val LocalDialogClose = staticCompositionLocalOf { DialogClose { it() } }
