package com.twitchplayer.phone.feature.player

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.twitchplayer.phone.R
import com.twitchplayer.phone.ui.components.MessageState
import com.twitchplayer.tv.core.ui.formatUptime
import com.twitchplayer.tv.feature.player.data.stream.ClipResolver
import com.twitchplayer.tv.feature.vod.ui.seekTarget
import kotlinx.coroutines.CancellationException
import org.koin.compose.koinInject

private sealed interface ClipLoad {
    data object Loading : ClipLoad
    data class Ready(val url: String) : ClipLoad
    data class Failed(val failure: ClipFailure) : ClipLoad
}

/** A clip: plays the best MP4, seek bar, +/-10 s, replay at the end, share. */
@Composable
fun ClipRoute(
    slug: String,
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    resolver: ClipResolver = koinInject(),
) {
    var load by remember(slug) { mutableStateOf<ClipLoad>(ClipLoad.Loading) }
    var attempt by remember(slug) { mutableIntStateOf(0) }
    LaunchedEffect(slug, attempt) {
        load = ClipLoad.Loading
        load = runCatching { ClipLoad.Ready(resolver.resolve(slug).first().url) as ClipLoad }.getOrElse { error ->
            if (error is CancellationException) throw error
            ClipLoad.Failed(error.toClipFailure())
        }
    }
    Box(modifier.fillMaxSize().background(Color.Black)) {
        when (val state = load) {
            ClipLoad.Loading -> {
                CircularProgressIndicator(Modifier.align(Alignment.Center).size(40.dp), color = Color.White)
                ClipBack(onBack)
            }

            is ClipLoad.Failed -> {
                MessageState(
                    title = stringResource(state.failure.title),
                    body = stringResource(state.failure.line),
                    modifier = Modifier.align(Alignment.Center).background(MaterialTheme.colorScheme.background),
                    actionLabel = stringResource(R.string.state_retry),
                    onAction = { attempt++ },
                )
                ClipBack(onBack)
            }

            is ClipLoad.Ready -> ClipPlayer(
                slug,
                state.url,
                title,
                onBack,
                onFail = { failure -> load = ClipLoad.Failed(failure) },
            )
        }
    }
}

@Composable
private fun ClipBack(onBack: () -> Unit) {
    Row(Modifier.statusBarsPadding().padding(4.dp)) {
        ControlButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.player_back), onBack)
    }
}

@Composable
private fun ClipPlayer(slug: String, url: String, title: String, onBack: () -> Unit, onFail: (ClipFailure) -> Unit) {
    val restoration = rememberPlayerRestoration(slug)
    val context = LocalContext.current
    val playback = remember(url) { RecordedPlayback(context) }
    val exo = playback.exo
    val snapshot = rememberPlayerSnapshot(exo, pollMs = POLL_MS)
    var dragging by remember { mutableStateOf(false) }
    val currentOnFail by rememberUpdatedState(onFail)
    PauseWhenInBackground(exo)
    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                currentOnFail(error.toClipFailure())
            }
        }
        exo.addListener(listener)
        playback.setSource(url, restoration.positionMs ?: 0L, recording = false, playWhenReady = true)
        // The player itself is released by `playback` (a RememberObserver) when this page leaves the composition.
        onDispose { exo.removeListener(listener) }
    }
    // Where the clip is, kept for a page brought back after the process was killed.
    SavePlaybackPosition(exo, POSITION_SAVE_MS, enabled = true) { restoration.positionMs = it }
    val togglePlay = { exo.playWhenReady = !exo.playWhenReady }
    val shareText = clipShareText(title, slug)
    val shareLabel = stringResource(R.string.phone_player_share)
    PlayerScaffold(
        snapshot = snapshot,
        failed = false,
        title = title,
        subtitle = null,
        onBack = onBack,
        onTogglePlay = togglePlay,
        restoration = restoration,
        pinned = dragging,
        video = { videoModifier -> ExoVideoSurface(exo, videoModifier) },
        topActions = { chrome ->
            if (shareText != null) {
                ControlButton(
                    Icons.Filled.Share,
                    shareLabel,
                    {
                        chrome.controls.touch()
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText)
                        runCatching { context.startActivity(Intent.createChooser(send, shareLabel)) }
                    },
                )
            }
        },
        center = { chrome ->
            RecordedCenter(
                snapshot = snapshot,
                onTogglePlay = togglePlay,
                onReplay = {
                    exo.seekTo(0)
                    exo.playWhenReady = true
                },
                onSkip = { delta -> exo.seekTo(seekTarget(exo.currentPosition, delta, snapshot.durationMs)) },
                chrome = chrome,
            )
        },
        bottom = { chrome ->
            RecordedBottom(
                snapshot = snapshot,
                durationMs = snapshot.durationMs,
                onSeek = { exo.seekTo(it) },
                onDragging = { dragging = it },
                chrome = chrome,
                landscapeStart = {},
                trailing = { FullscreenButton(chrome) },
            )
        },
        portraitContent = {
            Column(Modifier.weight(1f).fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
                if (snapshot.durationMs > 0) {
                    Text(formatUptime(snapshot.durationMs), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
    )
}

private const val POLL_MS = 500L
private const val POSITION_SAVE_MS = 5_000L
