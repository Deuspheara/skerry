package com.twitchplayer.phone.feature.discover

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.twitchplayer.phone.R
import com.twitchplayer.phone.feature.player.PhoneChatList
import com.twitchplayer.phone.feature.player.overVideoPanelColor
import com.twitchplayer.phone.feature.player.rememberPlayerSnapshot
import com.twitchplayer.phone.feature.player.toPhoneStyle
import com.twitchplayer.phone.ui.components.Avatar
import com.twitchplayer.phone.ui.components.LoadHost
import com.twitchplayer.phone.ui.components.MessageState
import com.twitchplayer.phone.ui.components.PhoneIcons
import com.twitchplayer.phone.ui.components.RemoteImage
import com.twitchplayer.phone.ui.components.SignedOutCopy
import com.twitchplayer.phone.ui.components.THUMBNAIL_RATIO
import com.twitchplayer.phone.ui.components.ViewersBadge
import com.twitchplayer.tv.core.data.model.LiveStream
import com.twitchplayer.tv.core.presentation.Load
import com.twitchplayer.tv.core.ui.formatViewers
import com.twitchplayer.tv.feature.feed.presentation.FeedChatViewModel
import com.twitchplayer.tv.feature.feed.presentation.FeedEvent
import com.twitchplayer.tv.feature.feed.presentation.FeedMerge
import com.twitchplayer.tv.feature.feed.presentation.FeedUiState
import com.twitchplayer.tv.feature.feed.presentation.FeedViewModel
import com.twitchplayer.tv.feature.player.data.stream.TwitchPlaybackResolver
import com.twitchplayer.tv.feature.player.playback.NativeStatus
import com.twitchplayer.tv.feature.player.playback.NativeVideoPlayer
import com.twitchplayer.tv.feature.player.playback.NativeVideoSurface
import com.twitchplayer.tv.feature.player.playback.QualityLabels
import com.twitchplayer.tv.feature.player.playback.rememberNativeVideoPlayer
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The Discover tab: one live stream per screen, swiped vertically (see docs/discover-feed.md). The picture of the page the viewer rests on
 * plays muted; the next page's player is already loading it, paused. Only these two players exist (parity of the page index picks one), so a
 * long swipe session never holds more than two decoders. Chat connects only to the page the viewer stayed on for a moment and disconnects
 * as they leave it. A tap opens the full player with chat; a long press pauses or resumes the preview.
 */
@Composable
fun DiscoverRoute(
    onOpen: (String) -> Unit,
    onOpenClip: (slug: String, title: String) -> Unit,
    onChannel: (String) -> Unit,
    onSignIn: () -> Unit,
    playerOpen: Boolean,
    modifier: Modifier = Modifier,
    viewModel: FeedViewModel = koinViewModel(),
    resolver: TwitchPlaybackResolver = koinInject(),
) {
    val state by viewModel.presenter.state.collectAsStateWithLifecycle()
    var clips by rememberSaveable { mutableStateOf(false) }
    // The page is black whatever the theme: its status bar icons are light (like the player's), or the clock vanishes in the light theme.
    val systemBars = com.twitchplayer.phone.feature.player.LocalPlayerHost.current
    DisposableEffect(playerOpen) {
        if (!playerOpen) systemBars.setVideoSystemBars(true)
        onDispose { if (!playerOpen) systemBars.setVideoSystemBars(false) }
    }
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (clips) {
            ClipsRoute(onOpenClip, onChannel, onSignIn, playerOpen, onGoLive = { clips = false })
        } else {
            LiveFeed(state, resolver, playerOpen, onOpen, onChannel, onSignIn, onRetry = { viewModel.presenter.onEvent(FeedEvent.Retry) }, onSettle = { viewModel.presenter.onEvent(FeedEvent.Settled(it)) })
        }
        // Live and Clips only mean something to a viewer who can see them: the signed-out page offers a sign-in, not two tabs.
        if (state.load !is Load.SignedOut) {
            ModeSwitch(clips, onChange = { clips = it }, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp))
        }
    }
}

/** Live | Clips: two words over the picture, the chosen one underlined. */
@Composable
private fun ModeSwitch(clips: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        ModeWord(stringResource(R.string.phone_feed_mode_live), selected = !clips) { onChange(false) }
        ModeWord(stringResource(R.string.phone_feed_mode_clips), selected = clips) { onChange(true) }
    }
}

@Composable
private fun ModeWord(text: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.minimumInteractiveComponentSize().selectable(selected = selected, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text,
            color = Color.White.copy(alpha = if (selected) 1f else INFO_ALPHA),
            style = MaterialTheme.typography.titleSmall,
            // A shadow keeps the words readable on any picture.
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        )
        Box(Modifier.height(2.dp).width(24.dp).background(if (selected) Color.White else Color.Transparent))
    }
}

