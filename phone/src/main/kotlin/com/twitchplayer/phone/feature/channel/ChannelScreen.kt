package com.twitchplayer.phone.feature.channel

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twitchplayer.phone.R
import com.twitchplayer.phone.feature.auth.channelUrl
import com.twitchplayer.phone.feature.auth.isTwitchUrl
import com.twitchplayer.phone.navigation.PlaybackTarget
import com.twitchplayer.phone.ui.components.Avatar
import com.twitchplayer.phone.ui.components.AvatarLarge
import com.twitchplayer.phone.ui.components.DetailHeader
import com.twitchplayer.phone.ui.components.LoadHost
import com.twitchplayer.phone.ui.components.PhoneIcons
import com.twitchplayer.phone.ui.components.RefreshBox
import com.twitchplayer.phone.ui.components.RemoteImage
import com.twitchplayer.phone.ui.components.SectionHeader
import com.twitchplayer.phone.ui.components.SkeletonBlock
import com.twitchplayer.phone.ui.components.StreamCard
import com.twitchplayer.phone.ui.components.THUMBNAIL_RATIO
import com.twitchplayer.phone.ui.components.ThumbnailLabel
import com.twitchplayer.phone.ui.components.clickableItem
import com.twitchplayer.phone.ui.components.rememberLastSeenText
import com.twitchplayer.phone.ui.theme.PhoneColors
import com.twitchplayer.tv.core.data.model.ChannelClip
import com.twitchplayer.tv.core.data.model.ChannelDetails
import com.twitchplayer.tv.core.data.model.ChannelRef
import com.twitchplayer.tv.core.data.model.Replay
import com.twitchplayer.tv.core.data.model.ScheduledStream
import com.twitchplayer.tv.core.presentation.Load
import com.twitchplayer.tv.core.ui.formatViewers
import com.twitchplayer.tv.feature.channel.presentation.ChannelEvent
import com.twitchplayer.tv.feature.channel.presentation.ChannelViewModel
import com.twitchplayer.tv.feature.vod.data.VodProgress
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** A channel: who it is, whether it is live, what is next, and its clips and past broadcasts (paged). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelRoute(
    login: String,
    onBack: () -> Unit,
    onPlay: (PlaybackTarget) -> Unit,
    onChannel: (String) -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChannelViewModel = koinViewModel(key = "channel-$login") { parametersOf(login) },
) {
    val state by viewModel.presenter.state.collectAsStateWithLifecycle()
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // The header below names the channel: a second copy in the bar would only repeat it.
            DetailHeader(onBack = onBack)
        },
    ) { padding ->
        // On wide windows the page is a centred column, not a stretched list with a void beside it.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LoadHost(
                state.details,
                onRetry = { viewModel.presenter.onEvent(ChannelEvent.Retry) },
                onSignIn = onSignIn,
                modifier = Modifier.padding(padding).widthIn(max = CHANNEL_MAX_WIDTH),
                skeleton = { ChannelSkeleton() },
            ) { details ->
                RefreshBox(onRefresh = { viewModel.presenter.onEvent(ChannelEvent.Retry) }) {
                    ChannelContent(
                        details = details,
                        replays = allReplays(details, state.tail),
                        hasMore = hasMoreReplays(details, state.tail),
                        loadingMore = state.tail.loading,
                        progress = state.progress,
                        onLoadMore = { viewModel.presenter.onEvent(ChannelEvent.LoadMoreReplays(details.channel.id, details.replaysCursor)) },
                        onPlay = onPlay,
                        onChannel = onChannel,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelContent(
    details: ChannelDetails,
    replays: List<Replay>,
    hasMore: Boolean,
    loadingMore: Boolean,
    progress: Map<String, VodProgress>,
    onLoadMore: () -> Unit,
    onPlay: (PlaybackTarget) -> Unit,
    onChannel: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    val currentLoadMore by rememberUpdatedState(onLoadMore)
    val paging by rememberUpdatedState(Paging(canLoadMore = hasMore && !loadingMore, loaded = replays.size))
    // Asks for the next page when the end of the list comes into view, again after each page that still leaves it in view.
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val nearEnd = (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - PREFETCH_ITEMS
            Triple(nearEnd, paging.canLoadMore, paging.loaded)
        }.filter { (nearEnd, canLoadMore, _) -> nearEnd && canLoadMore }.distinctUntilChanged().collect { currentLoadMore() }
    }
    val now = remember(details) { System.currentTimeMillis() }
    val uriHandler = LocalUriHandler.current
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "header") {
            val openTwitch = { channelUrl(details.channel.login).takeIf(::isTwitchUrl)?.let { runCatching { uriHandler.openUri(it) } } }
            Header(
                channel = details.channel,
                followed = details.isFollowed,
                stream = details.stream,
                onFollowClick = { openTwitch() },
                onOpenTwitch = { openTwitch() },
            )
        }
        details.stream?.let { stream ->
            item(key = "live") {
                // A hero card the width of a tablet would dominate the page: it stops growing at a comfortable size.
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StreamCard(stream, onClick = { onPlay(PlaybackTarget.Live(stream.login)) }, showChannel = false)
                    liveForLabel(stream.startedAtMs, now)?.let { uptime ->
                        Text(stringResource(R.string.phone_live_for, uptime), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (details.schedule.isNotEmpty()) {
            item(key = "schedule-header") { SectionHeader(stringResource(R.string.channel_schedule)) }
            items(details.schedule, key = { "schedule-${it.startMs}-${it.title}" }) { ScheduleRow(it) }
        }
        if (details.clips.isNotEmpty()) {
            item(key = "clips-header") { SectionHeader(stringResource(R.string.sec_clips)) }
            item(key = "clips") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(details.clips, key = { it.slug }) { clip ->
                        ClipCard(clip, onClick = { onPlay(PlaybackTarget.Clip(clip.slug, clip.title)) }, modifier = Modifier.width(CLIP_CARD_WIDTH))
                    }
                }
            }
        }
        if (replays.isNotEmpty()) {
            item(key = "replays-header") { SectionHeader(stringResource(R.string.sec_replays)) }
            items(replays, key = { "replay-${it.id}" }) { replay ->
                ReplayRow(
                    replay = replay,
                    progress = progress[replay.id],
                    now = now,
                    onClick = {
                        val channel = details.channel
                        onPlay(
                            PlaybackTarget.Replay(
                                replay.id,
                                replay.title,
                                replay.durationSeconds,
                                channel.login,
                                channel.name,
                                channel.avatarUrl,
                                replay.views,
                                replay.publishedAtMs,
                            ),
                        )
                    },
                )
            }
            if (loadingMore) {
                item(key = "replays-loading") {
                    Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            }
        }
        if (details.similar.isNotEmpty()) {
            item(key = "similar-header") { SectionHeader(stringResource(R.string.sec_similar)) }
            item(key = "similar") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(details.similar, key = { it.id }) { channel -> SimilarChannel(channel, onClick = { onChannel(channel.login) }) }
                }
            }
        }
    }
}

/**
 * The identity of the channel: a banner of the theme's colours (Twitch's API gives no banner image), the avatar overlapping its lower edge,
 * the name, one status line (live with the count and category, or offline) and the actions: follow state, and open on Twitch. A live
 * channel's stream card (below) is the way to watch, so there is no second Watch button.
 */
@Composable
private fun Header(channel: ChannelRef, followed: Boolean, stream: com.twitchplayer.tv.core.data.model.LiveStream?, onFollowClick: () -> Unit, onOpenTwitch: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.fillMaxWidth().padding(bottom = BANNER_OVERLAP)) {
            Box(
                Modifier.fillMaxWidth().height(BANNER_HEIGHT).clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surfaceContainerHigh))),
            )
            Avatar(
                channel.avatarUrl,
                channel.name,
                AvatarLarge,
                Modifier.align(Alignment.BottomStart).offset(x = 16.dp, y = BANNER_OVERLAP).border(AVATAR_RING, MaterialTheme.colorScheme.background, CircleShape),
            )
        }
        Spacer(Modifier.height(BANNER_OVERLAP - 12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                channel.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            val status = if (stream != null) {
                listOf(stringResource(R.string.phone_search_viewers, remember(stream.viewers) { formatViewers(stream.viewers) }), stream.categoryName).filter { it.isNotBlank() }.joinToString(" · ")
            } else {
                rememberLastSeenText(channel.lastSeenLiveMs) ?: stringResource(R.string.phone_channel_offline)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (stream != null) Box(Modifier.size(8.dp).clip(CircleShape).background(PhoneColors.Live))
                Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FollowButton(followed, onFollowClick)
            IconButton(onClick = onOpenTwitch) { Icon(PhoneIcons.OpenInNew, contentDescription = stringResource(R.string.phone_player_open_twitch)) }
        }
    }
}

