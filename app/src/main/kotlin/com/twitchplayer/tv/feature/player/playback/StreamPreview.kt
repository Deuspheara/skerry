package com.twitchplayer.tv.feature.player.playback

import android.content.Context
import android.util.Log
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import com.twitchplayer.tv.core.ui.FocusProgress
import com.twitchplayer.tv.feature.player.data.stream.TwitchPlaybackResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Which channel the preview is on and whether its first frame is drawn (until then the card keeps showing its thumbnail). */
data class PreviewState(val login: String? = null, val rendered: Boolean = false)

/**
 * A muted, low quality live preview shown inside a focused card. One [ExoPlayer] shared by every card (only one card is
 * focused at a time), independent from the main player: it keeps working while the mini player plays. It plays the
 * smallest video rendition of the channel, takes no audio focus and decodes no audio. Any failure (offline channel, no
 * free hardware decoder, network) just leaves the thumbnail in place.
 */
@OptIn(UnstableApi::class)
class StreamPreview(
    context: Context,
    private val resolver: TwitchPlaybackResolver,
    private val scope: CoroutineScope,
) {
    val exoPlayer: ExoPlayer = ExoPlayer.Builder(
        context,
        DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
            .setEnableDecoderFallback(true),
    )
        .setLoadControl(
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(1_500, 4_000, 500, 1_000)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build(),
        )
        .setHandleAudioBecomingNoisy(false)
        .build()
        .apply {
            volume = 0f
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                .setMaxVideoSize(PREVIEW_MAX_WIDTH, PREVIEW_MAX_HEIGHT)
                .setForceLowestBitrate(true)
                .build()
        }

    private val mutableState = MutableStateFlow(PreviewState())
    val state: StateFlow<PreviewState> = mutableState.asStateFlow()

    private val http = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true).setConnectTimeoutMs(5_000).setReadTimeoutMs(5_000)
    private val hls = HlsMediaSource.Factory(http).setAllowChunklessPreparation(true)
    private var job: Job? = null

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onRenderedFirstFrame() {
                if (mutableState.value.login != null) mutableState.value = mutableState.value.copy(rendered = true)
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.w(TAG, "preview error ${error.errorCodeName}: ${error.message}")
                // Keep the thumbnail: no retry for a preview.
                mutableState.value = mutableState.value.copy(rendered = false)
                exoPlayer.stop()
            }
        })
    }

    fun start(login: String) {
        if (mutableState.value.login.equals(login, ignoreCase = true)) return
        job?.cancel()
        mutableState.value = PreviewState(login, rendered = false)
        job = scope.launch {
            val resolved = try {
                resolver.resolve(login)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.d(TAG, "no preview for $login: $e")
                return@launch
            }
            // The lowest rendition that still has video (the list is best first, audio only last).
            val url = resolved.variants.filter { !it.audioOnly }.minByOrNull { it.height }?.url ?: return@launch
            exoPlayer.setMediaSource(hls.createMediaSource(MediaItem.fromUri(url)), true)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }
    }

    /** Stops the preview when it is on [login] (a card losing focus must not stop the one that just got it). */
    fun stop(login: String) {
        if (!mutableState.value.login.equals(login, ignoreCase = true)) return
        halt()
    }

    fun halt() {
        job?.cancel()
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        mutableState.value = PreviewState()
    }

    fun release() {
        job?.cancel()
        exoPlayer.release()
    }

    private companion object {
        const val TAG = "StreamPreview"
        const val PREVIEW_MAX_WIDTH = 640
        const val PREVIEW_MAX_HEIGHT = 360
    }
}

/** Null where previews are off (dialogs, previews, tests): cards then never start one. */
val LocalStreamPreview = staticCompositionLocalOf<StreamPreview?> { null }

/** Created once for the app, halted when the activity goes to the background. */
@Composable
fun rememberStreamPreview(resolver: TwitchPlaybackResolver): StreamPreview {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preview = remember(resolver) { StreamPreview(context, resolver, scope) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(preview, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) preview.halt() }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            preview.release()
        }
    }
    return preview
}

/** How long a card must keep focus before its preview starts. */
private const val PREVIEW_DELAY_MS = 1_000L

/**
 * Drawn over a card's thumbnail: starts the preview once [focus] has held for [PREVIEW_DELAY_MS], fades the video in when
 * its first frame is there, and stops it as soon as focus leaves. A TextureView (not a SurfaceView) so the card's
 * rounded clip and focus scale apply to the picture; at this resolution the extra GPU copy is cheap.
 */
@Composable
fun StreamPreviewLayer(login: String, focus: FocusProgress, preview: StreamPreview, modifier: Modifier = Modifier) {
    LaunchedEffect(login, preview) {
        snapshotFlow { focus.value > 0.95f }.distinctUntilChanged().collectLatest { focused ->
            if (focused) {
                delay(PREVIEW_DELAY_MS)
                preview.start(login)
            } else {
                preview.stop(login)
            }
        }
    }
    DisposableEffect(login, preview) { onDispose { preview.stop(login) } }

    val state by preview.state.collectAsState()
    if (!state.login.equals(login, ignoreCase = true)) return
    val alpha by animateFloatAsState(if (state.rendered) 1f else 0f, tween(250), label = "previewAlpha")
    AndroidView(
        factory = { context ->
            TextureView(context).also { preview.exoPlayer.setVideoTextureView(it) }
        },
        modifier = modifier.graphicsLayer { this.alpha = alpha },
        onRelease = { preview.exoPlayer.clearVideoTextureView(it) },
    )
}
