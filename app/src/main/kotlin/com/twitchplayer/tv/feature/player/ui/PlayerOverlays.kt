package com.twitchplayer.tv.feature.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.twitchplayer.tv.R
import com.twitchplayer.tv.core.designsystem.component.ViewerCount
import com.twitchplayer.tv.core.designsystem.icon.TvIcons
import com.twitchplayer.tv.core.designsystem.theme.LocalMotion
import com.twitchplayer.tv.core.designsystem.theme.TvColors
import com.twitchplayer.tv.core.designsystem.theme.TvShapes
import com.twitchplayer.tv.core.designsystem.theme.TvType
import com.twitchplayer.tv.feature.player.data.chat.ChannelNotice
import com.twitchplayer.tv.feature.player.data.chat.RoomState
import com.twitchplayer.tv.feature.player.playback.AdBreak
import com.twitchplayer.tv.feature.player.playback.PlaybackStats
import java.util.Locale

/** Width of the statistics block (3 cells of [CELL_WIDTH] with gaps and padding), used to leave the pill its room. */
internal val STATS_WIDTH = 336.dp
private val CELL_WIDTH = 92.dp

/**
 * Playback statistics as a quiet 3 x 2 block (value over label): latency, video and bitrate over network, buffer and
 * dropped frames. Sized for the couch (16 / 13 sp), never focusable.
 */
@Composable
fun StatsStrip(stats: PlaybackStats, modifier: Modifier = Modifier) {
    Column(
        modifier.width(STATS_WIDTH).background(TvColors.Black85, TvShapes.Card).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatCell(stats.latencyMs?.let { formatLatency(it) } ?: "-", R.string.stats_latency)
            StatCell(formatVideo(stats), R.string.stats_video)
            StatCell(formatBitrate(stats.videoBitrate.toLong()), R.string.stats_bitrate)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatCell(formatBitrate(stats.bandwidth), R.string.stats_network)
            StatCell(formatLatency(stats.bufferMs), R.string.stats_buffer)
            StatCell(stats.droppedFrames.toString(), R.string.stats_dropped)
        }
    }
}

@Composable
private fun StatCell(value: String, label: Int) {
    Column(Modifier.width(CELL_WIDTH), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(value, style = TvType.MetaNumeric.copy(fontSize = 16.sp), color = TvColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(stringResource(label), style = TvType.Meta, color = TvColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** One pill over the video: an icon that says what kind of thing it is, then a short line. */
@Composable
private fun OverlayPill(icon: ImageVector, tint: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        modifier.height(36.dp).background(TvColors.Black85, TvShapes.Control).padding(horizontal = 12.dp).semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
        content()
    }
}

/** "Ad · 23 s": the stream is showing an advertisement; the countdown is read from the playlist. */
@Composable
fun AdChip(ad: AdBreak, modifier: Modifier = Modifier) {
    val seconds = ((ad.remainingMs + 999) / 1000).toInt()
    OverlayPill(TvIcons.Ad, TvColors.Tint, modifier) {
        Text(stringResource(R.string.player_ad_break, seconds), style = TvType.Pill, color = TvColors.Text, maxLines = 1)
    }
}

/** What happened on the channel: raid, new title or category, stream ended. One at a time, see [StatusLayers]. */
@Composable
fun ChannelBanner(notice: ChannelNotice, modifier: Modifier = Modifier) {
    when (notice) {
        is ChannelNotice.Raid -> OverlayPill(TvIcons.Users, TvColors.Tint, modifier) {
            Text(
                stringResource(if (notice.following) R.string.banner_raid_following else R.string.banner_raid, notice.toName),
                style = TvType.Pill,
                color = TvColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (notice.viewers > 0) ViewerCount(notice.viewers, color = TvColors.TextSecondary)
        }

        is ChannelNotice.Updated -> OverlayPill(TvIcons.Sparkles, TvColors.Tint, modifier) {
            val text = notice.category?.let { stringResource(R.string.banner_updated_category, it) } ?: notice.title.orEmpty()
            Text(text, style = TvType.Pill, color = TvColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        }

        ChannelNotice.Offline -> OverlayPill(TvIcons.OffAir, TvColors.TextSecondary, modifier) {
            Text(stringResource(R.string.banner_offline), style = TvType.Pill, color = TvColors.Text, maxLines = 1)
        }
    }
}

/**
 * The room's active modes as small icon chips (slow and followers-only carry their value). Each chip is a glyph, not a
 * sentence; the whole row is announced as one text for screen readers.
 */
@Composable
fun RoomChips(room: RoomState, modifier: Modifier = Modifier) {
    val slow = room.slowSeconds?.let { stringResource(R.string.room_slow, it) }
    val followers = room.followersOnlyMinutes?.let { if (it == 0) stringResource(R.string.room_followers) else stringResource(R.string.room_followers_min, it) }
    val subs = stringResource(R.string.room_subscribers).takeIf { room.subscribersOnly }
    val emotes = stringResource(R.string.room_emotes).takeIf { room.emoteOnly }
    val unique = stringResource(R.string.room_unique).takeIf { room.uniqueOnly }
    val spoken = listOfNotNull(slow, followers, subs, emotes, unique).joinToString(", ")
    Row(modifier.semantics { contentDescription = spoken }, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        ModeChip(slow != null, TvIcons.Clock, stringResource(R.string.unit_seconds_short, room.slowSeconds ?: 0))
        ModeChip(followers != null, TvIcons.Heart, room.followersOnlyMinutes?.takeIf { it > 0 }?.let { stringResource(R.string.unit_minutes_short, it) })
        ModeChip(subs != null, TvIcons.Star, null)
        ModeChip(emotes != null, TvIcons.Smile, null)
        ModeChip(unique != null, TvIcons.Unique, null)
    }
}

/** A chip that scales and fades in when its mode turns on and out when it turns off (neighbours slide with the row). */
@Composable
private fun androidx.compose.foundation.layout.RowScope.ModeChip(visible: Boolean, icon: ImageVector, value: String?) {
    val motion = LocalMotion.current
    androidx.compose.animation.AnimatedVisibility(
        visible,
        enter = androidx.compose.animation.fadeIn(motion.fast()) + androidx.compose.animation.scaleIn(motion.fast(), initialScale = 0.8f),
        exit = androidx.compose.animation.fadeOut(motion.exit()) + androidx.compose.animation.scaleOut(motion.exit(), targetScale = 0.8f),
    ) {
        Row(
            Modifier.height(22.dp).background(TvColors.Alt2, TvShapes.Chip).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(icon, null, tint = TvColors.Tint, modifier = Modifier.size(14.dp))
            if (value != null) Text(value, style = TvType.MetaNumeric, color = TvColors.Text, maxLines = 1)
        }
    }
}

internal fun formatLatency(ms: Long): String = String.format(Locale.ROOT, "%.1f s", ms / 1000.0)

internal fun formatBitrate(bitsPerSecond: Long): String = when {
    bitsPerSecond <= 0 -> "-"
    bitsPerSecond >= 1_000_000 -> String.format(Locale.ROOT, "%.1f Mb/s", bitsPerSecond / 1_000_000.0)
    else -> "${bitsPerSecond / 1000} kb/s"
}

internal fun formatVideo(s: PlaybackStats): String {
    if (s.height <= 0) return "-"
    val fps = if (s.frameRate > 0f) "@${Math.round(s.frameRate)}" else ""
    return "${s.height}p$fps"
}
