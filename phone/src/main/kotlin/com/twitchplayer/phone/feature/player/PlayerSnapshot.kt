package com.twitchplayer.phone.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import kotlinx.coroutines.delay

/** The parts of a Media3 [Player] the screens draw, as observable state (one listener, removed with the composition). */
@Stable
class PlayerSnapshot internal constructor(private val player: Player) {
    var isPlaying by mutableStateOf(player.isPlaying)
        internal set
    var playWhenReady by mutableStateOf(player.playWhenReady)
        internal set
    var buffering by mutableStateOf(player.playbackState == Player.STATE_BUFFERING)
        internal set
    var ended by mutableStateOf(player.playbackState == Player.STATE_ENDED)
        internal set
    var videoWidth by mutableIntStateOf(player.videoSize.width)
        internal set
    var videoHeight by mutableIntStateOf(player.videoSize.height)
        internal set
    var speed by mutableFloatStateOf(player.playbackParameters.speed)
        internal set
    var positionMs by mutableLongStateOf(0L)
        internal set
    var durationMs by mutableLongStateOf(0L)
        internal set

    /** A frame is on screen: the stream or replay is playing or paused with a picture, not starting up. */
    val hasPicture: Boolean get() = videoWidth > 0 && videoHeight > 0

    internal fun refreshPosition() {
        positionMs = player.currentPosition.coerceAtLeast(0L)
        durationMs = player.duration.takeIf { it > 0 } ?: durationMs
    }
}

/** [PlayerSnapshot] of [player]; with [pollMs] the position and duration are also refreshed on that interval (replays and clips). */
@Composable
fun rememberPlayerSnapshot(player: Player, pollMs: Long? = null): PlayerSnapshot {
    val snapshot = remember(player) { PlayerSnapshot(player) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                snapshot.isPlaying = isPlaying
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                snapshot.playWhenReady = playWhenReady
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                snapshot.buffering = playbackState == Player.STATE_BUFFERING
                snapshot.ended = playbackState == Player.STATE_ENDED
                snapshot.refreshPosition()
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                snapshot.videoWidth = videoSize.width
                snapshot.videoHeight = videoSize.height
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                snapshot.speed = playbackParameters.speed
            }
        }
        player.addListener(listener)
        snapshot.refreshPosition()
        onDispose { player.removeListener(listener) }
    }
    if (pollMs != null) {
        LaunchedEffect(snapshot, pollMs) {
            while (true) {
                snapshot.refreshPosition()
                delay(pollMs)
            }
        }
    }
    return snapshot
}
