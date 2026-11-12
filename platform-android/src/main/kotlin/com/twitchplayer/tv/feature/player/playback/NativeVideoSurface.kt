package com.twitchplayer.tv.feature.player.playback

import android.graphics.Color
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.twitchplayer.tv.feature.player.data.stream.TwitchPlaybackResolver

/** A [NativeVideoPlayer] that lives as long as the caller composes it (the whole player session) and follows the activity's visibility. */
@Composable
fun rememberNativeVideoPlayer(
    resolver: TwitchPlaybackResolver,
    preferredQuality: () -> String?,
    onQualityPicked: (String) -> Unit,
    qualityLabels: () -> QualityLabels,
    /** See [NativeVideoPlayer]: false for previews that must not take audio focus until the sound is turned on. */
    handleAudioFocus: Boolean = true,
): NativeVideoPlayer {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val player = remember(resolver) { NativeVideoPlayer(context, resolver, scope, preferredQuality, onQualityPicked, qualityLabels, handleAudioFocus) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(player, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> player.onForeground(false)
                Lifecycle.Event.ON_START -> player.onForeground(true)
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            player.release()
        }
    }
    return player
}

/**
 * The video: Media3's [PlayerView] on a SurfaceView (the default surface type). A SurfaceView is its own SurfaceFlinger
 * layer fed straight by the decoder, so video frames never redraw the app window and the SoC's hardware composition is
 * kept; a TextureView would copy every frame through the GPU into the window (and could animate its transform, which this
 * screen does not need: the video is resized once per chat change, see PlayerScreen).
 *
 * The view is created once and only ever resized by [modifier]; the [PlayerView]'s AspectRatioFrameLayout letterboxes the
 * picture (RESIZE_MODE_FIT) inside whatever box it is given.
 */
@OptIn(UnstableApi::class)
@Composable
fun NativeVideoSurface(player: NativeVideoPlayer, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                useController = false // the app draws its own controls
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShutterBackgroundColor(Color.BLACK)
                setKeepContentOnPlayerReset(true) // the last frame stays up while the next channel starts
                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                isFocusable = false
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                this.player = player.exoPlayer
            }
        },
        modifier = modifier,
        onRelease = { it.player = null },
    )
}
