package com.twitchplayer.tv.feature.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.widthIn
import com.twitchplayer.tv.feature.player.playback.AdBreak
import com.twitchplayer.tv.feature.player.playback.PlaybackStats
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.util.lerp
import kotlin.math.roundToInt
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.twitchplayer.tv.R
import com.twitchplayer.tv.core.data.model.ChatMode
import com.twitchplayer.tv.core.data.model.ChatSettings
import com.twitchplayer.tv.core.designsystem.component.EmptyState
import com.twitchplayer.tv.core.designsystem.component.LocalToast
import com.twitchplayer.tv.core.designsystem.component.NavRail
import com.twitchplayer.tv.core.designsystem.component.NavRailItem
import com.twitchplayer.tv.core.designsystem.component.TvIconButton
import com.twitchplayer.tv.core.designsystem.icon.TvIcons
import com.twitchplayer.tv.core.designsystem.theme.LocalMotion
import com.twitchplayer.tv.core.designsystem.theme.TvColors
import com.twitchplayer.tv.core.designsystem.theme.TvDimens
import com.twitchplayer.tv.core.designsystem.theme.TvShapes
import com.twitchplayer.tv.core.designsystem.theme.TvType
import com.twitchplayer.tv.core.ui.TvFocusable
import com.twitchplayer.tv.feature.player.presentation.ChatLine
import com.twitchplayer.tv.feature.player.presentation.PlayerCommand
import com.twitchplayer.tv.feature.player.presentation.PlayerUiState
import com.twitchplayer.tv.feature.player.presentation.PlayerViewModel
import com.twitchplayer.tv.BuildConfig
import com.twitchplayer.tv.core.data.model.PlayerEngine
import com.twitchplayer.tv.feature.player.data.stream.TwitchPlaybackResolver
import com.twitchplayer.tv.feature.player.playback.NativeStatus
import com.twitchplayer.tv.feature.player.playback.NativeVideoSurface
import com.twitchplayer.tv.feature.player.playback.VideoPlayer
import com.twitchplayer.tv.core.data.repository.SendResult
import com.twitchplayer.tv.feature.player.data.chat.ChannelNotice
import com.twitchplayer.tv.feature.player.data.chat.RoomState
import com.twitchplayer.tv.feature.player.playback.VideoQualities
import com.twitchplayer.tv.feature.player.playback.rememberNativeVideoPlayer
import com.twitchplayer.tv.feature.player.ui.embed.PlayerConfigProvider
import com.twitchplayer.tv.feature.player.ui.embed.TwitchEmbedPlayer
import com.twitchplayer.tv.feature.player.ui.embed.TwitchPlayerConfig
import com.twitchplayer.tv.feature.player.ui.embed.WebViewVideoPlayer
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import org.koin.compose.koinInject

/**
 * The player lives above the navigation stack so playback survives screen changes. [fullScreen] is true while the
 * player screen is on top; otherwise the same embed keeps playing as a small picture in the bottom-right corner.
 * Nothing is composed (and the embed is released) until a session exists and after it is closed.
 */
@Composable
fun PlayerHost(
    viewModel: PlayerViewModel,
    fullScreen: Boolean,
    dockItems: List<NavRailItem>,
    onDockSelect: (Int) -> Unit,
    onBack: () -> Unit,
    onOpenChannel: (String) -> Unit,
    onExpand: () -> Unit,
    config: TwitchPlayerConfig? = koinInject<PlayerConfigProvider>().config,
) {
    val state = viewModel.state.collectAsState()
    val active by remember { derivedStateOf { state.value.active } }
    if (active) PlayerHostContent(viewModel, fullScreen, dockItems, onDockSelect, onBack, onOpenChannel, onExpand, config)
}

