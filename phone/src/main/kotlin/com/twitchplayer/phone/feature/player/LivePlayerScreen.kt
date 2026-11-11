package com.twitchplayer.phone.feature.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twitchplayer.phone.R
import com.twitchplayer.phone.feature.auth.channelUrl
import com.twitchplayer.phone.feature.auth.isTwitchUrl
import com.twitchplayer.phone.feature.channel.liveForLabel
import com.twitchplayer.phone.ui.components.Avatar
import com.twitchplayer.phone.ui.components.LiveBadge
import com.twitchplayer.phone.ui.components.MessageState
import com.twitchplayer.phone.ui.components.PhoneIcons
import com.twitchplayer.phone.ui.components.ViewersBadge
import com.twitchplayer.tv.core.data.model.ChatMode
import com.twitchplayer.tv.core.data.repository.SendResult
import com.twitchplayer.tv.core.data.repository.SessionRepository
import com.twitchplayer.tv.core.data.repository.SessionState
import com.twitchplayer.tv.feature.player.data.chat.ChannelNotice
import com.twitchplayer.tv.feature.player.data.stream.TwitchPlaybackResolver
import com.twitchplayer.tv.feature.player.playback.NativeStatus
import com.twitchplayer.tv.feature.player.playback.NativeVideoSurface
import com.twitchplayer.tv.feature.player.playback.QualityLabels
import com.twitchplayer.tv.feature.player.playback.VideoQualities
import com.twitchplayer.tv.feature.player.playback.rememberNativeVideoPlayer
import com.twitchplayer.tv.feature.player.presentation.PlayerCommand
import com.twitchplayer.tv.feature.player.presentation.PlayerEvent
import com.twitchplayer.tv.feature.player.presentation.PlayerViewModel
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * A live channel: native Media3 playback (the phone has no WebView embed), chat from the shared presenter, quality sheet,
 * fullscreen, Picture-in-Picture. The presenter lives in this page's ViewModel, so a rotation keeps the session; leaving the page
 * ends it (and chat disconnects with the ViewModel).
 */