@Composable
private fun LiveFeed(
    state: FeedUiState,
    resolver: TwitchPlaybackResolver,
    playerOpen: Boolean,
    onOpen: (String) -> Unit,
    onChannel: (String) -> Unit,
    onSignIn: () -> Unit,
    onRetry: () -> Unit,
    onSettle: (String) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        LoadHost(
            state.load,
            onRetry = onRetry,
            onSignIn = onSignIn,
            skeleton = { Box(Modifier.fillMaxSize().background(Color.Black)) },
            staleMarker = false,
            signedOut = SignedOutCopy(stringResource(R.string.phone_signedout_discover_title), stringResource(R.string.phone_signedout_discover_body)),
        ) { content ->
            if (content.streams.isEmpty()) {
                MessageState(
                    title = stringResource(R.string.phone_feed_empty),
                    actionLabel = stringResource(R.string.state_retry),
                    onAction = onRetry,
                    icon = PhoneIcons.Discover,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                FeedPager(
                    streams = content.streams,
                    autoplayAllowed = state.autoplayAllowed,
                    playerOpen = playerOpen,
                    resolver = resolver,
                    onSettle = onSettle,
                    onOpen = onOpen,
                    onChannel = onChannel,
                )
            }
        }
    }
}

@Composable
private fun FeedPager(
    streams: ImmutableList<LiveStream>,
    autoplayAllowed: Boolean,
    playerOpen: Boolean,
    resolver: TwitchPlaybackResolver,
    onSettle: (String) -> Unit,
    onOpen: (String) -> Unit,
    onChannel: (String) -> Unit,
) {
    // The list is frozen on the first composition: a refresh only changes what is AHEAD of the page the viewer rests on (see FeedMerge), so
    // nothing moves, reorders or disappears under them, whatever the refresh ranked first.
    var shown by remember { mutableStateOf(streams) }
    val pagerState = rememberPagerState { shown.size }
    val settled = pagerState.settledPage
    DisposableEffect(streams) {
        shown = FeedMerge.merge(shown, streams, pagerState.settledPage) { it.channelId }
        onDispose { }
    }

    var muted by rememberSaveable { mutableStateOf(true) }
    var chatOn by rememberSaveable { mutableStateOf(true) }
    // Streams the viewer asked to play while autoplay is off.
    var asked by remember { mutableStateOf(emptySet<String>()) }
    var dwelled by remember { mutableStateOf<String?>(null) }

    val labels = QualityLabels(stringResource(R.string.quality_auto), stringResource(R.string.quality_audio_only))
    val currentLabels by rememberUpdatedState(labels)
    // handleAudioFocus = false: a muted preview must not pause the viewer's music (focus is taken when the sound is turned on, see FeedAudio).
    val players = listOf(
        rememberNativeVideoPlayer(resolver, preferredQuality = { PREVIEW_QUALITY }, onQualityPicked = {}, qualityLabels = { currentLabels }, handleAudioFocus = false),
        rememberNativeVideoPlayer(resolver, preferredQuality = { PREVIEW_QUALITY }, onQualityPicked = {}, qualityLabels = { currentLabels }, handleAudioFocus = false),
    )
    val currentOnSettle by rememberUpdatedState(onSettle)
    LaunchedEffect(settled, shown, autoplayAllowed, playerOpen, muted, asked) {
        val stream = shown.getOrNull(settled) ?: return@LaunchedEffect
        val current = players[settled % PLAYERS]
        val next = players[(settled + 1) % PLAYERS]
        current.exoPlayer.volume = if (muted) 0f else 1f
        next.exoPlayer.volume = 0f
        current.setAudioFocus(FeedAudio.takesFocus(muted, playerOpen))
        next.setAudioFocus(false)
        if (playerOpen) {
            // The full player is on top: it plays, the previews stop (sound, data and a decoder less).
            current.pause()
            next.pause()
            return@LaunchedEffect
        }
        current.load(stream.login)
        if (autoplayAllowed || stream.login in asked) current.play() else current.pause()
        val upcoming = shown.getOrNull(settled + 1)
        if (upcoming != null && autoplayAllowed) {
            next.load(upcoming.login)
            next.pause()
        } else {
            next.pause()
        }
        delay(DWELL_MS)
        dwelled = stream.login
        currentOnSettle(stream.login)
    }
    // Leaving the page the chat belongs to disconnects it at once.
    DisposableEffect(settled) {
        dwelled = null
        onDispose { }
    }

    // One page per fling: a long fling would otherwise compose and decode pages that go by in a blink.
    val fling = PagerDefaults.flingBehavior(state = pagerState, pagerSnapDistance = PagerSnapDistance.atMost(1))
    VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize().background(Color.Black), beyondViewportPageCount = 0, flingBehavior = fling, key = { shown[it].channelId }) { page ->
        val stream = shown[page]
        FeedPage(
            stream = stream,
            index = page,
            count = shown.size,
            player = players[page % PLAYERS],
            isSettled = page == settled,
            view = FeedView(
                muted = muted,
                chatOn = chatOn,
                showChatFor = if (dwelled == stream.login && !playerOpen && page == settled) stream.channelId else null,
                tapToPlay = !autoplayAllowed && stream.login !in asked,
            ),
            actions = FeedActions(
                onToggleMute = { muted = !muted },
                onToggleChat = { chatOn = !chatOn },
                onPlayHere = { asked = asked + stream.login },
                onOpen = { onOpen(stream.login) },
                onChannel = { onChannel(stream.login) },
            ),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FeedPage(
    stream: LiveStream,
    index: Int,
    count: Int,
    player: NativeVideoPlayer,
    isSettled: Boolean,
    view: FeedView,
    actions: FeedActions,
) {
    val muted = view.muted
    val chatOn = view.chatOn
    val showChatFor = view.showChatFor
    val tapToPlay = view.tapToPlay
    val onToggleMute = actions.onToggleMute
    val onToggleChat = actions.onToggleChat
    val onPlayHere = actions.onPlayHere
    val onOpen = actions.onOpen
    val onChannel = actions.onChannel
    val loaded by player.loaded.collectAsStateWithLifecycle()
    val status by player.status.collectAsStateWithLifecycle()
    val mine = loaded.equals(stream.login, ignoreCase = true)
    val showVideo = mine && status is NativeStatus.Playing
    val viewers = remember(stream.viewers) { formatViewers(stream.viewers) }
    val description = stringResource(R.string.phone_feed_page_description, stream.name, stream.categoryName, viewers, index + 1, count)
    val source = remember { MutableInteractionSource() }
    val openLabel = stringResource(R.string.phone_feed_open)
    val snapshot = rememberPlayerSnapshot(player.exoPlayer)
    val togglePlayLabel = stringResource(if (snapshot.playWhenReady) R.string.player_pause else R.string.player_play)
    val togglePlay = { if (player.exoPlayer.isPlaying) player.pause() else player.play() }
    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .semantics(mergeDescendants = false) {
                contentDescription = description
                // The gestures of the page, as actions a screen reader can list: the long press is not discoverable without them.
                customActions = listOf(
                    CustomAccessibilityAction(togglePlayLabel) {
                        togglePlay()
                        true
                    },
                    CustomAccessibilityAction(openLabel) {
                        onOpen()
                        true
                    },
                )
            }
            .combinedClickable(interactionSource = source, indication = null, onClickLabel = openLabel, onClick = onOpen, onLongClickLabel = togglePlayLabel, onLongClick = togglePlay),
    ) {
        // The stream's own picture, darkened, fills the page behind the card so there is never an empty black screen around a 16:9 video.
        RemoteImage(stream.thumbnailUrl, Modifier.fillMaxSize().alpha(BACKDROP_ALPHA))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.35f), BACKDROP_FADE to Color.Black)))
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = MODE_SWITCH_SPACE)) {
            // The block (card, facts, actions) sits a little above the middle of the page and the chat fills what is under it: no black void.
            Spacer(Modifier.weight(CARD_TOP_WEIGHT))
            // The card: the video (its surface, a PlayerView, is only built for the page the viewer rests on, never while it slides in: the swipe
            // itself draws a picture and text, and the video appears as soon as the page settles, its player prepared since the last page).
            Box(Modifier.padding(horizontal = 12.dp).fillMaxWidth().aspectRatio(THUMBNAIL_RATIO).clip(RoundedCornerShape(12.dp)).background(Color.Black)) {
                if (mine && isSettled) NativeVideoSurface(player, Modifier.fillMaxSize())
                // The picture stands in until the stream really plays (and stays when autoplay is off or the channel went offline).
                if (!showVideo) RemoteImage(stream.thumbnailUrl, Modifier.fillMaxSize())
                if (tapToPlay && isSettled) {
                    Text(
                        stringResource(R.string.phone_feed_tap_to_play),
                        modifier = Modifier.align(Alignment.Center).clip(CircleShape).background(Color.Black.copy(alpha = SCRIM_ALPHA)).clickable(onClick = onPlayHere).padding(horizontal = 20.dp, vertical = 12.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
            Column(Modifier.padding(horizontal = 16.dp).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Avatar(stream.avatarUrl, stream.name, AVATAR, Modifier.clickable(onClick = onChannel))
                    Column(Modifier.weight(1f)) {
                        Text(stream.name, color = Color.White, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(stream.categoryName, color = Color.White.copy(alpha = INFO_ALPHA), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    ViewersBadge(stream.viewers)
                }
                Text(stream.title, color = Color.White, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                // The actions, each with its word under the icon: a rail of bare icons did not say what a button does.
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    FeedAction(
                        if (muted) PhoneIcons.VolumeOff else PhoneIcons.VolumeUp,
                        stringResource(if (muted) R.string.phone_feed_label_sound_off else R.string.phone_feed_label_sound_on),
                        stringResource(if (muted) R.string.phone_feed_mute else R.string.phone_feed_unmute),
                        onToggleMute,
                        Modifier.weight(1f),
                    )
                    FeedAction(PhoneIcons.Chat, stringResource(R.string.phone_feed_label_chat), stringResource(if (chatOn) R.string.phone_feed_chat_hide else R.string.phone_feed_chat_show), onToggleChat, Modifier.weight(1f))
                    FeedAction(Icons.Filled.Person, stringResource(R.string.phone_feed_label_channel), stringResource(R.string.phone_feed_channel), onChannel, Modifier.weight(1f))
                    FeedAction(PhoneIcons.Discover, stringResource(R.string.phone_feed_label_watch), stringResource(R.string.phone_feed_open), onOpen, Modifier.weight(1f))
                }
            }
            // The chat fills what is left under the card; nothing when it is off or the viewer has not stayed on the page yet.
            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp).padding(top = 8.dp, bottom = 8.dp)) {
                if (chatOn && showChatFor != null) FeedChat(showChatFor, Modifier.fillMaxSize())
            }
            if (index < count - 1) {
                PagerHint(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 12.dp))
            }
        }
    }
}

/** An action under the card: a 40 dp disc with the icon and a 12 sp word below, one 48 dp-wide-and-tall target. */
@Composable
internal fun FeedAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.minimumInteractiveComponentSize().clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = ACTION_DISC_ALPHA)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** "Swipe up for the next stream": a chevron and four words that say the page is a pager. Hidden from screen readers (the page description says "n of m"). */
@Composable
internal fun PagerHint(modifier: Modifier = Modifier) {
    Row(modifier.clearAndSetSemantics { }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(PhoneIcons.ChevronDown, contentDescription = null, tint = Color.White.copy(alpha = INFO_ALPHA), modifier = Modifier.size(16.dp).rotate(HALF_TURN))
        Text(stringResource(R.string.phone_feed_swipe_hint), color = Color.White.copy(alpha = INFO_ALPHA), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
internal fun RailButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp).clip(CircleShape).background(Color.Black.copy(alpha = RAIL_ALPHA))) {
        Icon(icon, contentDescription = description, tint = Color.White)
    }
}