@Composable
private fun PlayerHostContent(
    viewModel: PlayerViewModel,
    fullScreen: Boolean,
    dockItems: List<NavRailItem>,
    onDockSelect: (Int) -> Unit,
    onBack: () -> Unit,
    onOpenChannel: (String) -> Unit,
    onExpand: () -> Unit,
    config: TwitchPlayerConfig?,
) {
    // A session exists (full screen or docked picture): the TV must not fall asleep under a playing stream.
    com.twitchplayer.tv.core.ui.KeepScreenOn()
    // Chat messages are collected by the chat panel only (and only while it shows): a new batch never recomposes this.
    val state by viewModel.state.collectAsState()
    val chatSettings by viewModel.chatSettings.collectAsState()
    val chatStyle = remember(chatSettings) { chatSettings.toStyle() }
    val webPlayer = remember { WebViewVideoPlayer() }
    // Two players behind one VideoPlayer. The native one (Media3) is used when the setting and the build allow it, and
    // gives a channel to the WebView player for good (until another channel is picked) when it cannot resolve or play it.
    val engine by viewModel.playerEngine.collectAsState()
    val preferredQuality by viewModel.preferredQuality.collectAsState()
    val currentPreferred by rememberUpdatedState(preferredQuality)
    val nativePlayer = if (BuildConfig.NATIVE_PLAYER_ENABLED && engine == PlayerEngine.Native) {
        rememberNativeVideoPlayer(koinInject<TwitchPlaybackResolver>(), { currentPreferred }, viewModel::setPreferredQuality)
    } else null
    var fallbackLogin by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(nativePlayer) {
        nativePlayer?.status?.collect { status ->
            if (status is NativeStatus.Failed) {
                android.util.Log.w("PlayerHost", "native player failed for ${status.channel}, using the WebView player: ${status.cause}")
                fallbackLogin = status.channel
            }
        }
    }
    val useNative = nativePlayer != null && !state.login.equals(fallbackLogin, ignoreCase = true)
    val player: VideoPlayer = if (useNative) nativePlayer else webPlayer
    LaunchedEffect(nativePlayer, useNative, state.login) {
        if (useNative) nativePlayer.load(state.login) else nativePlayer?.stop()
    }
    val toast = LocalToast.current
    val unavailable = stringResource(R.string.player_quality_unavailable)
    val showStats by viewModel.showStats.collectAsState()
    LaunchedEffect(player, showStats) { player.setStatsEnabled(showStats) }
    LaunchedEffect(player) { player.adBreak.map { it != null }.distinctUntilChanged().collect { viewModel.setAdActive(it) } }
    var composing by remember { mutableStateOf(false) }
    val needsSignIn = stringResource(R.string.chat_needs_signin)
    val sendFailed = stringResource(R.string.chat_send_failed)
    val dropped = stringResource(R.string.chat_dropped)
    LaunchedEffect(viewModel) {
        viewModel.sent.collect { result ->
            when (result) {
                SendResult.Sent -> Unit
                is SendResult.Dropped -> toast.show(result.reason.ifBlank { dropped })
                SendResult.NeedsSignIn -> toast.show(needsSignIn)
                SendResult.Failed -> toast.show(sendFailed)
            }
        }
    }
    var interactive by remember { mutableStateOf(false) }
    var dockOpen by remember { mutableStateOf(false) }
    var qualities by remember { mutableStateOf<VideoQualities?>(null) }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(viewModel, player) {
        viewModel.commands.collect { command ->
            when (command) {
                PlayerCommand.Play -> player.play()
                PlayerCommand.Pause -> player.pause()
                PlayerCommand.ToggleMute -> player.toggleMute()
            }
        }
    }
    LaunchedEffect(fullScreen) {
        viewModel.setMinimized(!fullScreen)
        if (!fullScreen) { dockOpen = false; interactive = false }
    }

    // Back closes the topmost layer: embed focus mode, dock, suggestions, chat options, the bottom overlay, chat, then the screen.
    val handleBack = {
        when {
            interactive -> { interactive = false; player.setInteractive(false) }
            dockOpen -> dockOpen = false
            viewModel.handleBack() -> Unit
            else -> onBack()
        }
    }
    BackHandler(enabled = fullScreen, onBack = handleBack)

    val focus = remember { PlayerControl.entries.associateWith { FocusRequester() } }
    val infoFocus = remember { FocusRequester() }
    val dockFocus = remember { FocusRequester() }
    val chatHeaderFocus = remember { FocusRequester() }
    val chatFocus = remember { FocusRequester() }
    val chatOptionsFocus = remember { FocusRequester() }
    var lastControl by remember { mutableStateOf(PlayerControl.Pause) }
    var infoFocused by remember { mutableStateOf(false) }
    var focusInChat by remember { mutableStateOf(false) }
    // Never hand focus back to "Back": a stray OK after closing a layer would leave the player.
    fun restoreControl() { runCatching { focus.getValue(if (lastControl == PlayerControl.Back) PlayerControl.Pause else lastControl).requestFocus() } }
    LaunchedEffect(fullScreen) { if (fullScreen) runCatching { focus.getValue(PlayerControl.Pause).requestFocus() } }

    val sheet = remember { PlayerSheet() }
    // Layers: 0 = video alone, 1 = bottom overlay (stream info + controls), then rows of streams below the controls.
    // Like a feed: Down scrolls further down (controls -> first row -> next rows), Up comes back (rows -> controls ->
    // stream info -> video alone), Back closes the topmost layer.
    // Remote, keyboard (arrows, Esc) and mouse (move = activity, wheel = Up / Down) all go through these.
    fun goUp() {
        when {
            // The first row closes the rows (focus search would otherwise land on the controls above it).
            state.zapperOpen -> if (sheet.focusedRow == 0 || !focusManager.moveFocus(FocusDirection.Up)) viewModel.closeZapper()
            !state.controlsVisible -> viewModel.wake()
            !infoFocused -> runCatching { infoFocus.requestFocus() }
            else -> viewModel.hideOverlay()
        }
    }
    fun goDown() {
        when {
            state.zapperOpen -> focusManager.moveFocus(FocusDirection.Down)
            !state.controlsVisible -> viewModel.wake()
            infoFocused -> restoreControl()
            else -> viewModel.openZapper()
        }
    }

    val motion = LocalMotion.current
    val overlayShown = state.controlsVisible
    val barAlpha = animateFloatAsState(if (overlayShown) 1f else 0f, motion.toward(overlayShown), label = "bar")
    val chatDim = animateFloatAsState(if (state.zapperOpen) 0.7f else 0f, motion.toward(state.zapperOpen), label = "chatDim")
    val overlayChatAlpha = animateFloatAsState(if (state.zapperOpen) 0f else 1f, motion.toward(!state.zapperOpen), label = "overlayChat")

    val density = LocalDensity.current
    val windowSize = LocalWindowInfo.current.containerSize
    val screenWidth = with(density) { windowSize.width.toDp() }
    val screenHeight = with(density) { windowSize.height.toDp() }
    // The chat column holds a text area of 24 % / 28 % of the screen plus its insets;
    // the video keeps at least 2/3 of the width. Over-the-video chat leaves the video full width.
    val columnWidth = { mode: ChatMode ->
        val fraction = when (mode) { ChatMode.Compact -> TvDimens.ChatCompactFraction; else -> 0f }
        if (fraction == 0f) 0.dp else minOf(
            screenWidth * fraction + TvDimens.ChatInsetStart + TvDimens.ChatInsetEnd,
            screenWidth * (1f - TvDimens.VideoMinFraction),
        )
    }
    // The video is a hardware overlay that Chromium places on its own clock: moving or scaling it frame by frame makes
    // the picture wobble behind the app's frames. So it is resized once, at the start of a chat change, and only the
    // chat moves (transform only): opening, the column slides in next to the already narrower picture; closing, the
    // opaque column slides away from the picture already at full width underneath. No cover, no settle delay.
    val chatMode = state.effectiveChat
    val chatWidth = columnWidth(chatMode)
    val videoWidth = screenWidth - chatWidth
    val videoHeight = minOf(videoWidth * 9f / 16f, screenHeight)
    val videoTop = (screenHeight - videoHeight) / 2
    val controlsEnabled = !state.zapperOpen && !dockOpen
    val miniWidthPx = with(density) { (TvDimens.MiniWidth * TvDimens.MiniScale).roundToPx() }
    val miniHeightPx = with(density) { (TvDimens.MiniHeight * TvDimens.MiniScale).roundToPx() }
    val miniEndPx = with(density) { TvDimens.SafeHorizontal.roundToPx() }
    val miniBottomPx = with(density) { TvDimens.SafeBottom.roundToPx() }
    val columnPx = with(density) { columnWidth(chatMode).toPx() }
    val videoWidthPx by rememberUpdatedState(with(density) { videoWidth.toPx() })

    // A new one per full-screen session: the chat is simply there (or not) when the player expands.
    val chatMotion = remember(fullScreen) { ChatMotion(shown = false) }
    // Entering full screen from the docked picture, a chat that was open is not composed during the expansion: it mounts once
    // the picture has nearly settled and slides in then (laying out dozens of messages mid-transition made it stutter).
    // Native video: its frame (size and place) follows the chat's own animation and the mini <-> full-screen change, read in
    // the layout phase of one node (no recomposition). One SurfaceView resize per frame is a SurfaceControl transaction
    // synced with the app's frames; the WebView overlay could not do that (it jittered), so that path still resizes once.
    val expand = remember { Animatable(if (fullScreen) 1f else 0f) }
    LaunchedEffect(fullScreen) { expand.animateTo(if (fullScreen) 1f else 0f, if (fullScreen) motion.glide() else motion.glideOut()) }
    val miniSettled by remember { derivedStateOf { expand.value < 0.02f } }
    val visibleChatPx = {
        val m = chatMotion
        if (m.overlay) 0f else {
            val width = if (m.widthPx > 0f) m.widthPx else columnPx
            val column = if (m.column.value == 0f) width else m.column.value
            (column - m.away.value * width).coerceAtLeast(0f)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .then(
                if (!fullScreen) Modifier else Modifier
                    // Native video: the SurfaceView punches its own hole and the letterbox shows the app root / window (same
                    // colour), so this fill would only be one more full-screen pass over the picture.
                    .then(if (useNative) Modifier else Modifier.background(TvColors.Body))
                    .onPreviewKeyEvent { event ->
                        // Back is a plain key on a TV remote: handle it here (on release) so consecutive presses are never dropped.
                        if (event.key == Key.Back || event.key == Key.Escape) {
                            if (event.type == KeyEventType.KeyUp) handleBack()
                            return@onPreviewKeyEvent true
                        }
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        // Layer 0: the first key (Up / Down included) only brings the overlay back, focus stays where it was.
                        // Chat keeps its keys: scrolling the history never needs a wake-up press.
                        val hidden = !state.controlsVisible && !state.zapperOpen && !dockOpen
                        if (hidden && !focusInChat) { viewModel.wake(); return@onPreviewKeyEvent true }
                        viewModel.wake()
                        false
                    }
                    .onKeyEvent { event ->
                        // Only keys nothing else used get here (chat scrolling, option steps and lists consume theirs).
                        if (event.type != KeyEventType.KeyDown || dockOpen || interactive || focusInChat) return@onKeyEvent false
                        when (event.key) {
                            Key.DirectionUp -> { goUp(); true }
                            Key.DirectionDown -> { goDown(); true }
                            // Nothing further left (the focus search found no neighbour): reveal the dock.
                            Key.DirectionLeft -> if (state.zapperOpen) false else { if (!focusManager.moveFocus(FocusDirection.Left)) dockOpen = true; true }
                            else -> false
                        }
                    }
                    .pointerInput(Unit) {
                        // Observed on the way down (never consumed): the embed and the buttons still get every event.
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull() ?: continue
                                when (event.type) {
                                    PointerEventType.Scroll -> if (change.position.x < videoWidthPx && !dockOpen) {
                                        if (change.scrollDelta.y < 0f) goUp() else if (change.scrollDelta.y > 0f) goDown()
                                    }
                                    PointerEventType.Move, PointerEventType.Press -> if (!state.zapperOpen) viewModel.wake()
                                    else -> Unit
                                }
                            }
                        }
                    },
            ),
    ) {
        if (useNative) {
            // The PlayerView letterboxes the picture itself inside the frame; only the frame animates.
            NativeVideoSurface(
                player = nativePlayer,
                modifier = Modifier.videoFrame(
                    expand = { expand.value }, visibleChatPx = visibleChatPx,
                    miniWidth = miniWidthPx, miniHeight = miniHeightPx, miniEnd = miniEndPx, miniBottom = miniBottomPx,
                ),
            )
        } else if (config == null) {
            if (fullScreen) EmptyState(TvIcons.OffAir, stringResource(R.string.player_unconfigured), Modifier.align(Alignment.Center), line = stringResource(R.string.player_unconfigured_line))
        } else {
            // Full screen: the viewport is never resized by overlays and keeps Twitch's >= 400x300 minimum. It changes
            // size once per chat mode change (never per animation frame).
            // Mini: a fixed-size viewport drawn at a fraction of its size in the bottom-right corner.
            TwitchEmbedPlayer(
                channelLogin = state.login,
                config = config,
                controller = webPlayer,
                modifier = if (fullScreen) {
                    Modifier.offset(y = videoTop).size(videoWidth, videoHeight)
                } else {
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = TvDimens.SafeHorizontal, bottom = TvDimens.SafeBottom)
                        .size(TvDimens.MiniWidth, TvDimens.MiniHeight)
                        .graphicsLayer { scaleX = TvDimens.MiniScale; scaleY = TvDimens.MiniScale; transformOrigin = TransformOrigin(1f, 1f) }
                },
            )
        }

        if (!fullScreen) {
            // Native video: the focus frame appears once the picture has landed in its corner.
            if (!useNative || miniSettled) {
            val miniLabel = stringResource(R.string.player_mini)
            TvFocusable(
                onClick = onExpand,
                shape = TvShapes.Card,
                description = miniLabel,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = TvDimens.SafeHorizontal, bottom = TvDimens.SafeBottom)
                    .size(TvDimens.MiniWidth * TvDimens.MiniScale, TvDimens.MiniHeight * TvDimens.MiniScale),
            ) { Box(Modifier.matchParentSize().border(1.dp, TvColors.Divider, TvShapes.Card)) }
            }
        } else {
            FullScreenLayers(
                state = state, chatMode = chatMode, videoWidth = videoWidth, screenHeight = screenHeight,
                barAlpha = { barAlpha.value }, chatDim = { chatDim.value }, overlayChatAlpha = { overlayChatAlpha.value },
                sheet = sheet,
                controlsEnabled = controlsEnabled, focus = focus, infoFocus = infoFocus,
                onFocused = { lastControl = it },
                onInfoFocused = { infoFocused = it },
                onBack = onBack,
                onOpenChannel = { onOpenChannel(state.login) },
                viewModel = viewModel,
                onPauseLong = { if (config != null && !useNative) { interactive = true; player.setInteractive(true) } },
                onQuality = {
                    player.loadQualities { result ->
                        if (result.options.isEmpty()) toast.show(unavailable) else qualities = result
                    }
                },
                onWrite = { viewModel.ensureChatVisible(); composing = true },
                chat = ChatLayerSpec(
                    lines = viewModel.chat,
                    style = chatStyle,
                    settings = chatSettings,
                    columnWidth = columnWidth,
                    motion = chatMotion,
                    headerFocus = chatHeaderFocus,
                    panelFocus = chatFocus,
                    optionsFocus = chatOptionsFocus,
                    onLeave = ::restoreControl,
                    onFocusInside = { focusInChat = it },
                    warm = lastControl == PlayerControl.Chat && state.controlsVisible,
                    ready = { expand.value > 0.95f },
                ),
            )
            StatusLayers(player, showStats, state.notice, videoWidth)

            // The dock: the navigation rail slides over the player when nothing is left of the controls.
            AnimatedVisibility(
                visible = dockOpen,
                modifier = Modifier.align(Alignment.CenterStart),
                enter = slideInHorizontally(motion.medium()) { -it } + fadeIn(motion.medium()),
                exit = slideOutHorizontally(motion.exit()) { -it } + fadeOut(motion.exit()),
            ) {
                NavRail(
                    items = dockItems,
                    selectedIndex = -1,
                    onSelect = { dockOpen = false; onDockSelect(it) },
                    selectedRequester = dockFocus,
                    compactOnly = true,
                    modifier = Modifier.onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight) { dockOpen = false; true } else false
                    },
                )
            }
        }
    }

    if (composing) {
        ChatComposeDialog(onSend = { viewModel.sendChat(it); composing = false }, onDismiss = { composing = false })
    }
    qualities?.let { current ->
        QualityDialog(current, onPick = { id -> player.setQuality(id); qualities = null }, onDismiss = { qualities = null })
    }

    // Opening the dock moves focus into it; closing it (or the suggestions) returns focus to the last used control.
    LaunchedEffect(dockOpen) {
        if (dockOpen) runCatching { dockFocus.requestFocus() }
        else if (fullScreen) restoreControl()
    }
    LaunchedEffect(state.zapperOpen) {
        if (!state.zapperOpen && fullScreen) restoreControl()
    }
    // Chat closing under the focus gives it back to the controls.
    LaunchedEffect(state.chatVisible) { if (!state.chatVisible && fullScreen && focusInChat) restoreControl() }
    // The options take focus when opened from the chat header, and give it back to the header.
    var optionsWereOpen by remember { mutableStateOf(false) }
    LaunchedEffect(state.chatSettingsOpen) {
        if (state.chatSettingsOpen) {
            optionsWereOpen = true
            withFrameNanos { }
            runCatching { chatOptionsFocus.requestFocus() }
        } else if (optionsWereOpen) {
            optionsWereOpen = false
            withFrameNanos { }
            if (state.chatVisible) runCatching { chatHeaderFocus.requestFocus() } else restoreControl()
        }
    }
}

