package com.twitchplayer.phone.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import com.twitchplayer.phone.ui.components.PhoneIcons
import com.twitchplayer.tv.core.data.repository.SettingsRepository
import com.twitchplayer.tv.core.platform.NumberFormatter
import com.twitchplayer.tv.core.ui.formatUptime
import com.twitchplayer.tv.feature.player.data.stream.QualityPolicy
import com.twitchplayer.tv.feature.player.data.stream.ResolvedVod
import com.twitchplayer.tv.feature.player.data.stream.VodResolver
import com.twitchplayer.tv.feature.player.playback.VideoQualities
import com.twitchplayer.tv.feature.player.playback.VideoQuality
import com.twitchplayer.tv.feature.vod.data.VodProgress
import com.twitchplayer.tv.feature.vod.data.VodProgressStore
import com.twitchplayer.tv.feature.vod.ui.PLAYBACK_SPEEDS
import com.twitchplayer.tv.feature.vod.ui.nextSpeed
import com.twitchplayer.tv.feature.vod.ui.seekTarget
import com.twitchplayer.tv.feature.vod.ui.speedLabel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.math.abs

private sealed interface ReplayLoad {
    data object Loading : ReplayLoad
    data class Ready(val vod: ResolvedVod, val saved: VodProgress?, val preferredQuality: String?) : ReplayLoad
    data class Failed(val error: ReplayError) : ReplayLoad
}

/**
 * A past broadcast: resolves it (token + playlist), offers to resume where the viewer stopped, plays it with a seek bar, +/-10 s,
 * speed and quality, and remembers the position (every 10 s while playing, cleared when it ends).
 */
@Composable
fun ReplayRoute(
    id: String,
    title: String,
    durationSeconds: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    resolver: VodResolver = koinInject(),
    store: VodProgressStore = koinInject(),
    settings: SettingsRepository = koinInject(),
) {
    var load by remember(id) { mutableStateOf<ReplayLoad>(ReplayLoad.Loading) }
    var attempt by remember(id) { mutableIntStateOf(0) }
    // After a playback error: where it stopped, so Retry continues there without asking again.
    var continueAt by remember(id) { mutableStateOf<Long?>(null) }
    LaunchedEffect(id, attempt) {
        load = ReplayLoad.Loading
        load = runCatching {
            val vod = resolver.resolve(id)
            ReplayLoad.Ready(vod, store.progress.first()[id], settings.settings.first().preferredQuality)
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            ReplayLoad.Failed(error.toReplayError())
        }
    }
    Box(modifier.fillMaxSize().background(Color.Black)) {
        when (val state = load) {
            ReplayLoad.Loading -> {
                CircularProgressIndicator(Modifier.align(Alignment.Center).size(40.dp), color = Color.White)
                BackOnly(onBack)
            }

            is ReplayLoad.Failed -> {
                MessageState(
                    title = stringResource(state.error.title),
                    body = stringResource(state.error.line),
                    modifier = Modifier.align(Alignment.Center).background(MaterialTheme.colorScheme.background),
                    actionLabel = if (state.error.retryable) stringResource(R.string.state_retry) else null,
                    onAction = if (state.error.retryable) ({ attempt++ }) else null,
                )
                BackOnly(onBack)
            }

            is ReplayLoad.Ready -> ReplayPlayer(
                vod = state.vod,
                saved = state.saved,
                preferredQuality = state.preferredQuality,
                title = title,
                knownDurationMs = durationSeconds * MILLIS,
                continueAt = continueAt,
                onBack = onBack,
                onFail = { error, at ->
                    continueAt = at
                    load = ReplayLoad.Failed(error)
                },
            )
        }
    }
}

@Composable
private fun BackOnly(onBack: () -> Unit) {
    Row(Modifier.statusBarsPadding().padding(4.dp)) {
        ControlButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.player_back), onBack)
    }
}

