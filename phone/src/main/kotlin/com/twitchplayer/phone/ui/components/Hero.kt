package com.twitchplayer.phone.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.twitchplayer.phone.R
import com.twitchplayer.phone.ui.theme.PhoneColors
import com.twitchplayer.tv.core.data.model.LiveStream
import com.twitchplayer.tv.core.ui.formatViewers

/**
 * The stream Home leads with: one full-width 16:9 card, the picture under a bottom scrim that carries the streamer's face, the title (two
 * lines) and who and what. The viewer count sits top-right; no LIVE badge (every stream on Home is live, the count and its dot say so).
 * One described, focusable target.
 */
@Composable
fun HeroStreamCard(stream: LiveStream, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val viewers = remember(stream.viewers) { formatViewers(stream.viewers) }
    val description = stringResource(R.string.cd_stream, stream.name, viewers, stream.title)
    Box(
        modifier.fillMaxWidth().aspectRatio(THUMBNAIL_RATIO).clip(RoundedCornerShape(HERO_RADIUS)).clickableItem(onClick).semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        RemoteImage(stream.thumbnailUrl, Modifier.matchParentSize())
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0f to Color.Transparent, HERO_SCRIM_START to Color.Transparent, 1f to Color.Black.copy(alpha = HERO_SCRIM_ALPHA))))
        ViewersBadge(stream.viewers, Modifier.align(Alignment.TopEnd).padding(10.dp))
        Row(Modifier.align(Alignment.BottomStart).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(stream.avatarUrl, stream.name, HERO_AVATAR)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stream.title, style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Content), fontWeight = FontWeight.SemiBold, color = PhoneColors.OnScrim, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${stream.name} · ${stream.categoryName}", style = MaterialTheme.typography.bodySmall, color = PhoneColors.OnScrim.copy(alpha = HERO_META_ALPHA), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * The followed channels that are live, as a row of avatars with a live ring (the stories pattern): who is on, at a glance, before the
 * titles. A tap plays the stream. The ring is red but also described ("live") for screen readers.
 */
@Composable
fun LiveNowRow(streams: List<LiveStream>, onClick: (LiveStream) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 16.dp)) {
        items(streams, key = { "live-now-${it.channelId}" }, contentType = { "live-now" }) { stream ->
            val description = stringResource(R.string.cd_live_channel, stream.name)
            Column(
                Modifier.width(LIVE_NOW_WIDTH).clip(RoundedCornerShape(12.dp)).clickableItem { onClick(stream) }.padding(vertical = 4.dp).semantics(mergeDescendants = true) { contentDescription = description },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.size(LIVE_NOW_RING).border(RING_WIDTH, PhoneColors.Live, CircleShape).padding(RING_GAP), contentAlignment = Alignment.Center) {
                    Avatar(stream.avatarUrl, stream.name, LIVE_NOW_AVATAR)
                }
                Text(stream.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
        }
    }
}

private val HERO_RADIUS = 16.dp
private val HERO_AVATAR = 44.dp
private const val HERO_SCRIM_START = 0.4f
private const val HERO_SCRIM_ALPHA = 0.85f
private const val HERO_META_ALPHA = 0.85f
private val LIVE_NOW_WIDTH = 76.dp
private val LIVE_NOW_RING = 64.dp
private val LIVE_NOW_AVATAR = 54.dp
private val RING_WIDTH = 2.dp
private val RING_GAP = 3.dp