/**
 * The chat column's motion, kept by the host for the whole full-screen session.
 * [away]: 0 = in place, 1 = gone. [column]: the column's visible width while it changes width (compact <-> large).
 */
@Stable
private class ChatMotion(shown: Boolean) {
    val away = Animatable(if (shown) 0f else 1f)
    val column = Animatable(0f)
    /** The column's laid-out width and whether it is the over-the-video kind; set by the chat layer, read by the video's layout. */
    var widthPx = 0f
    var overlay = false
}

/**
 * Waits until frames come back at their normal pace before a transition starts. Showing the chat lays out every message
 * that arrived while it was hidden: those first frames can take 100+ ms on a TV, and an animation started on them
 * skips most of its course (it looked like a cut). Gives up after a few frames.
 */
private suspend fun awaitSteadyFrames() {
    var last = withFrameNanos { it }
    repeat(6) {
        val now = withFrameNanos { it }
        if (now - last < 25_000_000L) return
        last = now
    }
}

/** What the chat layer needs from the host, grouped so the call site stays readable. */
private class ChatLayerSpec(
    val lines: StateFlow<ImmutableList<ChatLine>>,
    val style: ChatStyle,
    val settings: ChatSettings,
    val columnWidth: (ChatMode) -> Dp,
    val motion: ChatMotion,
    val headerFocus: FocusRequester,
    val panelFocus: FocusRequester,
    val optionsFocus: FocusRequester,
    val onLeave: () -> Unit,
    val onFocusInside: (Boolean) -> Unit,
    /** The chat button is focused: the hidden chat already takes the latest messages, so showing it only slides it in. */
    val warm: Boolean = false,
    /** True once the mini <-> full-screen expansion has nearly settled: a chat that was already open mounts then. */
    val ready: () -> Boolean = { true },
)