@Composable
fun LivePlayerRoute(
    login: String,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = koinViewModel(key = "live-$login", parameters = { parametersOf(login) }),
    resolver: TwitchPlaybackResolver = koinInject(),
    session: SessionRepository = koinInject(),
) {
    val presenter = viewModel.presenter
    val state by presenter.state.collectAsStateWithLifecycle()
    val chat by presenter.chat.collectAsStateWithLifecycle()
    val chatSettings by presenter.chatSettings.collectAsStateWithLifecycle()
    val sessionState by session.state.collectAsStateWithLifecycle()
    val host = LocalPlayerHost.current
    val inPip by host.inPictureInPicture.collectAsStateWithLifecycle()

    val labels = QualityLabels(stringResource(R.string.quality_auto), stringResource(R.string.quality_audio_only))
    val currentLabels by rememberUpdatedState(labels)
    val player = rememberNativeVideoPlayer(
        resolver = resolver,
        preferredQuality = { presenter.preferredQuality.value },
        onQualityPicked = { presenter.onEvent(PlayerEvent.SetPreferredQuality(it)) },
        qualityLabels = { currentLabels },
    )
    val snapshot = rememberPlayerSnapshot(player.exoPlayer)
    val status by player.status.collectAsStateWithLifecycle()

    DisposableEffect(login) {
        presenter.onEvent(PlayerEvent.Attach(login))
        onDispose { }
    }
    DisposableEffect(state.login) {
        if (state.login.isNotEmpty()) player.load(state.login)
        onDispose { }
    }
    LaunchedEffect(presenter, player) {
        presenter.commands.collect { command ->
            when (command) {
                PlayerCommand.Play -> player.play()
                PlayerCommand.Pause -> player.pause()
                PlayerCommand.ToggleMute -> player.toggleMute()
            }
        }
    }

    val snackbar = remember { SnackbarHostState() }
    // What survives the process being killed in the background: the chat mode chosen for this session and the fullscreen lock.
    val restoration = rememberPlayerRestoration(login)
    DisposableEffect(Unit) {
        restoredChatMode(restoration.chatMode)?.takeIf { it != state.chatMode }?.let { presenter.onEvent(PlayerEvent.SetChatMode(it)) }
        onDispose { }
    }
    DisposableEffect(state.chatMode) {
        restoration.chatMode = state.chatMode.name
        onDispose { }
    }
    val needsSignIn = stringResource(R.string.chat_needs_signin)
    val failedText = stringResource(R.string.chat_send_failed)
    val droppedText = stringResource(R.string.chat_dropped)
    LaunchedEffect(presenter) {
        presenter.sent.collect { result ->
            when (result) {
                SendResult.Sent -> Unit
                is SendResult.Dropped -> snackbar.showSnackbar(droppedText)
                SendResult.NeedsSignIn -> snackbar.showSnackbar(needsSignIn)
                SendResult.Failed -> snackbar.showSnackbar(failedText)
            }
        }
    }

    var qualities by remember { mutableStateOf<VideoQualities?>(null) }
    val failed = status is NativeStatus.Failed
    val signedIn = sessionState is SessionState.SignedIn
    val style = remember(chatSettings) { chatSettings.toPhoneStyle() }
    val current = state.current
    val title = current?.name?.ifBlank { null } ?: state.login
    val uriHandler = LocalUriHandler.current

    Box(modifier.fillMaxSize()) {
        PlayerScaffold(
            snapshot = snapshot,
            failed = failed,
            title = title,
            subtitle = current?.title?.ifBlank { null },
            onBack = onBack,
            onTogglePlay = { presenter.onEvent(PlayerEvent.TogglePause) },
            restoration = restoration,
            pinned = qualities != null,
            video = { videoModifier -> NativeVideoSurface(player, videoModifier) },
            stageTop = { chrome ->
                // Above the control layer: undimmed (the scrim would grey the last lines) and tappable (Retry).
                StageStatus(status = status, hasPicture = snapshot.hasPicture, onRetry = { player.load(state.login) })
                // Chat laid over the video (landscape only): the viewer's background opacity behind the lines, above the bottom bar.
                if (overlayChatVisible(state.chatMode, chrome.landscape, signedIn, inPip)) {
                    val lift by animateDpAsState(if (chrome.controls.visible) CONTROLS_BAR else 0.dp, label = "chat-lift")
                    Box(
                        Modifier.align(Alignment.BottomStart).safeDrawingPadding().padding(bottom = lift).fillMaxWidth(OVERLAY_WIDTH).fillMaxHeight(OVERLAY_HEIGHT)
                            .clip(RoundedCornerShape(8.dp)).background(overVideoPanelColor(style)),
                    ) { PhoneChatList(chat, style, overVideo = true, animationsAllowed = !inPip) }
                }
            },
            topActions = { chrome ->
                if (host.canPictureInPicture) {
                    ControlButton(PhoneIcons.PictureInPicture, stringResource(R.string.phone_player_pip), { host.enterPictureInPicture() })
                }
                ControlButton(
                    PhoneIcons.Chat,
                    stringResource(R.string.phone_player_chat_toggle),
                    {
                        chrome.controls.touch()
                        presenter.onEvent(PlayerEvent.SetChatMode(nextChatMode(state.chatMode, chrome.landscape)))
                    },
                )
            },
            center = {
                // When the stream cannot play, the retry button sits in the middle of the picture: nothing may cover it.
                if (!failed) {
                    CenterPlayButton(playing = snapshot.playWhenReady, buffering = snapshot.buffering || status is NativeStatus.Resolving, onClick = {
                        presenter.onEvent(PlayerEvent.TogglePause)
                    })
                }
            },
            bottom = { chrome ->
                Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    // A stream that cannot play is not shown as live.
                    if (!failed) {
                        LiveBadge()
                        Spacer(Modifier.width(BADGE_GAP))
                        current?.let { ViewersBadge(it.viewers) }
                    }
                    Box(Modifier.weight(1f))
                    ControlButton(
                        if (state.muted) PhoneIcons.VolumeOff else PhoneIcons.VolumeUp,
                        stringResource(if (state.muted) R.string.player_muted else R.string.player_sound),
                        {
                            chrome.controls.touch()
                            presenter.onEvent(PlayerEvent.ToggleMute)
                        },
                    )
                    ControlButton(
                        PhoneIcons.Quality,
                        stringResource(R.string.player_quality),
                        {
                            chrome.controls.touch()
                            qualities = VideoQualities.None
                            player.loadQualities { qualities = it }
                        },
                    )
                    FullscreenButton(chrome)
                }
            },
            landscapeSide = { chrome ->
                if (layoutChatMode(state.chatMode, true) == ChatMode.Compact) {
                    Column(Modifier.width(SIDE_PANEL).fillMaxHeight().background(MaterialTheme.colorScheme.surface).safeDrawingPadding()) {
                        ChatArea(chat, style, signedIn, onSignIn, Modifier.weight(1f))
                        if (signedIn) ChatInputRow({ presenter.onEvent(PlayerEvent.SendChat(it)) })
                    }
                }
            },
            portraitContent = {
                Column(Modifier.weight(1f).fillMaxWidth()) {
                    StreamInfo(current?.avatarUrl, title, current?.categoryName, current?.startedAtMs ?: 0L, state.notice) {
                        channelUrl(state.login).takeIf(::isTwitchUrl)?.let { runCatching { uriHandler.openUri(it) } }
                    }
                    HorizontalDivider()
                    if (layoutChatMode(state.chatMode, false) == ChatMode.Hidden) {
                        MessageState(
                            title = stringResource(R.string.chat_hidden),
                            modifier = Modifier.weight(1f),
                            actionLabel = stringResource(R.string.phone_player_show_chat),
                            onAction = { presenter.onEvent(PlayerEvent.SetChatMode(ChatMode.Compact)) },
                        )
                    } else {
                        ChatArea(chat, style, signedIn, onSignIn, Modifier.weight(1f))
                        if (signedIn) {
                            ChatInputRow({ presenter.onEvent(PlayerEvent.SendChat(it)) }, Modifier.navigationBarsPadding().imePadding())
                        }
                    }
                }
            },
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).safeDrawingPadding())
        qualities?.let { options ->
            QualitySheet(
                qualities = options,
                onPick = {
                    player.setQuality(it)
                    qualities = null
                },
                onDismiss = { qualities = null },
            )
        }
    }
}

