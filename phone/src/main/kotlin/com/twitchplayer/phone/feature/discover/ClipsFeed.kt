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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.twitchplayer.phone.R
import com.twitchplayer.phone.feature.player.ExoVideoSurface
import com.twitchplayer.phone.feature.player.PauseWhenInBackground
import com.twitchplayer.phone.feature.player.RecordedPlayback
import com.twitchplayer.phone.feature.player.rememberPlayerSnapshot
import com.twitchplayer.phone.ui.components.Avatar
import com.twitchplayer.phone.ui.components.LoadHost
import com.twitchplayer.phone.ui.components.MessageState
import com.twitchplayer.phone.ui.components.PhoneIcons
import com.twitchplayer.phone.ui.components.RemoteImage
import com.twitchplayer.tv.core.data.model.FeedClip
import com.twitchplayer.tv.core.ui.formatViewers
import com.twitchplayer.tv.feature.feed.presentation.ClipsFeedViewModel
import com.twitchplayer.tv.feature.feed.presentation.FeedMerge
import com.twitchplayer.tv.feature.player.data.stream.ClipResolver
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * The clips half of Discover: the week's most viewed clips of the busiest categories, one per screen, looping, muted until the viewer turns
 * the sound on. Same rules as the live pager: two players only (the page index parity picks one), the next clip is resolved and prepared
 * paused, nothing is fetched ahead when autoplay is not allowed, and a tap opens the clip in the full player.
 */
@Composable
fun ClipsRoute(
    onOpenClip: (slug: String, title: String) -> Unit,
    onChannel: (String) -> Unit,
    onSignIn: () -> Unit,
    playerOpen: Boolean,
    modifier: Modifier = Modifier,
    viewModel: ClipsFeedViewModel = koinViewModel(),
) {
    val state by viewModel.presenter.state.collectAsStateWithLifecycle()
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LoadHost(state.load, onRetry = viewModel.presenter::retry, onSignIn = onSignIn, skeleton = { Box(Modifier.fillMaxSize().background(Color.Black)) }, staleMarker = false) { content ->
            if (content.clips.isEmpty()) {
                MessageState(title = stringResource(R.string.phone_feed_clips_empty), modifier = Modifier.fillMaxSize())
            } else {
                ClipsPager(content.clips, state.autoplayAllowed, playerOpen, onOpenClip, onChannel)
            }
        }
    }
}

@Composable
private fun ClipsPager(
    clips: ImmutableList<FeedClip>,
    autoplayAllowed: Boolean,
    playerOpen: Boolean,
    onOpenClip: (String, String) -> Unit,
    onChannel: (String) -> Unit,
    resolver: ClipResolver = koinInject(),
) {
    val context = LocalContext.current
    // Frozen on the first composition; a refresh only changes what is ahead of the page the viewer rests on (see FeedMerge).
    var shown by remember { mutableStateOf(clips) }
    val pagerState = rememberPagerState { shown.size }
    val settled = pagerState.settledPage
    DisposableEffect(clips) {
        shown = FeedMerge.merge(shown, clips, pagerState.settledPage) { it.slug }
        onDispose { }
    }
    var muted by rememberSaveable { mutableStateOf(true) }
    var asked by remember { mutableStateOf(emptySet<String>()) }
    // handleAudioFocus = false: muted clips must not pause the viewer's music; focus is taken when the sound is turned on.
    val players = remember { listOf(RecordedPlayback(context, handleAudioFocus = false), RecordedPlayback(context, handleAudioFocus = false)) }
    players.forEach { PauseWhenInBackground(it.exo) }
    // The slug each player holds, and the files already resolved (a resolved address is kept: a clip swiped back to is not asked for again).
    val holding = remember { mutableStateListOf<String?>(null, null) }
    val urls = remember { ClipUrlCache() }

    LaunchedEffect(settled, shown, autoplayAllowed, playerOpen, muted, asked) {
        val clip = shown.getOrNull(settled) ?: return@LaunchedEffect
        val slot = settled % PLAYERS
        val other = (settled + 1) % PLAYERS
        players[slot].exo.volume = if (muted) 0f else 1f
        players[other].exo.volume = 0f
        players[slot].setAudioFocus(FeedAudio.takesFocus(muted, playerOpen))
        players[other].setAudioFocus(false)
        if (playerOpen) {
            players[slot].exo.pause()
            players[other].exo.pause()
            return@LaunchedEffect
        }
        val play = autoplayAllowed || clip.slug in asked
        // A resolved address older than ClipUrlCache keeps is asked for again (a clip swiped back to long after, in a player that holds another).
        if (holding[slot] != clip.slug) {
            val url = urls.get(clip.slug) ?: resolve(resolver, clip.slug)?.also { urls.put(clip.slug, it) }
            if (url != null) {
                players[slot].exo.repeatMode = Player.REPEAT_MODE_ONE
                players[slot].setSource(url, 0L, recording = false, playWhenReady = play)
                holding[slot] = clip.slug
            }
        } else {
            players[slot].exo.playWhenReady = play
        }
        val upcoming = shown.getOrNull(settled + 1)
        if (upcoming != null && autoplayAllowed && holding[other] != upcoming.slug) {
            val url = urls.get(upcoming.slug) ?: resolve(resolver, upcoming.slug)?.also { urls.put(upcoming.slug, it) }
            if (url != null) {
                players[other].exo.repeatMode = Player.REPEAT_MODE_ONE
                players[other].setSource(url, 0L, recording = false, playWhenReady = false)
                holding[other] = upcoming.slug
            }
        }
    }

    val fling = PagerDefaults.flingBehavior(state = pagerState, pagerSnapDistance = PagerSnapDistance.atMost(1))
    VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize().background(Color.Black), beyondViewportPageCount = 0, flingBehavior = fling, key = { shown[it].slug }) { page ->
        val clip = shown[page]
        ClipPage(
            clip = clip,
            index = page,
            count = shown.size,
            player = players[page % PLAYERS],
            view = ClipView(mine = holding[page % PLAYERS] == clip.slug, settledPage = page == settled, muted = muted, tapToPlay = !autoplayAllowed && clip.slug !in asked),
            actions = ClipActions(
                onToggleMute = { muted = !muted },
                onPlayHere = { asked = asked + clip.slug },
                onOpen = { onOpenClip(clip.slug, clip.title) },
                onChannel = { onChannel(clip.channelLogin) },
            ),
        )
    }
}