/** Everything drawn over the video in full-screen mode: scrim, chat, the bottom overlay (layer 1) and the suggestions (layer 2). */
@Composable
private fun BoxScope.FullScreenLayers(
    state: PlayerUiState,
    chatMode: ChatMode,
    videoWidth: Dp,
    screenHeight: Dp,
    barAlpha: () -> Float,
    chatDim: () -> Float,
    overlayChatAlpha: () -> Float,
    sheet: PlayerSheet,
    controlsEnabled: Boolean,
    focus: Map<PlayerControl, FocusRequester>,
    infoFocus: FocusRequester,
    onFocused: (PlayerControl) -> Unit,
    onInfoFocused: (Boolean) -> Unit,
    onBack: () -> Unit,
    onOpenChannel: () -> Unit,
    viewModel: PlayerViewModel,
    onPauseLong: () -> Unit,
    onQuality: () -> Unit,
    onWrite: () -> Unit,
    chat: ChatLayerSpec,
) {
    val motion = LocalMotion.current
    val density = LocalDensity.current
    val gapPx = with(density) { SHEET_GAP.toPx() }
    // The sheet: stream info and controls, then the rows of streams laid out below the screen edge. Opening the rows
    // and moving between them only translates it (no layout), so the focused row ends at the bottom of the screen.
    LaunchedEffect(state.zapperOpen, sheet.focusedRow, sheet.rowHeightPx) {
        if (state.zapperOpen) sheet.composed = true
        val target = if (state.zapperOpen && sheet.rowHeightPx > 0) (sheet.focusedRow + 1) * sheet.rowHeightPx + gapPx else 0f
        sheet.shift.animateTo(target, if (state.zapperOpen) motion.medium() else motion.exit())
        if (!state.zapperOpen) { sheet.composed = false; sheet.focusedRow = 0 }
    }
    val shift = { sheet.shift.value }
    // The controls sit 64 dp above the bottom edge; once the rows are up that is a wide gap above the first row's title.
    // They come down by up to TUCK as the rows rise (same animation, no layout).
    val tuckPx = with(density) { SHEET_TUCK.toPx() }
    val tuck = { if (sheet.rowHeightPx > 0f) (sheet.shift.value / sheet.rowHeightPx).coerceIn(0f, 1f) * tuckPx else 0f }

    // One scrim under the bottom overlay, only while it shows: it sits on the video's bottom edge, not its centre.
    Box(
        Modifier.width(videoWidth).height(280.dp).align(Alignment.BottomStart)
            .graphicsLayer { alpha = barAlpha(); translationY = -shift() }
            // Below the gradient: the same dark backdrop continues past the bottom edge of the screen, so as the sheet rises
            // for the rows of streams they sit on one continuous backdrop (no seam, no clear picture behind the cards).
            .drawBehind { drawRect(SheetBackdrop, topLeft = Offset(0f, size.height), size = Size(size.width, size.height * 6)) }
            // Dark enough behind the stream name and the controls to read them over any picture.
            .background(Brush.verticalGradient(0f to Color.Transparent, 0.25f to TvColors.Black60, 0.5f to TvColors.Black80, 1f to SheetBackdrop)),
    )
    if (chatMode == ChatMode.Hidden || chatMode == ChatMode.Overlay) {
        // Wake hint: a thin line along the bottom of the video while the overlay is hidden.
        Box(
            Modifier.align(Alignment.BottomStart).width(videoWidth).height(2.dp)
                .graphicsLayer { alpha = (1f - barAlpha()) * 0.4f }
                .background(Color.White),
        )
    }

    ChatLayer(state, chatMode, screenHeight, barAlpha, chatDim, overlayChatAlpha, viewModel, chat)

    // Layer 1: stream info above the control row, bottom-left, rising slightly as it fades in.
    val infoEnd = if (chatMode == ChatMode.Overlay) TvDimens.ChatOverlay else TvDimens.SafeHorizontal
    Column(
        Modifier
            .align(Alignment.BottomStart)
            .width(videoWidth - infoEnd)
            .padding(start = TvDimens.SafeHorizontal - 4.dp, bottom = 64.dp)
            .graphicsLayer { val a = barAlpha(); alpha = a; translationY = (1f - a) * 12.dp.toPx() - shift() + tuck() }
            .focusProperties { canFocus = controlsEnabled },
    ) {
        PlayerHeader(
            login = state.login, stream = state.current,
            onOpenChannel = onOpenChannel,
            ticking = state.controlsVisible && !state.zapperOpen,
            modifier = Modifier.focusRequester(infoFocus).onFocusChanged { onInfoFocused(it.hasFocus) },
        )
        Spacer(Modifier.height(14.dp))
        PlayerControlBar(
            state = state,
            focus = focus,
            onFocused = onFocused,
            onBack = onBack,
            onPause = viewModel::togglePause,
            onPauseLong = onPauseLong,
            onMute = viewModel::toggleMute,
            onFullscreen = viewModel::toggleFullscreen,
            onPrevious = viewModel::previous,
            onNext = viewModel::next,
            onChat = viewModel::cycleChat,
            onQuality = onQuality,
            onWrite = onWrite,
            chatTarget = if (state.chatVisible) chat.headerFocus else null,
            modifier = Modifier.padding(start = 4.dp),
        )
    }

    // The rows, starting at the bottom edge of the screen and moving up with the sheet.
    if (sheet.composed) {
        val zapperLabel = stringResource(R.string.player_zapper)
        val screenPx = with(density) { screenHeight.toPx() }
        ZapRows(
            rows = state.zapRows,
            playingLogin = state.login,
            onZap = viewModel::zap,
            onRowFocused = { sheet.focusedRow = it },
            onRowHeight = { sheet.rowHeightPx = it.toFloat() },
            modifier = Modifier
                .align(Alignment.TopStart)
                .width(videoWidth)
                .wrapContentHeight(Alignment.Top, unbounded = true)
                .graphicsLayer { translationY = screenPx - shift() }
                .semantics { contentDescription = zapperLabel },
        )
    }
}

