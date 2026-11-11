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
    data object Failed : ClipLoad
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
            ClipLoad.Failed
        }
    }
    Box(modifier.fillMaxSize().background(Color.Black)) {
        when (val state = load) {
            ClipLoad.Loading -> {
                CircularProgressIndicator(Modifier.align(Alignment.Center).size(40.dp), color = Color.White)
                ClipBack(onBack)
            }

            ClipLoad.Failed -> {
                MessageState(
                    title = stringResource(R.string.clip_unavailable),
                    body = stringResource(R.string.clip_unavailable_line),
                    modifier = Modifier.align(Alignment.Center).background(MaterialTheme.colorScheme.background),
                    actionLabel = stringResource(R.string.state_retry),
                    onAction = { attempt++ },
                )
                ClipBack(onBack)
            }

            is ClipLoad.Ready -> ClipPlayer(slug, state.url, title, onBack)
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
private fun ClipPlayer(slug: String, url: String, title: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val playback = remember(url) { RecordedPlayback(context) }
    val exo = playback.exo
    val snapshot = rememberPlayerSnapshot(exo, pollMs = POLL_MS)
    var failed by remember(url) { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                failed = true
            }
        }
        exo.addListener(listener)
        playback.setSource(url, 0L, recording = false, playWhenReady = true)
        onDispose {
            exo.removeListener(listener)
            playback.release()
        }
    }
    val togglePlay = { exo.playWhenReady = !exo.playWhenReady }
    val shareText = clipShareText(title, slug)
    val shareLabel = stringResource(R.string.phone_player_share)
    PlayerScaffold(
        snapshot = snapshot,
        failed = failed,
        title = title,
        subtitle = null,
        onBack = onBack,
        onTogglePlay = togglePlay,
        pinned = dragging,
        video = { videoModifier -> ExoVideoSurface(exo, videoModifier) },
        stageOverlay = {
            if (failed) {
                Text(stringResource(R.string.clip_unavailable), color = Color.White, modifier = Modifier.align(Alignment.Center))
            }
        },
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
            SeekRow(snapshot, onSeek = { exo.seekTo(it) }, onDragging = { dragging = it })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { FullscreenButton(chrome) }
        },
        portraitContent = {
            Column(Modifier.weight(1f).fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                if (snapshot.durationMs > 0) {
                    Text(formatUptime(snapshot.durationMs), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
    )
}

private const val POLL_MS = 500L
