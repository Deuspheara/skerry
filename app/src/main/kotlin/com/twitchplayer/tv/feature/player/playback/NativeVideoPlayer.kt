package com.twitchplayer.tv.feature.player.playback

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.hls.HlsManifest
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.twitchplayer.tv.R
import com.twitchplayer.tv.feature.player.data.stream.ChannelOfflineException
import com.twitchplayer.tv.feature.player.data.stream.QualityPolicy
import com.twitchplayer.tv.feature.player.data.stream.ResolvedStream
import com.twitchplayer.tv.feature.player.data.stream.StreamVariant
import com.twitchplayer.tv.feature.player.data.stream.TwitchPlaybackResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the native player is doing, for the host: [Failed] hands the channel to the WebView player. */
sealed interface NativeStatus {
    data object Idle : NativeStatus
    data object Resolving : NativeStatus
    data object Playing : NativeStatus
    data class Failed(val channel: String, val cause: Throwable) : NativeStatus {
        val offline: Boolean get() = cause is ChannelOfflineException
    }
}

/**
 * Plays Twitch's HLS with Media3. One [ExoPlayer] for the whole session: a channel switch resolves the new stream and
 * swaps the media source on the same player (the surface stays attached, the last frame stays up until the first new one).
 * Everything runs on [scope]'s main dispatcher: the ExoPlayer is built, used and released on the main thread.
 *
 * Latency: the live offset targets 4 s (Twitch's HLS segments are 2 s and ExoPlayer has no support for Twitch's
 * prefetch segments, so about three segments is the floor); the player speeds up to 1.04x to catch up and slows to 0.97x
 * to recover.
 */