/** Where the sheet under the video stands: its upward shift, the focused row and the measured row height. */
@Stable
private class PlayerSheet {
    val shift = Animatable(0f)
    var focusedRow by mutableIntStateOf(0)
    var rowHeightPx by mutableFloatStateOf(0f)
    var composed by mutableStateOf(false)
}

/** The dark backdrop behind the bottom overlay and the rows under it. */
private val SheetBackdrop = Color.Black.copy(alpha = 0.92f)

/**
 * The native video's frame, one layout node: full screen minus the chat column's visible width (both read from animations
 * in the layout phase), or the mini corner, blended by [expand] (0 = mini, 1 = full screen). The child is measured at the exact
 * frame, so the PlayerView's AspectRatioFrameLayout letterboxes inside it.
 */
private fun Modifier.videoFrame(
    expand: () -> Float,
    visibleChatPx: () -> Float,
    miniWidth: Int, miniHeight: Int, miniEnd: Int, miniBottom: Int,
): Modifier = layout { measurable, constraints ->
    val maxW = constraints.maxWidth
    val maxH = constraints.maxHeight
    val t = expand()
    val fullW = (maxW - visibleChatPx()).roundToInt().coerceAtLeast(1)
    val w = lerp(miniWidth, fullW, t)
    val h = lerp(miniHeight, maxH, t)
    val x = lerp(maxW - miniEnd - miniWidth, 0, t)
    val y = lerp(maxH - miniBottom - miniHeight, 0, t)
    val placeable = measurable.measure(Constraints.fixed(w, h))
    // placeRelative: in a right-to-left language the chat column is on the left, so the picture sits on the right.
    layout(maxW, maxH) { placeable.placeRelative(x, y) }
}