@Composable
private fun ReplayPlayer(
    vod: ResolvedVod,
    saved: VodProgress?,
    preferredQuality: String?,
    title: String,
    knownDurationMs: Long,
    continueAt: Long?,
    onBack: () -> Unit,
    onFail: (ReplayError, Long) -> Unit,
    store: VodProgressStore = koinInject(),
    appScope: CoroutineScope = koinInject(),
    numbers: NumberFormatter = koinInject(),
) {
    val context = LocalContext.current
    val autoLabel = stringResource(R.string.quality_auto)
    val currentOnFail by rememberUpdatedState(onFail)
    val playback = remember(vod.vodId) { RecordedPlayback(context) }
    val exo = playback.exo
    val snapshot = rememberPlayerSnapshot(exo, pollMs = POLL_MS)
    val decision = remember(vod.vodId) { decideStart(saved, continueAt) }
    var startAt by remember(vod.vodId) { mutableStateOf((decision as? StartDecision.Start)?.atMs) }
    var selected by remember(vod.vodId) { mutableStateOf(QualityPolicy.choose(vod.variants, preferredQuality)?.id ?: QualityPolicy.AUTO) }
    var started by remember(vod.vodId) { mutableStateOf(false) }
    var qualities by remember { mutableStateOf<VideoQualities?>(null) }
    var dragging by remember { mutableStateOf(false) }
    val duration = if (snapshot.durationMs > 0) snapshot.durationMs else maxOf(knownDurationMs, vod.durationMs)

    fun urlFor(id: String) = vod.variants.firstOrNull { it.id == id }?.url ?: vod.masterUrl

    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val network = error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                    error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
                currentOnFail(if (network) ReplayError.Network else ReplayError.Playback, exo.currentPosition.coerceAtLeast(0L))
            }
        }
        exo.addListener(listener)
        onDispose {
            exo.removeListener(listener)
            // Remember where it stopped (a finished replay is forgotten); written on the app scope, the page is going away.
            val at = exo.currentPosition.coerceAtLeast(0L)
            val total = if (exo.duration > 0 && !vod.recording) exo.duration else duration
            when (progressAction(started, exo.playbackState == Player.STATE_ENDED)) {
                ProgressAction.Save -> appScope.launch { store.save(vod.vodId, at, total) }
                ProgressAction.Clear -> appScope.launch { store.clear(vod.vodId) }
                ProgressAction.Skip -> Unit
            }
            playback.release()
        }
    }
    // Playback begins when the start position is known (immediately, or after the viewer answers "resume?").
    DisposableEffect(startAt) {
        startAt?.let { at ->
            started = true
            playback.setSource(urlFor(selected), at, vod.recording, playWhenReady = true)
        }
        onDispose { }
    }
    // Save where the viewer is every few seconds while playing, so a crash or a power-off loses little.
    LaunchedEffect(exo, started) {
        while (started) {
            delay(SAVE_EVERY_MS)
            if (exo.isPlaying && !dragging) {
                val at = exo.currentPosition
                val total = if (exo.duration > 0 && !vod.recording) exo.duration else duration
                appScope.launch { store.save(vod.vodId, at, total) }
            }
        }
    }
    DisposableEffect(snapshot.ended) {
        if (snapshot.ended && started) appScope.launch { store.clear(vod.vodId) }
        onDispose { }
    }

    val togglePlay = { exo.playWhenReady = !exo.playWhenReady }
    PlayerScaffold(
        snapshot = snapshot,
        failed = false,
        title = title,
        subtitle = null,
        onBack = onBack,
        onTogglePlay = togglePlay,
        pinned = dragging || qualities != null || (decision is StartDecision.Ask && startAt == null),
        video = { videoModifier -> ExoVideoSurface(exo, videoModifier) },
        stageOverlay = {
            val ask = decision as? StartDecision.Ask
            if (ask != null && startAt == null) {
                ResumeOffer(ask.saved.positionMs, onResume = { startAt = ask.saved.positionMs }, onStartOver = { startAt = 0L }, modifier = Modifier.align(Alignment.Center))
            }
        },
        center = { chrome ->
            if (startAt != null) {
                RecordedCenter(
                    snapshot = snapshot,
                    onTogglePlay = togglePlay,
                    onReplay = {
                        exo.seekTo(0)
                        exo.playWhenReady = true
                    },
                    onSkip = { delta -> exo.seekTo(seekTarget(exo.currentPosition, delta, duration)) },
                    chrome = chrome,
                )
            }
        },
        bottom = { chrome ->
            if (startAt != null) {
                SeekRow(snapshot, onSeek = { exo.seekTo(it) }, onDragging = { dragging = it }, durationMs = duration)
                Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!vod.recording && chrome.landscape) {
                        androidx.compose.material3.TextButton(
                            onClick = {
                                chrome.controls.touch()
                                exo.setPlaybackSpeed(nextSpeed(exo.playbackParameters.speed))
                            },
                        ) { Text(speedLabel(snapshot.speed, numbers), color = Color.White) }
                    }
                    Box(Modifier.weight(1f))
                    if (vod.variants.size > 1) {
                        ControlButton(
                            PhoneIcons.Quality,
                            stringResource(R.string.player_quality),
                            {
                                chrome.controls.touch()
                                qualities = VideoQualities(
                                    listOf(VideoQuality(QualityPolicy.AUTO, autoLabel)) + vod.variants.map { VideoQuality(it.id, it.name) },
                                    selected,
                                )
                            },
                        )
                    }
                    FullscreenButton(chrome)
                }
            }
        },
        portraitContent = {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                if (duration > 0) Text(formatUptime(duration), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // A replay still being recorded is pinned to normal speed.
                if (!vod.recording && startAt != null) {
                    Text(stringResource(R.string.phone_player_speed), style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PLAYBACK_SPEEDS.forEach { speed ->
                            FilterChip(
                                selected = abs(snapshot.speed - speed) < SPEED_EPSILON,
                                onClick = { exo.setPlaybackSpeed(speed) },
                                label = { Text(speedLabel(speed, numbers)) },
                            )
                        }
                    }
                }
            }
        },
    )
    qualities?.let { options ->
        QualitySheet(
            qualities = options,
            onPick = { picked ->
                qualities = null
                selected = picked
                playback.setSource(if (picked == QualityPolicy.AUTO) vod.masterUrl else urlFor(picked), exo.currentPosition, vod.recording, exo.playWhenReady)
            },
            onDismiss = { qualities = null },
        )
    }
}

private const val MILLIS = 1_000L
private const val SPEED_EPSILON = 0.01f
private const val POLL_MS = 500L
private const val SAVE_EVERY_MS = 10_000L