/** Chat messages when signed in, otherwise the prompt to sign in. */
@Composable
private fun ChatArea(
    chat: kotlinx.collections.immutable.ImmutableList<com.twitchplayer.tv.feature.player.presentation.ChatLine>,
    style: PhoneChatStyle,
    signedIn: Boolean,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (signedIn) {
        PhoneChatList(chat, style, overVideo = false, modifier = modifier)
    } else {
        ChatSignInPrompt(onSignIn, modifier)
    }
}

/** Spinner while the stream resolves, the error or offline message when it cannot play. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.StageStatus(status: NativeStatus, hasPicture: Boolean, onRetry: () -> Unit) {
    when {
        status is NativeStatus.Failed -> {
            val offline = status.offline
            Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(if (offline) R.string.phone_channel_offline else R.string.state_error),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
                androidx.compose.material3.OutlinedButton(
                    onClick = onRetry,
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = RETRY_BORDER_ALPHA)),
                ) { Text(stringResource(R.string.state_retry)) }
            }
        }

        !hasPicture && status is NativeStatus.Resolving -> CircularProgressIndicator(Modifier.align(Alignment.Center).size(40.dp), color = Color.White)
    }
}

/** Title, category, viewers and uptime under the picture, plus raids / stream-ended notices, and a button to open the channel on twitch.tv. */
@Composable
private fun StreamInfo(avatarUrl: String?, name: String, category: String?, startedAtMs: Long, notice: ChannelNotice?, onOpenTwitch: () -> Unit) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(UPTIME_TICK_MS)
            now = System.currentTimeMillis()
        }
    }
    val uptime = liveForLabel(startedAtMs, now)
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(avatarUrl, name, 40.dp)
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                val line = listOfNotNull(category?.ifBlank { null }, uptime?.let { stringResource(R.string.phone_live_for, it) }).joinToString(" · ")
                if (line.isNotEmpty()) {
                    Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
            ControlButton(
                PhoneIcons.OpenInNew,
                stringResource(R.string.phone_player_open_twitch),
                onOpenTwitch,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        NoticeBanner(notice)
    }
}

@Composable
private fun NoticeBanner(notice: ChannelNotice?) {
    val text = when (notice) {
        is ChannelNotice.Raid -> stringResource(R.string.banner_raid, notice.toName)
        ChannelNotice.Offline -> stringResource(R.string.banner_offline)
        else -> null
    } ?: return
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(8.dp)) {
        Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}

private val SIDE_PANEL = 320.dp
private val CONTROLS_BAR = 56.dp
private val BADGE_GAP = 8.dp
private const val RETRY_BORDER_ALPHA = 0.7f
private const val OVERLAY_WIDTH = 0.34f
private const val OVERLAY_HEIGHT = 0.5f
private const val UPTIME_TICK_MS = 30_000L