/** The address of the clip's best file, asked for up to [ClipRetry.ATTEMPTS] times (a refusal is often a hiccup); null when it keeps failing. */
private suspend fun resolve(resolver: ClipResolver, slug: String): String? {
    for (attempt in 1..ClipRetry.ATTEMPTS) {
        delay(ClipRetry.delayBefore(attempt))
        try {
            resolver.resolve(slug).firstOrNull()?.url?.let { return it }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Try again, then give up: the page keeps its thumbnail.
        }
    }
    return null
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClipPage(
    clip: FeedClip,
    index: Int,
    count: Int,
    player: RecordedPlayback,
    view: ClipView,
    actions: ClipActions,
) {
    val mine = view.mine
    val settledPage = view.settledPage
    val muted = view.muted
    val tapToPlay = view.tapToPlay
    val onToggleMute = actions.onToggleMute
    val onPlayHere = actions.onPlayHere
    val onOpen = actions.onOpen
    val onChannel = actions.onChannel
    val snapshot = rememberPlayerSnapshot(player.exo)
    val showVideo = mine && snapshot.hasPicture
    val views = remember(clip.views) { formatViewers(clip.views) }
    val openLabel = stringResource(R.string.phone_feed_clip_open)
    val togglePlayLabel = stringResource(if (snapshot.playWhenReady) R.string.player_pause else R.string.player_play)
    val togglePlay = { if (player.exo.isPlaying) player.exo.pause() else player.exo.play() }
    val description = stringResource(R.string.phone_feed_clip_description, clip.title, clip.channelName, views, index + 1, count)
    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .semantics {
                contentDescription = description
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
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = openLabel,
                onClick = onOpen,
                onLongClickLabel = togglePlayLabel,
                onLongClick = togglePlay,
            ),
    ) {
        // The surface is only built for the page the viewer rests on, never while it slides in (see the live pager).
        if (mine && settledPage) ExoVideoSurface(player.exo, Modifier.fillMaxSize())
        if (!showVideo) RemoteImage(clip.thumbnailUrl, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, SCRIM_START to Color.Transparent, 1f to Color.Black.copy(alpha = SCRIM_ALPHA))))
        if (tapToPlay && settledPage) {
            Text(
                stringResource(R.string.phone_feed_tap_to_play),
                modifier = Modifier.align(Alignment.Center).clip(CircleShape).background(Color.Black.copy(alpha = SCRIM_ALPHA)).clickable(onClick = onPlayHere).padding(horizontal = 20.dp, vertical = 12.dp),
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
            )
        }
        Column(Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            RailButton(if (muted) PhoneIcons.VolumeOff else PhoneIcons.VolumeUp, stringResource(if (muted) R.string.phone_feed_mute else R.string.phone_feed_unmute), onToggleMute)
            RailButton(PhoneIcons.OpenInNew, stringResource(R.string.phone_feed_channel), onChannel)
            RailButton(PhoneIcons.Discover, stringResource(R.string.phone_feed_clip_open), onOpen)
        }
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth(INFO_WIDTH).padding(start = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Avatar(clip.channelAvatarUrl, clip.channelName, AVATAR_SIZE, Modifier.clickable(onClick = onChannel))
                Column(Modifier.weight(1f)) {
                    Text(clip.channelName, color = Color.White, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(views, color = Color.White.copy(alpha = INFO_ALPHA), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }
            Text(clip.title, color = Color.White, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** What a clip page shows besides the clip: whether its player holds it, whether it is the settled page, the sound and the play prompt. */
@androidx.compose.runtime.Immutable
private class ClipView(val mine: Boolean, val settledPage: Boolean, val muted: Boolean, val tapToPlay: Boolean)

@androidx.compose.runtime.Immutable
private class ClipActions(val onToggleMute: () -> Unit, val onPlayHere: () -> Unit, val onOpen: () -> Unit, val onChannel: () -> Unit)

private const val PLAYERS = 2
private const val SCRIM_START = 0.55f
private const val SCRIM_ALPHA = 0.65f
private const val INFO_ALPHA = 0.8f
private const val INFO_WIDTH = 0.78f
private val AVATAR_SIZE = 40.dp