/** Space kept between the focused row's bottom and the screen's bottom edge. */
private val SHEET_GAP = 24.dp

/** How far the controls come down towards the rows once they are up. */
private val SHEET_TUCK = 36.dp

/**
 * The chat, composed once for the whole full-screen session: hiding it slides it away (column) or fades it (over the
 * video) but keeps it composed with its scroll position and messages, so it comes back at once. While hidden it stops
 * collecting messages (no recomposition) and its emotes stop animating.
 *
 * Only transform and alpha animate. The column always has an opaque underlay, whatever the background setting, so the
 * video never shows through it, even while it moves. Changing width lays out once at the new width and slides from the old edge.
 */
@Composable
private fun BoxScope.ChatLayer(
    state: PlayerUiState,
    chatMode: ChatMode,
    screenHeight: Dp,
    barAlpha: () -> Float,
    chatDim: () -> Float,
    overlayChatAlpha: () -> Float,
    viewModel: PlayerViewModel,
    chat: ChatLayerSpec,
) {
    val motion = LocalMotion.current
    val density = LocalDensity.current
    val shown = chatMode != ChatMode.Hidden
    // Where the chat sits survives hiding, so it leaves the way it came.
    val lastPlacement = remember { arrayOf(ChatMode.Compact) }
    if (shown) lastPlacement[0] = chatMode
    val placement = lastPlacement[0]
    val overlay = placement == ChatMode.Overlay
    val width = if (overlay) TvDimens.ChatOverlay else chat.columnWidth(placement)
    val widthPx = with(density) { width.toPx() }
    val direction = if (androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl) -1f else 1f

    val away = chat.motion.away
    val column = chat.motion.column
    SideEffect { chat.motion.widthPx = widthPx; chat.motion.overlay = overlay }
    var mounted by remember { mutableStateOf(false) }
    LaunchedEffect(shown) {
        if (shown) {
            if (!mounted) { snapshotFlow(chat.ready).first { it }; mounted = true }
            if (away.value > 0f && motion.enabled) awaitSteadyFrames()
        }
        away.animateTo(if (shown) 0f else 1f, if (shown) motion.glide() else motion.glideOut())
    }
    // Width change (the screen size, or compact <-> over-the-video): the column is laid out at the new width at once and
    // its visible width eases from the old one, so it starts from the old left edge and settles on the new one.
    LaunchedEffect(widthPx, overlay) {
        if (overlay || !shown || column.value == 0f) column.snapTo(widthPx)
        else if (column.value != widthPx) {
            if (motion.enabled) awaitSteadyFrames()
            column.animateTo(widthPx, motion.glide())
        }
    }
    val shift = { if (overlay || column.value == 0f) 0f else widthPx - column.value }

    // Warm: on the chat button, a press is likely, so the (invisible) list catches up now instead of composing a dozen
    // new lines in the frame that starts the slide (a 25-75 ms frame measured on the TV).
    val lines by rememberChatLines(chat.lines, active = shown || chat.warm)
    val style = if (overlay) chat.style.copy(textShadow = true) else chat.style
    val container = if (overlay) {
        Modifier.align(Alignment.BottomEnd).width(width).height(screenHeight * 2 / 3)
            .graphicsLayer {
                val a = away.value
                alpha = (1f - a) * overlayChatAlpha()
                translationY = a * 12.dp.toPx()
            }
    } else {
        Modifier.align(Alignment.TopEnd).width(width).fillMaxHeight()
            .graphicsLayer {
                val a = away.value
                // Towards the end edge: right in left-to-right languages, left in right-to-left ones.
                translationX = (a * widthPx + shift()) * direction
                // Fully away: not drawn at all.
                alpha = if (a >= 1f) 0f else 1f
            }
            .background(TvColors.Body)
            .background(TvColors.Base.copy(alpha = style.backgroundAlpha))
    }
    Box(
        container
            .focusProperties { canFocus = shown && !state.zapperOpen }
            .onFocusChanged { chat.onFocusInside(it.hasFocus) }
            // Hidden but still composed: invisible to accessibility services too.
            .then(if (shown) Modifier else Modifier.clearAndSetSemantics { }),
    ) {
        Column(
            Modifier.fillMaxSize().padding(
                start = if (overlay) 20.dp else TvDimens.ChatInsetStart,
                end = if (overlay) 20.dp else TvDimens.ChatInsetEnd,
                top = if (overlay) 0.dp else TvDimens.SafeTop,
            ),
        ) {
            val room by viewModel.room.collectAsState()
            ChatHeader(
                showTitle = !overlay,
                room = room,
                settingsOpen = state.chatSettingsOpen,
                onOptions = { if (state.chatSettingsOpen) viewModel.closeChatSettings() else viewModel.openChatSettings() },
                focusRequester = chat.headerFocus,
                onLeave = chat.onLeave,
                // Over the video the header is chrome: it shows with the bottom overlay.
                modifier = if (overlay) Modifier.graphicsLayer { alpha = maxOf(barAlpha(), if (state.chatSettingsOpen) 1f else 0f) } else Modifier,
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (mounted) ChatPanel(
                    lines, style, Modifier.fillMaxSize(),
                    focusRequester = chat.panelFocus, onLeave = chat.onLeave,
                    overlay = overlay, active = shown,
                    bottomInset = TvDimens.SafeBottom + 6.dp,
                )
                if (state.chatSettingsOpen) {
                    ChatSettingsPanel(
                        settings = chat.settings,
                        onChange = viewModel::updateChatSettings,
                        modifier = Modifier.fillMaxSize().background(TvColors.Base).padding(bottom = TvDimens.SafeBottom),
                        firstFocus = chat.optionsFocus,
                    )
                }
            }
        }
        if (!overlay) Box(Modifier.matchParentSize().drawBehind { drawRect(TvColors.Base, alpha = chatDim()) })
    }
}