/**
 * The follow state as a button: tonal and ticked when following, outlined when not. Twitch's API no longer lets a third-party app follow
 * or unfollow, so the button opens the channel on twitch.tv (the trailing arrow says so) where the viewer changes it; the state is read
 * from the API when the page loads.
 */
@Composable
private fun FollowButton(followed: Boolean, onClick: () -> Unit) {
    val stateText = stringResource(if (followed) R.string.phone_follow_state_on else R.string.phone_follow_state_off)
    val hint = stringResource(R.string.phone_follow_opens_twitch)
    val semantics = Modifier.semantics {
        stateDescription = stateText
        onClick(label = hint, action = null)
    }
    val content: @Composable RowScope.() -> Unit = {
        if (followed) Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.padding(end = 6.dp).size(FOLLOW_ICON))
        Text(stringResource(if (followed) R.string.followed else R.string.phone_follow))
    }
    if (followed) {
        FilledTonalButton(onClick = onClick, modifier = semantics, content = content)
    } else {
        OutlinedButton(onClick = onClick, modifier = semantics, content = content)
    }
}

@Composable
private fun ScheduleRow(segment: ScheduledStream) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val whenText = remember(segment.startMs) {
        DateUtils.formatDateTime(
            context,
            segment.startMs,
            DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_ALL,
        )
    }
    Column {
        Text(segment.title.ifBlank { segment.category }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            listOf(whenText, segment.category).filter { it.isNotBlank() }.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ClipCard(clip: ChannelClip, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val views = remember(clip.views) { formatViewers(clip.views) }
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).clickableItem(onClick).semantics(mergeDescendants = true) { contentDescription = clip.title },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(THUMBNAIL_RATIO).clip(RoundedCornerShape(12.dp))) {
            RemoteImage(clip.thumbnailUrl, Modifier.matchParentSize())
            ThumbnailLabel(durationLabel(clip.durationSeconds), Modifier.align(Alignment.BottomEnd).padding(6.dp))
        }
        Column(Modifier.padding(horizontal = 2.dp)) {
            Text(clip.title, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.replay_views, views), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun ReplayRow(replay: Replay, progress: VodProgress?, now: Long, onClick: () -> Unit) {
    val views = remember(replay.views) { formatViewers(replay.views) }
    val published = remember(replay.publishedAtMs, now) {
        if (replay.publishedAtMs > 0L) DateUtils.getRelativeTimeSpanString(replay.publishedAtMs, now, DateUtils.MINUTE_IN_MILLIS).toString() else ""
    }
    val duration = replay.duration.takeIf { it.isNotBlank() } ?: durationLabel(replay.durationSeconds)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickableItem(onClick).semantics(mergeDescendants = true) { contentDescription = replay.title },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.width(REPLAY_THUMBNAIL_WIDTH).aspectRatio(THUMBNAIL_RATIO).clip(RoundedCornerShape(12.dp))) {
            RemoteImage(replay.thumbnailUrl, Modifier.matchParentSize())
            ThumbnailLabel(duration, Modifier.align(Alignment.BottomEnd).padding(6.dp))
            if (progress != null && progress.fraction > 0f) {
                LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth())
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(replay.title, style = MaterialTheme.typography.titleSmall.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Content), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(published, stringResource(R.string.replay_views, views)).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SimilarChannel(channel: ChannelRef, onClick: () -> Unit) {
    val description = if (channel.isLive) stringResource(R.string.cd_live_channel, channel.name) else stringResource(R.string.cd_offline, channel.name)
    Column(
        Modifier.width(SIMILAR_WIDTH).clip(RoundedCornerShape(12.dp)).clickableItem(onClick).padding(8.dp).semantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Avatar(channel.avatarUrl, channel.name, SIMILAR_AVATAR)
        Text(channel.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ChannelSkeleton() {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            SkeletonBlock(Modifier.width(AvatarLarge).aspectRatio(1f), androidx.compose.foundation.shape.CircleShape)
            SkeletonBlock(Modifier.width(SKELETON_NAME_WIDTH).padding(vertical = 10.dp))
        }
        SkeletonBlock(Modifier.fillMaxWidth().aspectRatio(THUMBNAIL_RATIO))
    }
}

/** What the pagination effect needs, read fresh each time (the effect itself is keyed on the list state only). */
private data class Paging(val canLoadMore: Boolean, val loaded: Int)

private const val PREFETCH_ITEMS = 3
private val FOLLOW_ICON = 18.dp
private val BANNER_HEIGHT = 96.dp
private val BANNER_OVERLAP = 36.dp
private val AVATAR_RING = 4.dp
private val CLIP_CARD_WIDTH = 220.dp
private val REPLAY_THUMBNAIL_WIDTH = 150.dp
private val SIMILAR_WIDTH = 80.dp
private val SIMILAR_AVATAR = 56.dp
private val SKELETON_NAME_WIDTH = 160.dp

private val CHANNEL_MAX_WIDTH = 640.dp
