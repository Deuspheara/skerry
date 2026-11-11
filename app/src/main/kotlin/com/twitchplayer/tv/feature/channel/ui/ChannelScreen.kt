package com.twitchplayer.tv.feature.channel.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.twitchplayer.tv.R
import com.twitchplayer.tv.core.data.model.ChannelClip
import com.twitchplayer.tv.core.data.model.ChannelDetails
import com.twitchplayer.tv.core.data.model.ChannelRef
import com.twitchplayer.tv.core.data.model.Replay
import com.twitchplayer.tv.core.data.model.ScheduledStream
import com.twitchplayer.tv.core.designsystem.component.Avatar
import com.twitchplayer.tv.core.designsystem.component.CardRow
import com.twitchplayer.tv.core.designsystem.component.HeroBackdrop
import com.twitchplayer.tv.core.designsystem.component.LiveBadge
import com.twitchplayer.tv.core.designsystem.component.LocalToast
import com.twitchplayer.tv.core.designsystem.component.SectionHeader
import com.twitchplayer.tv.core.designsystem.component.SkeletonChannel
import com.twitchplayer.tv.core.designsystem.component.TvButton
import com.twitchplayer.tv.core.designsystem.component.TvIconButton
import com.twitchplayer.tv.core.designsystem.component.ViewerCount
import com.twitchplayer.tv.core.designsystem.component.heroImage
import com.twitchplayer.tv.core.designsystem.icon.TvIcons
import com.twitchplayer.tv.core.designsystem.theme.TvColors
import com.twitchplayer.tv.core.designsystem.theme.TvDimens
import com.twitchplayer.tv.core.designsystem.theme.TvShapes
import com.twitchplayer.tv.core.designsystem.theme.TvType
import com.twitchplayer.tv.core.ui.LoadGate
import com.twitchplayer.tv.core.ui.TvAsyncImage
import com.twitchplayer.tv.core.ui.TvFocusable
import com.twitchplayer.tv.core.ui.formatViewers
import com.twitchplayer.tv.core.ui.rememberRowSpec
import com.twitchplayer.tv.core.ui.sharedArt
import com.twitchplayer.tv.core.ui.staggerIn
import com.twitchplayer.tv.feature.channel.presentation.ChannelViewModel
import com.twitchplayer.tv.feature.vod.data.VodProgress
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ChannelRoute(
    login: String,
    onBack: () -> Unit,
    onWatch: (String) -> Unit,
    onChannel: (String) -> Unit,
    onClip: (ChannelClip) -> Unit,
    onReplay: (Replay) -> Unit,
    viewModel: ChannelViewModel = koinViewModel(key = login) { parametersOf(login) },
) {
    val load by viewModel.state.collectAsState()
    val tail by viewModel.tail.collectAsState()
    val progress by viewModel.progress.collectAsState()
    LoadGate(load, onRetry = viewModel::retry, skeleton = { SkeletonChannel() }) { details ->
        ChannelScreen(
            details, onBack, onWatch, onChannel,
            onClip = {
                viewModel.lastClip = it.slug
                onClip(it)
            },
            restoreClip = viewModel.lastClip,
            extraReplays = tail.replays,
            progress = progress,
            onReplay = {
                viewModel.lastReplay = it.id
                onReplay(it)
            },
            restoreReplay = viewModel.lastReplay,
            loadMoreReplays = { viewModel.loadMoreReplays(details.channel.id, details.replaysCursor) },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelScreen(
    details: ChannelDetails,
    onBack: () -> Unit,
    onWatch: (String) -> Unit,
    onChannel: (String) -> Unit,
    onClip: (ChannelClip) -> Unit,
    restoreClip: String?,
    extraReplays: List<Replay>,
    progress: Map<String, VodProgress>,
    onReplay: (Replay) -> Unit,
    restoreReplay: String?,
    loadMoreReplays: () -> Unit,
) {
    val channel = details.channel
    // First focus goes to something that does something: Watch when live, else the first similar channel, else Back.
    val primary = remember { FocusRequester() }
    val back = remember { FocusRequester() }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val clipFocus = remember { FocusRequester() }
    val replayFocus = remember { FocusRequester() }
    val replayRowState = androidx.compose.foundation.lazy.rememberLazyListState()
    // Every page loaded so far; the id keys the cards, so a page that repeats one does not duplicate it.
    val replays = remember(details.replays, extraReplays) { if (extraReplays.isEmpty()) details.replays else (details.replays + extraReplays).distinctBy { it.id } }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val replayIndex = replays.indexOfFirst { it.id == restoreReplay }
        if (replayIndex >= 0) {
            // Back from a replay: scroll to the replays row, bring the opened card into view and give it focus.
            listState.scrollToItem(1 + (if (details.schedule.isNotEmpty()) 1 else 0) + (if (details.clips.isNotEmpty()) 1 else 0))
            replayRowState.scrollToItem(replayIndex)
            androidx.compose.runtime.withFrameNanos { }
            if (runCatching { replayFocus.requestFocus() }.isSuccess) return@LaunchedEffect
        }
        val clipIndex = details.clips.indexOfFirst { it.slug == restoreClip }
        if (clipIndex >= 0) {
            // Back from a clip: scroll to the clips row and give focus to the card that was opened.
            listState.scrollToItem(1 + (if (details.schedule.isNotEmpty()) 1 else 0))
            androidx.compose.runtime.withFrameNanos { }
            if (runCatching { clipFocus.requestFocus() }.isSuccess) return@LaunchedEffect
        }
        runCatching { if (channel.isLive || details.similar.isNotEmpty()) primary.requestFocus() else back.requestFocus() }
    }
    val start = TvDimens.ContentStart
    // Scroll only when the focused item would be clipped: the header (and its Back button) stays in place.
    CompositionLocalProvider(LocalBringIntoViewSpec provides rememberRowSpec(start = TvDimens.SafeTop, peek = TvDimens.SafeBottom + 24.dp)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(top = TvDimens.SafeTop, bottom = TvDimens.SafeBottom),
        ) {
            item(key = "header", contentType = "header") {
                Box {
                    // Live: the stream's own picture fills the right of the header, so the page opens on the channel, not on a form.
                    details.stream?.thumbnailUrl?.takeIf { channel.isLive && it.isNotEmpty() }?.let {
                        Box(Modifier.matchParentSize(), contentAlignment = Alignment.CenterEnd) { HeroBackdrop(heroImage(it)) }
                    }
                    Column(Modifier.staggerIn(0).padding(start = start, end = TvDimens.ContentEnd, top = 12.dp)) {
                        Box(Modifier.padding(bottom = 12.dp)) {
                            TvIconButton(TvIcons.ChevronLeft, stringResource(R.string.cd_back), onBack, Modifier.focusRequester(back), size = 40.dp, iconSize = 20.dp, container = TvColors.Alt)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Top) {
                            Avatar(channel.name, channel.avatarUrl, Modifier.sharedArt("av:${channel.login}"), size = 80.dp)
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(channel.name, style = TvType.Display, color = TvColors.Text, maxLines = 1)
                                Row(Modifier.heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    if (channel.isLive) {
                                        LiveBadge(large = true)
                                        ViewerCount(channel.viewers)
                                    } else {
                                        InfoChip(TvIcons.OffAir, stringResource(R.string.sec_offline))
                                    }
                                    details.stream?.categoryName?.takeIf { it.isNotEmpty() }?.let {
                                        Text(it, style = TvType.Label, color = TvColors.TextSecondary, maxLines = 1)
                                    }
                                    // Following is shown as a fact: Twitch has no follow API for third-party apps.
                                    if (details.isFollowed) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Icon(TvIcons.HeartFilled, null, tint = TvColors.Purple, modifier = Modifier.size(14.dp))
                                            Text(stringResource(R.string.followed), style = TvType.Label, color = TvColors.TextSecondary, maxLines = 1)
                                        }
                                    }
                                }
                                details.stream?.title?.takeIf { channel.isLive && it.isNotEmpty() }?.let {
                                    Text(it, style = TvType.Body, color = TvColors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(560.dp))
                                }
                                details.stream?.let { stream ->
                                    // Language and the broadcaster's tags, as one quiet line.
                                    val facts = (listOf(stream.language.uppercase()) + stream.tags.take(4)).filter { it.isNotBlank() }
                                    if (channel.isLive && facts.isNotEmpty()) {
                                        Text(facts.joinToString(" · "), style = TvType.Meta, color = TvColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(560.dp))
                                    }
                                }
                                if (channel.isLive) {
                                    Box(Modifier.padding(top = 4.dp, bottom = 4.dp)) {
                                        TvButton(stringResource(R.string.watch), TvIcons.Play, { onWatch(channel.login) }, Modifier.focusRequester(primary), primary = true)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (details.schedule.isNotEmpty()) {
                item(key = "schedule", contentType = "section") {
                    Column(Modifier.staggerIn(1)) {
                        SectionHeader(TvIcons.Clock, stringResource(R.string.channel_schedule), Modifier.padding(start = start, top = 20.dp, bottom = 8.dp))
                        val context = androidx.compose.ui.platform.LocalContext.current
                        Column(Modifier.padding(start = start, end = TvDimens.ContentEnd), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            details.schedule.forEachIndexed { index, slot -> ScheduleRow(slot, context, first = index == 0) }
                        }
                    }
                }
            }
            if (details.clips.isNotEmpty()) {
                item(key = "clips", contentType = "section") {
                    Column(Modifier.staggerIn(2)) {
                        SectionHeader(TvIcons.Play, stringResource(R.string.sec_clips), Modifier.padding(start = start, top = 20.dp, bottom = 8.dp))
                        CardRow(startPadding = start) {
                            items(details.clips, key = { it.slug }, contentType = { "clip" }) { clip ->
                                ClipCard(clip, { onClip(clip) }, if (clip.slug == restoreClip) Modifier.focusRequester(clipFocus) else Modifier)
                            }
                        }
                    }
                }
            }
            if (replays.isNotEmpty()) {
                item(key = "replays", contentType = "section") {
                    Column(Modifier.staggerIn(3)) {
                        SectionHeader(TvIcons.Film, stringResource(R.string.sec_replays), Modifier.padding(start = start, top = 20.dp, bottom = 8.dp))
                        CardRow(state = replayRowState, startPadding = start) {
                            itemsIndexed(replays, key = { _, replay -> replay.id }, contentType = { _, _ -> "replay" }) { index, replay ->
                                ReplayCard(
                                    replay,
                                    progress[replay.id],
                                    { onReplay(replay) },
                                    Modifier
                                        .then(if (replay.id == restoreReplay) Modifier.focusRequester(replayFocus) else Modifier)
                                        // Near the end of what is loaded, ask for the next page (hasFocus: see the TvFocusable note in memory).
                                        .onFocusChanged { if (it.hasFocus && index >= replays.size - 3) loadMoreReplays() },
                                )
                            }
                        }
                    }
                }
            }
            if (details.similar.isNotEmpty()) {
                item(key = "similar", contentType = "section") {
                    Column(Modifier.staggerIn(3)) {
                        SectionHeader(TvIcons.Users, stringResource(R.string.sec_similar), Modifier.padding(start = start, top = 20.dp))
                        CardRow(startPadding = start) {
                            items(details.similar, key = { it.id }, contentType = { "similar" }) { other ->
                                SimilarChannel(other, { onChannel(other.login) }, if (other === details.similar.first() && !channel.isLive) Modifier.focusRequester(primary) else Modifier)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoChip(icon: androidx.compose.ui.graphics.vector.ImageVector?, text: String, tint: androidx.compose.ui.graphics.Color = TvColors.TextSecondary) {
    Row(
        Modifier.height(28.dp).background(TvColors.Alt2, TvShapes.Chip).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
        Text(text, style = TvType.Label, color = TvColors.Text, maxLines = 1)
    }
}

/** One past broadcast: thumbnail with its length and how far the viewer got, title, then "2 days ago · 12K views". */
@Composable
private fun ReplayCard(replay: Replay, progress: VodProgress?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val ago = remember(replay.publishedAtMs) {
        if (replay.publishedAtMs > 0) android.text.format.DateUtils.getRelativeTimeSpanString(replay.publishedAtMs, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS).toString() else ""
    }
    val views = stringResource(R.string.replay_views, formatViewers(replay.views))
    val meta = remember(ago, views) { listOf(ago, views).filter { it.isNotEmpty() }.joinToString(" · ") }
    TvFocusable(onClick = onClick, shape = TvShapes.Card, description = replay.title, focusScale = TvDimens.FOCUS_SCALE_CARD, modifier = modifier.width(TvDimens.LiveCardWidth)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            MediaThumb(replay.thumbnailUrl, replay.duration, progress = progress?.fraction ?: 0f)
            Text(replay.title, style = TvType.Title, color = TvColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(meta, style = TvType.Meta, color = TvColors.TextSecondary.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SimilarChannel(channel: ChannelRef, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TvFocusable(
        onClick = onClick,
        shape = TvShapes.Card,
        description = stringResource(R.string.cd_live_channel, channel.name),
        modifier = modifier.width(88.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box {
                Avatar(channel.name, channel.avatarUrl, Modifier.sharedArt("av:${channel.login}"), size = 56.dp)
                Box(
                    Modifier.align(Alignment.BottomEnd).size(14.dp)
                        .background(TvColors.Body, androidx.compose.foundation.shape.CircleShape).padding(2.dp)
                        .background(TvColors.Live, androidx.compose.foundation.shape.CircleShape),
                )
            }
            Text(
                channel.name,
                style = TvType.Meta.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                color = TvColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ClipCard(clip: ChannelClip, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TvFocusable(onClick = onClick, shape = TvShapes.Card, description = clip.title, focusScale = TvDimens.FOCUS_SCALE_CARD, modifier = modifier.width(TvDimens.LiveCardWidth)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            MediaThumb(clip.thumbnailUrl, "%d:%02d".format(clip.durationSeconds / 60, clip.durationSeconds % 60))
            Text(clip.title, style = TvType.Title, color = TvColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * One scheduled stream: the day and the time as a fixed-width date column, then the title over its category. The next one
 * is brighter; the ones after it step back.
 */
@Composable
private fun ScheduleRow(slot: ScheduledStream, context: android.content.Context, first: Boolean) {
    val day = android.text.format.DateUtils.formatDateTime(context, slot.startMs, android.text.format.DateUtils.FORMAT_SHOW_WEEKDAY or android.text.format.DateUtils.FORMAT_ABBREV_WEEKDAY or android.text.format.DateUtils.FORMAT_SHOW_DATE or android.text.format.DateUtils.FORMAT_ABBREV_MONTH)
    val time = android.text.format.DateUtils.formatDateTime(context, slot.startMs, android.text.format.DateUtils.FORMAT_SHOW_TIME)
    Row(
        Modifier.widthIn(max = 600.dp).fillMaxWidth().background(TvColors.Base, TvShapes.Card).padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(132.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(time, style = TvType.Title.copy(fontSize = 20.sp), color = if (first) TvColors.Text else TvColors.TextSecondary, maxLines = 1)
            Text(day, style = TvType.Meta, color = TvColors.TextSecondary, maxLines = 1)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                slot.title.ifBlank { slot.category },
                style = TvType.Title,
                color = if (first) TvColors.Text else TvColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 520.dp),
            )
            if (slot.title.isNotBlank() && slot.category.isNotBlank()) Text(slot.category, style = TvType.Meta, color = TvColors.TextSecondary, maxLines = 1)
        }
    }
}

/** The thumbnail shared by clip and replay cards: 16:9 art with a duration pill. */
@Composable
private fun MediaThumb(url: String, duration: String, semibold: Boolean = false, progress: Float = 0f) {
    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(TvShapes.Card)) {
        TvAsyncImage(url, null, TvDimens.LiveCardWidth, 102.dp, Modifier.matchParentSize())
        Text(
            duration,
            style = if (semibold) TvType.MetaNumeric.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) else TvType.MetaNumeric,
            color = TvColors.Text,
            modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).background(TvColors.Black80, androidx.compose.foundation.shape.RoundedCornerShape(50)).padding(horizontal = 6.dp, vertical = 3.dp),
        )
        // How far the viewer got: a thin purple line along the bottom edge (starts at the reading edge).
        if (progress > 0.01f) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(TvColors.Black60)) {
                Box(Modifier.fillMaxWidth(progress.coerceAtMost(1f)).height(4.dp).background(TvColors.Purple))
            }
        }
    }
}
