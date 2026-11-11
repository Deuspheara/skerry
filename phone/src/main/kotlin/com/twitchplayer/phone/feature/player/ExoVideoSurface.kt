package com.twitchplayer.phone.feature.player

import android.graphics.Color
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/** The picture of a plain Media3 [Player] (replays and clips); the app draws its own controls. A SurfaceView, like the live player's. */
@OptIn(UnstableApi::class)
@Composable
fun ExoVideoSurface(player: Player, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShutterBackgroundColor(Color.BLACK)
                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                isFocusable = false
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                this.player = player
            }
        },
        modifier = modifier,
        onRelease = { it.player = null },
    )
}