/**
 * The last few chat lines of the page the viewer stayed on, over the picture. Its presenter (and so its chat connection) lives in a store
 * of its own that is cleared when this leaves the screen: swiping on closes the connection, a feed never holds more than one.
 */
@Composable
private fun FeedChat(channelId: String, modifier: Modifier = Modifier) {
    val owner = remember(channelId) {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    androidx.compose.runtime.CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
        val viewModel: FeedChatViewModel = koinViewModel(key = "feed-$channelId", parameters = { parametersOf(channelId) })
        val chat by viewModel.presenter.lines.collectAsStateWithLifecycle()
        val settings by viewModel.presenter.chatSettings.collectAsStateWithLifecycle()
        val style = remember(settings) { settings.toPhoneStyle() }
        val last = remember(chat) { chat.takeLast(CHAT_LINES).toImmutableList() }
        if (last.isNotEmpty()) {
            Box(modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).background(overVideoPanelColor(style))) {
                PhoneChatList(last, style, overVideo = true, animationsAllowed = false)
            }
        }
    }
}

/** What a page shows besides its stream: the sound, the chat and the play prompt. */
@androidx.compose.runtime.Immutable
private class FeedView(val muted: Boolean, val chatOn: Boolean, val showChatFor: String?, val tapToPlay: Boolean)

/** What the viewer can do on a page. */
@androidx.compose.runtime.Immutable
private class FeedActions(
    val onToggleMute: () -> Unit,
    val onToggleChat: () -> Unit,
    val onPlayHere: () -> Unit,
    val onOpen: () -> Unit,
    val onChannel: () -> Unit,
)

private const val PLAYERS = 2

/** The rendition the previews ask for; a stream that does not offer it falls back to the player's default (see `QualityPolicy`). */
private const val PREVIEW_QUALITY = "360p30"
private const val DWELL_MS = 2_000L
private const val CHAT_LINES = 8
internal const val BACKDROP_ALPHA = 0.55f
internal const val BACKDROP_FADE = 0.8f
internal const val CARD_TOP_WEIGHT = 0.35f
private const val ACTION_DISC_ALPHA = 0.16f
internal val MODE_SWITCH_SPACE = 52.dp
private const val SCRIM_ALPHA = 0.65f
private const val RAIL_ALPHA = 0.4f
private const val INFO_ALPHA = 0.8f
private const val HALF_TURN = 180f
private val AVATAR = 40.dp