/** Chat title with the room's mode chips, and the options button. Down goes to the messages (or the options when open), Left back to the controls. */
@Composable
private fun ChatHeader(
    showTitle: Boolean,
    room: RoomState,
    settingsOpen: Boolean,
    onOptions: () -> Unit,
    focusRequester: FocusRequester,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth().height(36.dp).padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        // Over the video the title would float far above the messages: only the options button stays there.
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (showTitle) {
                Text(stringResource(R.string.player_chat), style = TvType.Label, color = TvColors.TextSecondary, maxLines = 1)
                if (room.any) RoomChips(room)
            }
        }
        TvIconButton(
            icon = if (settingsOpen) TvIcons.Close else TvIcons.ChatOptions,
            description = stringResource(R.string.player_chat_options),
            onClick = onOptions,
            size = 28.dp,
            iconSize = 16.dp,
            selected = settingsOpen,
            modifier = Modifier.focusRequester(focusRequester).onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) { onLeave(); true } else false
            },
        )
    }
}

/**
 * Over the video's top edge, never focusable.
 * - One pill at a time in the start corner, by priority: an ad break, then what just happened on the channel (the
 *   notice waits out the ad: see [PlayerViewModel.setAdActive]). Changing kind cross-fades; the ad's countdown ticks do not.
 * - The statistics block in the end corner; when the video is too narrow for both (a wide chat column), it sits under the pill.
 */
