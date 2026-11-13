package com.twitchplayer.phone.feature.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.twitchplayer.phone.ui.theme.LocalReducedMotion
import com.twitchplayer.tv.core.data.model.ChatMode
import com.twitchplayer.tv.core.data.repository.SendResult
import com.twitchplayer.tv.core.data.repository.SessionRepository
import com.twitchplayer.tv.core.data.repository.SessionState
import com.twitchplayer.tv.feature.player.data.chat.ChannelNotice
import com.twitchplayer.tv.feature.player.data.stream.StreamVariant
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
    onOpenChannel: (String) -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
    /** False when the session was brought back after the process was killed: the stream is ready but paused. */
    autoplay: Boolean = true,
    viewModel: PlayerViewModel = koinViewModel(key = "live-$login", parameters = { parametersOf(login) }),
    resolver: TwitchPlaybackResolver = koinInject(),
    session: SessionRepository = koinInject(),
) {
    val presenter = viewModel.presenter
    val state by presenter.state.collectAsStateWithLifecycle()
    val chat by presenter.chat.collectAsStateWithLifecycle()
    val picker by presenter.emotePicker.collectAsStateWithLifecycle()
    val chatSettings by presenter.chatSettings.collectAsStateWithLifecycle()
    val sessionState by session.state.collectAsStateWithLifecycle()
    val preferredQuality by presenter.preferredQuality.collectAsStateWithLifecycle()
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
    // A restored session starts paused, once: this page remembers it did, so nothing that runs again later pauses a stream the viewer has
    // since started. The player is paused at once (no sound before the presenter has the stream), and the presenter is told as soon as it
    // has it, so its Play / Pause button knows the stream is paused.
    val pauseAtStart = remember(login) { booleanArrayOf(!autoplay, !autoplay) }
    DisposableEffect(state.login) {
        if (state.login.isNotEmpty()) {
            player.load(state.login)
            if (pauseAtStart[0]) {
                // The player's own "paused by the viewer" (set after load, which clears it): the stream resolves and waits for the play button.
                player.pause()
                pauseAtStart[0] = false
            }
        }
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

    DisposableEffect(state.current != null) {
        if (state.current != null && pauseAtStart[1]) {
            pauseAtStart[1] = false
            presenter.onEvent(PlayerEvent.TogglePause)
        }
        onDispose { }
    }

    val snackbar = remember { SnackbarHostState() }
    // What survives the process being killed in the background: the chat mode chosen for this session and the fullscreen lock.
    val restoration = rememberPlayerRestoration(login)
    val setChatMode: (ChatMode) -> Unit = { mode -> restoration.choose(mode) { presenter.onEvent(PlayerEvent.SetChatMode(it)) } }
    DisposableEffect(Unit) {
        restoration.toRestore()?.let { presenter.onEvent(PlayerEvent.SetChatMode(it)) }
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
    val sleep = rememberSleepTimer()
    var sleepOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    // When the timer runs out the stream pauses, like pressing pause (the presenter keeps the state in step).
    SleepTimerEffect(sleep) { if (snapshot.playWhenReady) presenter.onEvent(PlayerEvent.TogglePause) }
    val failed = status is NativeStatus.Failed
    val signedIn = sessionState is SessionState.SignedIn
    val selfLogin = (sessionState as? SessionState.SignedIn)?.account?.login
    // The line being answered; a different channel or leaving the page drops it with the page's state.
    var replyTo by remember(login) { mutableStateOf<com.twitchplayer.tv.feature.player.presentation.ChatLine?>(null) }
    val send: (String) -> Unit = { text ->
        presenter.onEvent(PlayerEvent.SendChat(text, replyTo?.id))
        replyTo = null
    }
    val style = remember(chatSettings) { chatSettings.toPhoneStyle() }
    val animateEmotes = style.animatedEmotes && !LocalReducedMotion.current
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
            pinned = qualities != null || sleepOpen || settingsOpen,
            video = { videoModifier -> NativeVideoSurface(player, videoModifier) },
            stageOverlay = {
                // Audio only: no picture comes, so the stage says so (and whose stream it is) instead of staying black.
                if (preferredQuality == StreamVariant.AUDIO_ONLY && !failed && !snapshot.hasPicture) {
                    AudioOnlyStage(name = title, avatarUrl = current?.avatarUrl, modifier = Modifier.align(Alignment.Center))
                }
            },
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
                SleepTimerChip(sleep, onClick = {
                    chrome.controls.touch()
                    sleepOpen = true
                })
                if (host.canPictureInPicture) {
                    ControlButton(PhoneIcons.PictureInPicture, stringResource(R.string.phone_player_pip), { host.enterPictureInPicture() })
                }
                ControlButton(
                    PhoneIcons.Chat,
                    stringResource(R.string.phone_player_chat_toggle),
                    {
                        chrome.controls.touch()
                        setChatMode(nextChatMode(state.chatMode, chrome.landscape))
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
                        Icons.Filled.Settings,
                        stringResource(R.string.phone_player_settings),
                        {
                            chrome.controls.touch()
                            settingsOpen = true
                        },
                    )
                    FullscreenButton(chrome)
                }
            },
            landscapeSide = { chrome ->
                if (layoutChatMode(state.chatMode, true) == ChatMode.Compact) {
                    Column(Modifier.width(SIDE_PANEL).fillMaxHeight().background(MaterialTheme.colorScheme.surface).safeDrawingPadding()) {
                        ChatArea(chat, style, signedIn, onSignIn, Modifier.weight(1f), onReply = { replyTo = it })
                        if (signedIn) {
                            ChatComposer(send, picker, animateEmotes, { presenter.onEvent(PlayerEvent.PickedEmote(it)) }, handleInsets = false, replyTo = replyTo, onClearReply = { replyTo = null }, lines = chat, selfLogin = selfLogin)
                        }
                    }
                }
            },
            portraitContent = {
                Column(Modifier.weight(1f).fillMaxWidth()) {
                    StreamInfo(
                        avatarUrl = current?.avatarUrl,
                        name = title,
                        facts = StreamFacts(
                            title = current?.title,
                            viewers = current?.viewers ?: 0,
                            category = current?.categoryName,
                            startedAtMs = current?.startedAtMs ?: 0L,
                            labels = listOfNotNull(current?.language?.takeIf { it.isNotBlank() }?.uppercase()) + current?.tags.orEmpty(),
                        ),
                        notice = state.notice,
                        onOpenChannel = { onOpenChannel(state.login) },
                        onOpenTwitch = { channelUrl(state.login).takeIf(::isTwitchUrl)?.let { runCatching { uriHandler.openUri(it) } } },
                    )
                    HorizontalDivider()
                    if (layoutChatMode(state.chatMode, false) == ChatMode.Hidden) {
                        MessageState(
                            title = stringResource(R.string.chat_hidden),
                            modifier = Modifier.weight(1f),
                            actionLabel = stringResource(R.string.phone_player_show_chat),
                            onAction = { setChatMode(ChatMode.Compact) },
                        )
                    } else {
                        ChatArea(chat, style, signedIn, onSignIn, Modifier.weight(1f), onReply = { replyTo = it })
                        if (signedIn) {
                            ChatComposer(send, picker, animateEmotes, { presenter.onEvent(PlayerEvent.PickedEmote(it)) }, replyTo = replyTo, onClearReply = { replyTo = null }, lines = chat, selfLogin = selfLogin)
                        }
                    }
                }
            },
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).safeDrawingPadding())
        if (settingsOpen) {
            val off = stringResource(R.string.phone_player_settings_off)
            // What the viewer asked for: Auto, or the rendition they picked.
            val qualityValue = if (preferredQuality.isNullOrBlank() || preferredQuality.equals("auto", ignoreCase = true)) stringResource(R.string.quality_auto) else preferredQuality
            PlayerSettingsSheet(
                rows = listOf(
                    PlayerSettingRow(PhoneIcons.Quality, stringResource(R.string.player_quality), qualityValue) {
                        settingsOpen = false
                        qualities = VideoQualities.None
                        player.loadQualities { qualities = it }
                    },
                    PlayerSettingRow(PhoneIcons.Timer, stringResource(R.string.phone_player_sleep_timer), if (sleep.active) SleepTimerPolicy.format(sleep.remainingMs) else off) {
                        settingsOpen = false
                        sleepOpen = true
                    },
                ),
                onDismiss = { settingsOpen = false },
            )
        }
        if (sleepOpen) SleepTimerSheet(sleep, onDismiss = { sleepOpen = false })
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
    onReply: ((com.twitchplayer.tv.feature.player.presentation.ChatLine) -> Unit)? = null,
) {
    if (signedIn) {
        PhoneChatList(chat, style, overVideo = false, modifier = modifier, onReply = onReply)
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

/** What the page says about the stream under the picture: its title, category, viewers, start time and the language and tags the broadcaster set. */
@androidx.compose.runtime.Immutable
private class StreamFacts(val title: String?, val viewers: Int, val category: String?, val startedAtMs: Long, val labels: List<String>)

/** Title, category, viewers and uptime under the picture, plus raids / stream-ended notices, and a button to open the channel on twitch.tv. */
@Composable
private fun StreamInfo(avatarUrl: String?, name: String, facts: StreamFacts, notice: ChannelNotice?, onOpenChannel: () -> Unit, onOpenTwitch: () -> Unit) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(UPTIME_TICK_MS)
            now = System.currentTimeMillis()
        }
    }
    val uptime = liveForLabel(facts.startedAtMs, now)
    val twitchDescription = stringResource(R.string.phone_player_open_twitch)
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // What is being watched, always on the page (the controls over the picture hide): the stream's title, up to two lines.
        if (!facts.title.isNullOrBlank()) {
            Text(facts.title, style = MaterialTheme.typography.titleMedium.copy(fontSize = STREAM_TITLE_SP.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, textDirection = androidx.compose.ui.text.style.TextDirection.Content), maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // The streamer (picture and name) leads to the channel page; the button at the end opens twitch.tv.
            Row(
                Modifier.weight(1f).clickable(onClickLabel = stringResource(R.string.phone_player_open_channel), role = Role.Button, onClick = onOpenChannel),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Avatar(avatarUrl, name, 40.dp)
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    val watching = if (facts.viewers > 0) stringResource(R.string.phone_search_viewers, remember(facts.viewers) { com.twitchplayer.tv.core.ui.formatViewers(facts.viewers) }) else null
                    val line = listOfNotNull(facts.category?.ifBlank { null }, watching, uptime?.let { stringResource(R.string.phone_live_for, it) }).joinToString(" · ")
                    if (line.isNotEmpty()) {
                        Text(line, style = MaterialTheme.typography.bodySmall.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Content), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                }
            }
            androidx.compose.material3.TextButton(onClick = onOpenTwitch, modifier = Modifier.semantics { contentDescription = twitchDescription }) {
                Icon(PhoneIcons.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.phone_player_twitch), modifier = Modifier.padding(start = 6.dp), maxLines = 1)
            }
        }
        StreamTags(facts.labels)
        NoticeBanner(notice)
    }
}

/** The language and the tags the broadcaster set, as small pills in one scrolling row; nothing when there are none. */
@Composable
private fun StreamTags(labels: List<String>) {
    if (labels.isEmpty()) return
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(labels.size) { index ->
            Text(
                labels[index],
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.clip(androidx.compose.foundation.shape.CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
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
private const val RETRY_BORDER_ALPHA = 0.7f
private const val OVERLAY_WIDTH = 0.34f
private const val OVERLAY_HEIGHT = 0.5f
private const val UPTIME_TICK_MS = 30_000L
private const val STREAM_TITLE_SP = 18
