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
import com.twitchplayer.phone.navigation.PlaybackTarget
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
    info: ReplayInfo,
    onBack: () -> Unit,
    onOpenChannel: (String) -> Unit,
    onOpenReplay: (PlaybackTarget.Replay) -> Unit,
    modifier: Modifier = Modifier,
    /** False when the session was brought back after the process was killed: the replay opens paused. */
    autoplay: Boolean = true,
    resolver: VodResolver = koinInject(),
    store: VodProgressStore = koinInject(),
    settings: SettingsRepository = koinInject(),
) {
    val id = info.id
    var load by remember(id) { mutableStateOf<ReplayLoad>(ReplayLoad.Loading) }
    var attempt by remember(id) { mutableIntStateOf(0) }
    // Where the replay was: after a playback error Retry continues there without asking again, and after the process was killed the
    // page comes back at the same place (see PlayerRestoration).
    val restoration = rememberPlayerRestoration(id)
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
    val sheet = LocalPlayerSheet.current
    if (sheet != null && sheet.strip && load !is ReplayLoad.Ready) {
        PendingStrip(info.title, sheet, modifier)
        return
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
                ready = state,
                autoplay = autoplay,
                info = info,
                restoration = restoration,
                onBack = onBack,
                onOpenChannel = onOpenChannel,
                onOpenReplay = onOpenReplay,
                onFail = { error, at ->
                    restoration.positionMs = at
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
    ready: ReplayLoad.Ready,
    autoplay: Boolean,
    info: ReplayInfo,
    restoration: PlayerRestoration,
    onBack: () -> Unit,
    onOpenChannel: (String) -> Unit,
    onOpenReplay: (PlaybackTarget.Replay) -> Unit,
    onFail: (ReplayError, Long) -> Unit,
    store: VodProgressStore = koinInject(),
    settings: SettingsRepository = koinInject(),
    appScope: CoroutineScope = koinInject(),
    numbers: NumberFormatter = koinInject(),
) {
    val vod = ready.vod
    val context = LocalContext.current
    val autoLabel = stringResource(R.string.quality_auto)
    val currentOnFail by rememberUpdatedState(onFail)
    val playback = remember(vod.vodId) { RecordedPlayback(context) }
    val exo = playback.exo
    val snapshot = rememberPlayerSnapshot(exo, pollMs = POLL_MS)
    PauseWhenInBackground(exo)
    val qualityChoice = remember(settings, appScope) { QualityChoice(settings, appScope) }
    val knownDurationMs = maxOf(info.durationSeconds * MILLIS, vod.durationMs)
    val decision = remember(vod.vodId) { decideStart(ready.saved, resumableRestoredPosition(restoration.positionMs, knownDurationMs)) }
    var startAt by remember(vod.vodId) { mutableStateOf((decision as? StartDecision.Start)?.atMs) }
    var selected by remember(vod.vodId) { mutableStateOf(ReplayQuality.initial(vod.variants, ready.preferredQuality)) }
    var started by remember(vod.vodId) { mutableStateOf(false) }
    val startPlaying = remember(vod.vodId) { autoplay }
    var qualities by remember { mutableStateOf<VideoQualities?>(null) }
    val sleep = rememberSleepTimer()
    var sleepOpen by remember { mutableStateOf(false) }
    SleepTimerEffect(sleep) { exo.pause() }
    var dragging by remember { mutableStateOf(false) }
    val duration = if (snapshot.durationMs > 0) snapshot.durationMs else knownDurationMs

    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                currentOnFail(error.toReplayError(), exo.currentPosition.coerceAtLeast(0L))
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
            // The player itself is released by `playback` (a RememberObserver) right after this, when the page leaves the composition.
        }
    }
    // Playback begins when the start position is known (immediately, or after the viewer answers "resume?").
    DisposableEffect(startAt) {
        startAt?.let { at ->
            started = true
            playback.setSource(ReplayQuality.urlFor(vod.variants, vod.masterUrl, selected), at, vod.recording, playWhenReady = startPlaying)
        }
        onDispose { }
    }
    // Save where the viewer is every few seconds while playing, on pause and when the app stops, so a crash, a power-off or the
    // process being killed in the background loses little.
    SavePlaybackPosition(exo, SAVE_EVERY_MS, enabled = started && !dragging) { at ->
        val total = if (exo.duration > 0 && !vod.recording) exo.duration else duration
        appScope.launch { store.save(vod.vodId, at, total) }
        restoration.positionMs = at
    }
    DisposableEffect(snapshot.ended) {
        if (snapshot.ended && started) appScope.launch { store.clear(vod.vodId) }
        onDispose { }
    }

    val togglePlay = { exo.playWhenReady = !exo.playWhenReady }
    PlayerScaffold(
        snapshot = snapshot,
        failed = false,
        title = info.title,
        subtitle = null,
        onBack = onBack,
        onTogglePlay = togglePlay,
        restoration = restoration,
        pinned = dragging || qualities != null || sleepOpen || (decision is StartDecision.Ask && startAt == null),
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
                RecordedBottom(
                    snapshot = snapshot,
                    durationMs = duration,
                    onSeek = { exo.seekTo(it) },
                    onDragging = { dragging = it },
                    chrome = chrome,
                    landscapeStart = {
                        // The speed chips live under the picture in portrait; landscape has no room for them, so one button steps through.
                        if (!vod.recording) {
                            androidx.compose.material3.TextButton(
                                onClick = {
                                    chrome.controls.touch()
                                    exo.setPlaybackSpeed(nextSpeed(exo.playbackParameters.speed))
                                },
                            ) { Text(speedLabel(snapshot.speed, numbers), color = Color.White) }
                        }
                    },
                    trailing = {
                        SleepTimerChip(sleep, onClick = {
                            chrome.controls.touch()
                            sleepOpen = true
                        })
                        ControlButton(
                            PhoneIcons.Timer,
                            stringResource(R.string.phone_player_sleep_timer),
                            {
                                chrome.controls.touch()
                                sleepOpen = true
                            },
                        )
                        if (vod.variants.size > 1) {
                            ControlButton(
                                PhoneIcons.Quality,
                                stringResource(R.string.player_quality),
                                {
                                    chrome.controls.touch()
                                    qualities = ReplayQuality.options(vod.variants, autoLabel, selected)
                                },
                            )
                        }
                        FullscreenButton(chrome)
                    },
                )
            }
        },
        portraitContent = {
            ReplayDetails(
                info = info,
                durationMs = duration,
                speed = snapshot.speed,
                speedsEnabled = !vod.recording && startAt != null,
                numbers = numbers,
                onSpeed = { chosen -> exo.setPlaybackSpeed(chosen) },
                onOpenChannel = onOpenChannel,
                onOpenReplay = onOpenReplay,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        },
    )
    if (sleepOpen) SleepTimerSheet(sleep, onDismiss = { sleepOpen = false })
    qualities?.let { options ->
        QualitySheet(
            qualities = options,
            onPick = { picked ->
                qualities = null
                selected = picked
                qualityChoice.pick(picked)
                playback.setSource(ReplayQuality.urlFor(vod.variants, vod.masterUrl, picked), exo.currentPosition, vod.recording, exo.playWhenReady)
            },
            onDismiss = { qualities = null },
        )
    }
}

private const val MILLIS = 1_000L
private const val POLL_MS = 500L
private const val SAVE_EVERY_MS = 10_000L
