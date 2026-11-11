package com.twitchplayer.tv.feature.vod.ui

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.twitchplayer.tv.R
import com.twitchplayer.tv.core.data.repository.SettingsRepository
import com.twitchplayer.tv.core.designsystem.component.EmptyState
import com.twitchplayer.tv.core.designsystem.component.TvButton
import com.twitchplayer.tv.core.designsystem.component.TvSpinner
import com.twitchplayer.tv.core.designsystem.icon.TvIcons
import com.twitchplayer.tv.core.designsystem.theme.LocalMotion
import com.twitchplayer.tv.core.designsystem.theme.TvColors
import com.twitchplayer.tv.core.designsystem.theme.TvDimens
import com.twitchplayer.tv.core.designsystem.theme.TvShapes
import com.twitchplayer.tv.core.designsystem.theme.TvType
import com.twitchplayer.tv.core.platform.NumberFormatter
import com.twitchplayer.tv.core.ui.formatUptime
import com.twitchplayer.tv.feature.clip.ui.Hint
import com.twitchplayer.tv.feature.clip.ui.ProgressLine
import com.twitchplayer.tv.feature.player.data.stream.QualityPolicy
import com.twitchplayer.tv.feature.player.data.stream.ResolvedVod
import com.twitchplayer.tv.feature.player.data.stream.VodResolver
import com.twitchplayer.tv.feature.player.playback.VideoQualities
import com.twitchplayer.tv.feature.player.playback.VideoQuality
import com.twitchplayer.tv.feature.player.ui.QualityDialog
import com.twitchplayer.tv.feature.vod.data.VodProgress
import com.twitchplayer.tv.feature.vod.data.VodProgressStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private const val TAG = "VodScreen"
private const val CHROME_MS = 4_000L
private const val SKIP_BUBBLE_MS = 800L
private const val SEEK_APPLY_DELAY_MS = 250L
private const val SAVE_EVERY_MS = 5_000L

private sealed interface VodLoad {
    data object Loading : VodLoad
    data class Ready(val vod: ResolvedVod, val saved: VodProgress?, val preferredQuality: String?) : VodLoad
    data class Failed(val error: VodError) : VodLoad
}

/**
 * Plays one past broadcast full screen. Resolves it (token + master playlist, see [com.twitchplayer.tv.feature.player.data.stream.GqlVodResolver]),
 * offers to resume where the viewer stopped, then plays it with Media3. Left / Right seek (hold for bigger jumps), OK pauses,
 * Down reaches the quality and speed buttons, Back returns to the channel.
 */
@OptIn(UnstableApi::class)
@Composable
fun VodRoute(
    id: String,
    title: String,
    durationSeconds: Int,
    onBack: () -> Unit,
    resolver: VodResolver = koinInject(),
    store: VodProgressStore = koinInject(),
    settings: SettingsRepository = koinInject(),
) {
    var load by remember(id) { mutableStateOf<VodLoad>(VodLoad.Loading) }
    var attempt by remember(id) { mutableIntStateOf(0) }
    // After a playback error: where it stopped, so Retry continues there without asking again.
    var continueAt by remember(id) { mutableStateOf<Long?>(null) }
    LaunchedEffect(id, attempt) {
        load = try {
            val vod = resolver.resolve(id)
            VodLoad.Ready(vod, store.progress.first()[id], settings.settings.first().preferredQuality)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "replay $id cannot be resolved: $e")
            VodLoad.Failed(e.toVodError())
        }
    }
    BackHandler(onBack = onBack)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (val state = load) {
            VodLoad.Loading -> Box(Modifier.align(Alignment.Center)) { TvSpinner() }

            is VodLoad.Failed -> VodFailed(state.error, Modifier.align(Alignment.Center), onRetry = {
                load = VodLoad.Loading
                attempt++
            }, onBack = onBack)

            is VodLoad.Ready -> VodPlayback(
                vod = state.vod,
                progressId = id,
                title = title,
                saved = state.saved,
                preferredQuality = state.preferredQuality,
                knownDurationMs = durationSeconds * 1000L,
                continueAt = continueAt,
                store = store,
                settings = settings,
                onFailed = { error, position ->
                    continueAt = position
                    load = VodLoad.Failed(error)
                },
            )
        }
    }
}