@Composable
private fun BoxScope.StatusLayers(player: VideoPlayer, showStats: Boolean, notice: ChannelNotice?, videoWidth: Dp) {
    val motion = LocalMotion.current
    val ad by player.adBreak.collectAsState()
    val stats by player.stats.collectAsState()
    // What the pill showed last, so it can fade out with its content instead of an empty box.
    val lastAd = remember { mutableStateOf(AdBreak(0)) }
    ad?.let { lastAd.value = it }
    val lastStats = remember { mutableStateOf<PlaybackStats?>(null) }
    stats?.let { lastStats.value = it }
    val statsOn = showStats && stats != null
    val stacked = videoWidth < NARROW_VIDEO
    val pillMax = if (statsOn && !stacked) (videoWidth - TvDimens.SafeHorizontal * 2 - STATS_WIDTH - 16.dp).coerceAtLeast(200.dp) else (videoWidth - TvDimens.SafeHorizontal * 2)

    @Composable
    fun Pill() {
        val kind: Any? = if (ad != null) AD_KIND else notice
        androidx.compose.animation.AnimatedContent(
            targetState = kind,
            transitionSpec = {
                (fadeIn(motion.medium()) + slideInVertically(motion.medium()) { -it / 3 }) togetherWith
                    (fadeOut(motion.exit()) + slideOutVertically(motion.exit()) { -it / 3 })
            },
            label = "pill",
        ) { shown ->
            when (shown) {
                AD_KIND -> AdChip(ad ?: lastAd.value, Modifier.widthIn(max = pillMax))
                is ChannelNotice -> ChannelBanner(shown, Modifier.widthIn(max = pillMax))
                else -> Unit
            }
        }
    }

    @Composable
    fun Stats() {
        AnimatedVisibility(statsOn, enter = fadeIn(motion.medium()) + slideInVertically(motion.medium()) { -it / 4 }, exit = fadeOut(motion.exit())) {
            lastStats.value?.let { StatsStrip(it) }
        }
    }

    if (stacked) {
        Column(Modifier.align(Alignment.TopStart).padding(start = TvDimens.SafeHorizontal, top = TvDimens.SafeTop), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(); Stats()
        }
    } else {
        Box(Modifier.align(Alignment.TopStart).padding(start = TvDimens.SafeHorizontal, top = TvDimens.SafeTop)) { Pill() }
        Box(Modifier.align(Alignment.TopStart).width(videoWidth).padding(end = TvDimens.SafeHorizontal, top = TvDimens.SafeTop), contentAlignment = Alignment.TopEnd) { Stats() }
    }
}

private const val AD_KIND = "ad"
/** Below this video width the stats block goes under the pill (a large chat column leaves 640 dp). */
private val NARROW_VIDEO = 660.dp

/** The latest messages while [active]; frozen on the last list while hidden, so nothing recomposes until chat shows again. */
// The initial value is deliberately read once, when the state is created: the lines to show before the first collection.
@Suppress("StateFlowValueCalledInComposition")
@Composable
private fun rememberChatLines(flow: StateFlow<ImmutableList<ChatLine>>, active: Boolean): State<ImmutableList<ChatLine>> =
    produceState(flow.value, flow, active) { if (active) flow.collect { value = it } }
