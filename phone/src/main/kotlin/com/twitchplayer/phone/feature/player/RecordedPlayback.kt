package com.twitchplayer.phone.feature.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource

/**
 * The Media3 player of a replay or a clip: hardware decoders, normal buffering (not a live stream), audio focus and "becoming noisy"
 * handled by Media3. One instance per page; [release] when the page goes.
 */
@OptIn(UnstableApi::class)
class RecordedPlayback(private val context: Context) {
    val exo: ExoPlayer = ExoPlayer.Builder(
        context,
        DefaultRenderersFactory(context).setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF).setEnableDecoderFallback(true),
    )
        .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
        .setHandleAudioBecomingNoisy(true)
        .build()

    private val http = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true).setConnectTimeoutMs(HTTP_TIMEOUT_MS).setReadTimeoutMs(HTTP_TIMEOUT_MS)

    /**
     * Prepares [url] (an HLS playlist or an MP4) at [startAtMs]. A replay still being recorded has no end tag, so Media3 treats it as
     * live: it is pinned to speed 1 so it never "catches up" to the live edge on its own.
     */
    fun setSource(url: String, startAtMs: Long, recording: Boolean, playWhenReady: Boolean) {
        val item = MediaItem.Builder().setUri(url).apply {
            if (recording) setLiveConfiguration(MediaItem.LiveConfiguration.Builder().setMinPlaybackSpeed(1f).setMaxPlaybackSpeed(1f).build())
        }.build()
        val source: MediaSource = if (url.contains(".m3u8")) {
            HlsMediaSource.Factory(http).createMediaSource(item)
        } else {
            ProgressiveMediaSource.Factory(DefaultDataSource.Factory(context)).createMediaSource(item)
        }
        exo.setMediaSource(source, startAtMs)
        exo.prepare()
        exo.playWhenReady = playWhenReady
    }

    fun release() = exo.release()

    private companion object {
        const val HTTP_TIMEOUT_MS = 8_000
    }
}