@Composable
private fun VodFailed(error: VodError, modifier: Modifier, onRetry: () -> Unit, onBack: () -> Unit) {
    val action = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { action.requestFocus() } }
    EmptyState(
        TvIcons.OffAir,
        stringResource(error.title),
        modifier,
        line = stringResource(error.line),
        action = {
            if (error.retryable) {
                TvButton(stringResource(R.string.state_retry), TvIcons.Replay, onRetry, Modifier.focusRequester(action), primary = true)
            } else {
                TvButton(stringResource(R.string.cd_back), TvIcons.ChevronLeft, onBack, Modifier.focusRequester(action), primary = true)
            }
        },
    )
}

@OptIn(UnstableApi::class)
@Composable
private fun VodPlayback(
    vod: ResolvedVod,
    /** The id the channel screen knows the replay by (it keys the saved position). */
    progressId: String,
    title: String,
    saved: VodProgress?,
    preferredQuality: String?,
    knownDurationMs: Long,
    continueAt: Long?,
    store: VodProgressStore,
    settings: SettingsRepository,
    onFailed: (VodError, Long) -> Unit,
) {
    val context = LocalContext.current
    val appScope: CoroutineScope = koinInject()
    val uiScope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val motion = LocalMotion.current

    // What to start from: a retry continues; else a saved position the viewer is asked about; else the beginning.
    val offer = remember(vod.vodId) { if (continueAt == null && saved?.resumable == true) saved else null }
    var startAt by remember(vod.vodId) { mutableStateOf<Long?>(if (offer != null) null else continueAt ?: 0L) }

    val variants = vod.variants
    var selected by remember(vod.vodId) { mutableStateOf(QualityPolicy.choose(variants, preferredQuality)?.id ?: QualityPolicy.AUTO) }
    val player = remember(vod.vodId) {
        ExoPlayer.Builder(
            context,
            DefaultRenderersFactory(context).setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF).setEnableDecoderFallback(true),
        )
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .build()
    }
    val http = remember { DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true).setConnectTimeoutMs(8_000).setReadTimeoutMs(8_000) }
    fun source(id: String): MediaSource {
        val url = variants.firstOrNull { it.id == id }?.url ?: vod.masterUrl
        // A replay is not live: normal buffering, no live offset. One still being recorded has no end tag, so ExoPlayer calls it
        // live; it is pinned to speed 1 so it never "catches up" to the edge by itself.
        val item = MediaItem.Builder().setUri(url).apply {
            if (vod.recording) setLiveConfiguration(MediaItem.LiveConfiguration.Builder().setMinPlaybackSpeed(1f).setMaxPlaybackSpeed(1f).build())
        }.build()
        return if (url.contains(".m3u8")) {
            HlsMediaSource.Factory(http).createMediaSource(item)
        } else {
            ProgressiveMediaSource.Factory(DefaultDataSource.Factory(context)).createMediaSource(item)
        }
    }

    var playing by remember { mutableStateOf(false) }
    // The viewer's intent: true while playback is paused on purpose (buffering after a seek is not a pause).
    var paused by remember { mutableStateOf(false) }
    var ended by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1f) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(knownDurationMs.coerceAtLeast(vod.durationMs)) }
    // A seek in progress: shown at once, applied to the player when the keys rest (holding a key is many steps, one real seek).
    var pending by remember { mutableStateOf<Long?>(null) }
    var pendingAt by remember { mutableLongStateOf(0L) }
    var started by remember { mutableStateOf(false) }

    DisposableEffect(player, lifecycle) {
        var initialSeekDone = false
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Log.w(TAG, "replay ${vod.vodId} player error ${error.errorCodeName}: ${error.message}", error)
                val failure = if (error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                    error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
                ) {
                    VodError.Network
                } else {
                    VodError.Playback
                }
                onFailed(failure, player.currentPosition.coerceAtLeast(0L))
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                paused = !playWhenReady
            }
            override fun onPlaybackStateChanged(state: Int) {
                ended = state == Player.STATE_ENDED
                if (state == Player.STATE_READY && !initialSeekDone) {
                    initialSeekDone = true
                    // A replay still being recorded opens at its live edge: put it where the viewer chose.
                    val want = startAt
                    if (player.isCurrentMediaItemLive && want != null && kotlin.math.abs(player.currentPosition - want) > 5_000) player.seekTo(want)
                }
            }
            override fun onPlaybackParametersChanged(parameters: androidx.media3.common.PlaybackParameters) {
                speed = parameters.speed
            }
        }
        player.addListener(listener)
        player.setMediaSource(source(selected), startAt ?: 0L)
        player.prepare()
        player.playWhenReady = startAt != null
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) player.pause() }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            // Remember where it stopped (a finished replay is forgotten); written on the app scope, the screen is going away.
            if (started) {
                val at = pending ?: player.currentPosition.coerceAtLeast(0L)
                val total = if (player.duration > 0 && !vod.recording) player.duration else duration
                val finished = player.playbackState == Player.STATE_ENDED
                appScope.launch { if (finished) store.clear(progressId) else store.save(progressId, at, total) }
            }
            player.release()
        }
    }

    // The viewer's answer to "resume?" (or the automatic start) begins playback.
    LaunchedEffect(startAt) {
        val at = startAt ?: return@LaunchedEffect
        started = true
        // Also for 0: a replay still being recorded opens at its live edge, not at its start.
        if (kotlin.math.abs(player.currentPosition - at) > 1_000) player.seekTo(at)
        position = at
        player.playWhenReady = true
    }

    val focus = remember { FocusRequester() }
    val qualityButton = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (offer == null) runCatching { focus.requestFocus() } }
    var chromeUntil by remember { mutableLongStateOf(System.currentTimeMillis() + CHROME_MS) }
    var recent by remember { mutableStateOf(true) }
    var rowFocused by remember { mutableStateOf(false) }
    var showQuality by remember { mutableStateOf(false) }
    // The bar stays while paused, finished, or while a button holds focus: the viewer is deciding something.
    val chromeVisible = recent || paused || ended || rowFocused
    LaunchedEffect(chromeUntil) {
        recent = true
        while (System.currentTimeMillis() < chromeUntil) delay(200)
        recent = false
    }
    // Position readout: fast while it moves on screen, slow when only a paused bar is up; nothing while hidden.
    LaunchedEffect(chromeVisible, playing) {
        while (chromeVisible || playing) {
            if (pending == null) {
                position = player.currentPosition.coerceAtLeast(0L)
                duration = player.duration.takeIf { it > 0 } ?: duration
            }
            delay(if (chromeVisible && playing) 250 else 1_000)
        }
    }
    // Save where the viewer is every few seconds while playing, so a crash or a power-off loses little.
    LaunchedEffect(player, started) {
        while (started) {
            delay(SAVE_EVERY_MS)
            if (player.isPlaying && pending == null) {
                val at = player.currentPosition
                val total = if (player.duration > 0 && !vod.recording) player.duration else duration
                appScope.launch { store.save(progressId, at, total) }
            }
        }
    }
    LaunchedEffect(ended) { if (ended) appScope.launch { store.clear(progressId) } }
    LaunchedEffect(pendingAt) {
        if (pendingAt == 0L) return@LaunchedEffect
        delay(SEEK_APPLY_DELAY_MS)
        pending?.let {
            player.seekTo(it)
            position = it
        }
        pending = null
    }

    var skipMs by remember { mutableLongStateOf(0L) }
    var skipAt by remember { mutableLongStateOf(0L) }
    var skipVisible by remember { mutableStateOf(false) }
    LaunchedEffect(skipAt) {
        if (skipAt == 0L) return@LaunchedEffect
        skipVisible = true
        delay(SKIP_BUBBLE_MS)
        skipVisible = false
    }
    fun seekBy(deltaMs: Long) {
        val from = pending ?: player.currentPosition
        val target = seekTarget(from, deltaMs, if (vod.recording) player.duration.coerceAtLeast(0L) else duration)
        skipMs = if (skipVisible && (skipMs > 0) == (deltaMs > 0)) skipMs + deltaMs else deltaMs
        skipAt = System.currentTimeMillis()
        pending = target
        pendingAt = skipAt
        position = target
        ended = false
    }

    Box(
        Modifier.fillMaxSize().focusRequester(focus)
            .onPreviewKeyEvent { event ->
                // The resume question has its own buttons.
                if (startAt == null) return@onPreviewKeyEvent false
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val visibleBefore = chromeVisible
                chromeUntil = System.currentTimeMillis() + CHROME_MS
                if (rowFocused) {
                    if (event.key == Key.DirectionUp) {
                        focus.requestFocus()
                        return@onPreviewKeyEvent true
                    }
                    return@onPreviewKeyEvent false
                }
                val repeat = event.nativeKeyEvent.repeatCount
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Spacebar, Key.MediaPlayPause -> {
                        if (player.playbackState == Player.STATE_ENDED) {
                            player.seekTo(0)
                            position = 0
                            player.playWhenReady = true
                        } else {
                            player.playWhenReady = !player.isPlaying
                        }
                        true
                    }

                    Key.MediaPlay -> {
                        player.playWhenReady = true
                        true
                    }

                    Key.MediaPause -> {
                        player.playWhenReady = false
                        true
                    }

                    Key.DirectionRight, Key.MediaFastForward -> {
                        seekBy(vodSeekStep(repeat))
                        true
                    }

                    Key.DirectionLeft, Key.MediaRewind -> {
                        seekBy(-vodSeekStep(repeat))
                        true
                    }

                    Key.MediaSkipForward, Key.MediaNext -> {
                        seekBy(JUMP_MS)
                        true
                    }

                    Key.MediaSkipBackward, Key.MediaPrevious -> {
                        seekBy(-JUMP_MS)
                        true
                    }

                    // Down reaches the buttons when the bar is already up; else the first press only brings the bar back.
                    Key.DirectionDown -> {
                        if (visibleBefore) runCatching { qualityButton.requestFocus() }
                        true
                    }

                    else -> false
                }
            }
            .focusable(),
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    isFocusable = false
                    descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    this.player = player
                }
            },
            update = { it.keepScreenOn = playing },
            modifier = Modifier.fillMaxSize(),
            onRelease = { it.player = null },
        )
        AnimatedVisibility(
            (paused || ended) && startAt != null,
            Modifier.align(Alignment.Center),
            enter = fadeIn(motion.fast()) + scaleIn(motion.fast(), initialScale = 1.2f),
            exit = fadeOut(motion.exit()),
        ) {
            Box(Modifier.size(80.dp).background(TvColors.Black60, CircleShape), contentAlignment = Alignment.Center) {
                Icon(if (ended) TvIcons.Replay else TvIcons.Play, stringResource(if (ended) R.string.clip_replay else R.string.player_play), tint = TvColors.Text, modifier = Modifier.size(40.dp))
            }
        }
        AnimatedVisibility(skipVisible, Modifier.align(Alignment.Center), enter = fadeIn(motion.fast()), exit = fadeOut(motion.exit())) {
            Text(
                (if (skipMs >= 0) "+" else "-") + formatUptime(kotlin.math.abs(skipMs)),
                style = TvType.Headline,
                color = TvColors.Text,
                modifier = Modifier.background(TvColors.Black60, TvShapes.Chip).padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }
        AnimatedVisibility(chromeVisible && startAt != null, Modifier.align(Alignment.BottomStart).fillMaxWidth(), enter = fadeIn(motion.medium()), exit = fadeOut(motion.exit())) {
            Column(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(0f to Color.Transparent, 0.45f to TvColors.Black60, 1f to TvColors.Black85))
                    .padding(start = TvDimens.SafeHorizontal, end = TvDimens.SafeHorizontal, top = 64.dp, bottom = TvDimens.SafeBottom),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(title, style = TvType.PlayerName.copy(shadow = Shadow(TvColors.Black80, Offset(0f, 1f), 3f)), color = TvColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(formatUptime(position) + " / " + formatUptime(duration), style = TvType.MetaNumeric, color = TvColors.TextSecondary, maxLines = 1)
                }
                ProgressLine(if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f)
                Row(Modifier.onFocusChanged { rowFocused = it.hasFocus }, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    val qualityLabel = if (selected == QualityPolicy.AUTO) stringResource(R.string.quality_auto) else variants.firstOrNull { it.id == selected }?.name ?: selected
                    TvButton(qualityLabel, TvIcons.Quality, { showQuality = true }, Modifier.focusRequester(qualityButton))
                    val numbers: NumberFormatter = koinInject()
                    if (!vod.recording) TvButton(speedLabel(speed, numbers), null, { player.setPlaybackSpeed(nextSpeed(player.playbackParameters.speed)) })
                    androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    val step = remember {
                        android.icu.text.MeasureFormat.getInstance(java.util.Locale.getDefault(), android.icu.text.MeasureFormat.FormatWidth.NARROW)
                            .format(android.icu.util.Measure(10, android.icu.util.MeasureUnit.SECOND))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Hint(TvIcons.ChevronLeft, TvIcons.ChevronRight, step)
                        Hint(if (paused || ended) TvIcons.Play else TvIcons.Pause, null, null)
                    }
                }
            }
        }
        if (startAt == null && offer != null) {
            ResumeOffer(
                offer,
                onResume = { startAt = offer.positionMs },
                onStartOver = { startAt = 0L },
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }

    if (showQuality) {
        val options = buildList {
            add(VideoQuality(QualityPolicy.AUTO, stringResource(R.string.quality_auto)))
            variants.forEach { add(VideoQuality(it.id, if (it.audioOnly) stringResource(R.string.quality_audio_only) else it.name)) }
        }
        QualityDialog(
            VideoQualities(options, selected),
            onPick = { id ->
                showQuality = false
                uiScope.launch {
                    delay(50)
                    runCatching { focus.requestFocus() }
                }
                if (id != selected) {
                    selected = id
                    // Same stored choice as the live player: one preferred quality for the whole app.
                    appScope.launch { settings.setPreferredQuality(id) }
                    // Same replay, same place, other rendition.
                    val at = pending ?: player.currentPosition
                    val resume = player.playWhenReady
                    player.setMediaSource(source(id), at)
                    player.prepare()
                    player.playWhenReady = resume
                }
            },
            onDismiss = {
                showQuality = false
                uiScope.launch {
                    delay(50)
                    runCatching { focus.requestFocus() }
                }
            },
        )
    }
}

/** "Resume from 1:23:45" (focused) or "Start over": shown over the paused first frame. */
@Composable
private fun ResumeOffer(saved: VodProgress, onResume: () -> Unit, onStartOver: () -> Unit, modifier: Modifier) {
    val resume = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { resume.requestFocus() } }
    Column(modifier.width(360.dp).background(TvColors.Black60, TvShapes.Card).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TvButton(stringResource(R.string.vod_resume, formatUptime(saved.positionMs)), TvIcons.Play, onResume, Modifier.fillMaxWidth().focusRequester(resume), primary = true)
        TvButton(stringResource(R.string.vod_start_over), TvIcons.Replay, onStartOver, Modifier.fillMaxWidth())
    }
}