@OptIn(UnstableApi::class)
class NativeVideoPlayer(
    private val context: Context,
    private val resolver: TwitchPlaybackResolver,
    private val scope: CoroutineScope,
    private val preferredQuality: () -> String?,
    private val onQualityPicked: (String) -> Unit,
) : VideoPlayer {
    val exoPlayer: ExoPlayer = ExoPlayer.Builder(
        context,
        // Hardware decoders only: MediaCodec picks the SoC's AVC decoder, the extension (software) renderers are off.
        DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
            .setEnableDecoderFallback(true),
    )
        // Small buffers: a live stream never needs more than a few seconds ahead, and the TV's heap is small.
        .setLoadControl(
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(MIN_BUFFER_MS, MAX_BUFFER_MS, START_BUFFER_MS, REBUFFER_MS)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build(),
        )
        .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
        .setHandleAudioBecomingNoisy(true)
        .build()

    private val mutableStatus = MutableStateFlow<NativeStatus>(NativeStatus.Idle)
    val status: StateFlow<NativeStatus> = mutableStatus.asStateFlow()

    private val http = DefaultHttpDataSource.Factory()
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(HTTP_TIMEOUT_MS)
        .setReadTimeoutMs(HTTP_TIMEOUT_MS)
    private val hlsFactory = HlsMediaSource.Factory(http).setAllowChunklessPreparation(true)
    private val progressiveFactory by lazy(LazyThreadSafetyMode.NONE) { ProgressiveMediaSource.Factory(DefaultDataSource.Factory(context)) }

    private val mutableStats = MutableStateFlow<PlaybackStats?>(null)
    override val stats: StateFlow<PlaybackStats?> = mutableStats.asStateFlow()
    private val mutableAd = MutableStateFlow<AdBreak?>(null)
    override val adBreak: StateFlow<AdBreak?> = mutableAd.asStateFlow()
    private var statsEnabled = false
    private var droppedFrames = 0
    private var bandwidth = 0L
    private var monitor: Job? = null

    private var channel: String? = null
    private var stream: ResolvedStream? = null
    private var selected: String = QualityPolicy.AUTO
    private var userPaused = false
    private var restarts = 0
    private var job: Job? = null

    init {
        exoPlayer.addAnalyticsListener(object : AnalyticsListener {
            override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) {
                this@NativeVideoPlayer.droppedFrames += droppedFrames
            }

            override fun onBandwidthEstimate(eventTime: AnalyticsListener.EventTime, totalLoadTimeMs: Int, totalBytesLoaded: Long, bitrateEstimate: Long) {
                bandwidth = bitrateEstimate
            }
        })
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) { restarts = 0; mutableStatus.value = NativeStatus.Playing }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.w(TAG, "player error ${error.errorCodeName}: ${error.message}", error)
                if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    exoPlayer.seekToDefaultPosition()
                    exoPlayer.prepare()
                    return
                }
                // Dropped network or an expired token: resolve again (new token) after a growing pause, a few times.
                val name = channel ?: return
                if (restarts >= MAX_RESTARTS) { mutableStatus.value = NativeStatus.Failed(name, error); return }
                val wait = RESTART_DELAY_MS shl restarts++
                job?.cancel()
                job = scope.launch { delay(wait); start(name) }
            }
        })
    }

    /** Starts [login] (a no-op when it already plays or is being resolved); a failed channel is retried by calling this again. */
    fun load(login: String) {
        if (login.equals(channel, ignoreCase = true) && mutableStatus.value !is NativeStatus.Failed) return
        channel = login
        restarts = 0
        userPaused = false
        job?.cancel()
        job = scope.launch { start(login) }
        startMonitor()
    }

    private suspend fun start(login: String) {
        mutableStatus.value = NativeStatus.Resolving
        val resolved = try {
            resolver.resolve(login)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "cannot resolve $login: $e")
            mutableStatus.value = NativeStatus.Failed(login, e)
            return
        }
        stream = resolved
        droppedFrames = 0
        val variant = QualityPolicy.choose(resolved.variants, preferredQuality())
        selected = variant?.id ?: QualityPolicy.AUTO
        play(variant?.url ?: resolved.masterUrl)
    }

    private fun play(url: String) {
        val live = MediaItem.LiveConfiguration.Builder()
            .setTargetOffsetMs(TARGET_LIVE_OFFSET_MS)
            .setMinOffsetMs(MIN_LIVE_OFFSET_MS)
            .setMaxOffsetMs(MAX_LIVE_OFFSET_MS)
            .setMinPlaybackSpeed(0.97f)
            .setMaxPlaybackSpeed(1.04f)
            .build()
        val item = MediaItem.Builder().setUri(url).setLiveConfiguration(live).build()
        val hls = url.contains(".m3u8")
        val source: MediaSource = (if (hls) hlsFactory else progressiveFactory).createMediaSource(item)
        exoPlayer.repeatMode = if (hls) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE // a looping clip stands in for a stream (demo)
        exoPlayer.setMediaSource(source, /* resetPosition = */ true)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = !userPaused
    }

    /**
     * Once a second: the ad marker of the playlist (cheap: a few tags) and, only while the overlay is on, the statistics.
     * The loop lives on the main dispatcher with the player; it stops with [stop] / [release].
     */
    private fun startMonitor() {
        if (monitor?.isActive == true) return
        monitor = scope.launch {
            while (true) {
                // Nothing moves while paused, buffering from idle or stopped: no work then (a paused TV costs nothing).
                if (exoPlayer.isPlaying) {
                    publishAd()
                    if (statsEnabled) mutableStats.value = readStats()
                }
                delay(MONITOR_MS)
            }
        }
    }

    private fun publishAd() {
        val ad = runCatching {
            val tags = (exoPlayer.currentManifest as? HlsManifest)?.mediaPlaylist?.tags.orEmpty()
            if (tags.isEmpty()) null else {
                val offset = exoPlayer.currentLiveOffset.takeIf { it != C.TIME_UNSET } ?: 0L
                HlsAds.current(tags, System.currentTimeMillis() - offset)
            }
        }.getOrNull()
        // Seconds are all the UI shows: only republish when the visible value changes.
        if ((ad?.remainingMs?.div(1000)) != (mutableAd.value?.remainingMs?.div(1000))) mutableAd.value = ad
    }

    private fun readStats(): PlaybackStats {
        val format = exoPlayer.videoFormat
        val offset = exoPlayer.currentLiveOffset.takeIf { it != C.TIME_UNSET }
        return PlaybackStats(
            latencyMs = offset,
            bufferMs = exoPlayer.totalBufferedDuration,
            // HLS formats often carry no bitrate: the playlist's declared bandwidth of the rendition in use stands in.
            videoBitrate = format?.let { maxOf(it.averageBitrate, it.peakBitrate) }?.takeIf { it > 0 }
                ?: stream?.variants?.firstOrNull { it.id == selected }?.bandwidth ?: 0,
            bandwidth = bandwidth,
            droppedFrames = droppedFrames,
            width = format?.width?.takeIf { it > 0 } ?: 0,
            height = format?.height?.takeIf { it > 0 } ?: 0,
            frameRate = format?.frameRate?.takeIf { it > 0f } ?: 0f,
            codec = format?.codecs,
            speed = exoPlayer.playbackParameters.speed,
        )
    }

    override fun setStatsEnabled(enabled: Boolean) {
        statsEnabled = enabled
        if (enabled) mutableStats.value = readStats() else mutableStats.value = null
    }

    override fun play() { userPaused = false; exoPlayer.playWhenReady = true }

    override fun pause() { userPaused = true; exoPlayer.playWhenReady = false }

    override fun toggleMute() { exoPlayer.volume = if (exoPlayer.volume > 0f) 0f else 1f }

    override fun loadQualities(onResult: (VideoQualities) -> Unit) {
        val variants = stream?.variants.orEmpty()
        if (variants.isEmpty()) return onResult(VideoQualities.None)
        val options = buildList {
            add(VideoQuality(QualityPolicy.AUTO, context.getString(R.string.quality_auto)))
            variants.forEach { add(VideoQuality(it.id, it.label())) }
        }
        onResult(VideoQualities(options, selected))
    }

    override fun setQuality(id: String) {
        val resolved = stream ?: return
        if (id == selected) return
        val variant = resolved.variants.firstOrNull { it.id == id }
        if (variant == null && id != QualityPolicy.AUTO) return
        selected = id
        onQualityPicked(id)
        play(variant?.url ?: resolved.masterUrl)
    }

    override fun setInteractive(enabled: Boolean) = Unit

    /** The app went to the background (false) or came back (true): a live stream never keeps downloading unseen. */
    fun onForeground(visible: Boolean) {
        if (!visible) exoPlayer.playWhenReady = false
        else if (!userPaused && channel != null) { exoPlayer.seekToDefaultPosition(); exoPlayer.playWhenReady = true }
    }

    /** Stops playing and forgets the channel (the host handed it to another player). */
    fun stop() {
        job?.cancel()
        channel = null
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        monitor?.cancel()
        mutableAd.value = null
        mutableStatus.value = NativeStatus.Idle
    }

    fun release() {
        job?.cancel()
        monitor?.cancel()
        exoPlayer.release()
    }

    private fun StreamVariant.label(): String = if (audioOnly) context.getString(R.string.quality_audio_only) else name

    private companion object {
        const val TAG = "NativeVideoPlayer"
        const val MIN_BUFFER_MS = 5_000
        const val MAX_BUFFER_MS = 15_000
        const val START_BUFFER_MS = 1_000
        const val REBUFFER_MS = 2_000
        const val HTTP_TIMEOUT_MS = 8_000
        const val TARGET_LIVE_OFFSET_MS = 4_000L
        const val MIN_LIVE_OFFSET_MS = 2_500L
        const val MAX_LIVE_OFFSET_MS = 8_000L
        const val MONITOR_MS = 1_000L
        const val MAX_RESTARTS = 3
        const val RESTART_DELAY_MS = 800L
    }
}
