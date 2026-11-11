package com.twitchplayer.phone.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.twitchplayer.phone.R
import com.twitchplayer.tv.core.data.model.Category
import com.twitchplayer.tv.core.data.model.ChannelRef
import com.twitchplayer.tv.core.data.model.LiveStream
import com.twitchplayer.tv.core.ui.formatViewers

private val CardShape = RoundedCornerShape(12.dp)

/** A live stream: thumbnail with LIVE and viewers, then avatar, title and "channel · category". One focusable, described target. */
@Composable
fun StreamCard(stream: LiveStream, onClick: () -> Unit, modifier: Modifier = Modifier, minTitleLines: Int = 1) {
    val viewers = remember(stream.viewers) { formatViewers(stream.viewers) }
    val description = stringResource(R.string.cd_stream, stream.name, viewers, stream.title)
    Card(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = description },
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(THUMBNAIL_RATIO)) {
            RemoteImage(stream.thumbnailUrl, Modifier.matchParentSize())
            LiveBadge(Modifier.align(Alignment.TopStart).padding(8.dp))
            ViewersBadge(stream.viewers, Modifier.align(Alignment.BottomStart).padding(8.dp))
        }
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Avatar(stream.avatarUrl, stream.name, AvatarSmall)
            Column(Modifier.weight(1f)) {
                Text(stream.title, style = MaterialTheme.typography.titleSmall, minLines = minTitleLines, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    text = "${stream.name} · ${stream.categoryName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** A category poster (3:4) with its name below. */
@Composable
fun CategoryCard(category: Category, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = category.name },
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        RemoteImage(category.artUrl, Modifier.fillMaxWidth().aspectRatio(POSTER_RATIO))
        Text(
            text = category.name,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

/** A channel row (search results, offline follows, similar channels): avatar, name and its live state. */
@Composable
fun ChannelListItem(channel: ChannelRef, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val viewers = remember(channel.viewers) { formatViewers(channel.viewers) }
    val description = if (channel.isLive) stringResource(R.string.cd_live_channel, channel.name) else stringResource(R.string.cd_offline, channel.name)
    ListItem(
        headlineContent = { Text(channel.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            if (channel.isLive) {
                Text(stringResource(R.string.phone_channel_live_viewers, viewers), color = MaterialTheme.colorScheme.primary)
            } else {
                Text(stringResource(R.string.phone_channel_offline))
            }
        },
        leadingContent = { Avatar(channel.avatarUrl, channel.name, AvatarSmall) },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        modifier = modifier.clip(CardShape).clickableItem(onClick).semantics { contentDescription = description },
    )
}

/** From this width the stream grid has two or more columns. */
private const val MULTI_COLUMN_WIDTH_DP = 600

/**
 * Title lines to reserve on every [StreamCard] of a screen: with several columns two, so the cards of a row stay the same height
 * whether or not a title wraps; with one column one, so short titles do not leave a gap. Read once per screen, not per card.
 */
@Composable
@ReadOnlyComposable
fun streamTitleLines(): Int {
    val widthPx = LocalWindowInfo.current.containerSize.width
    val density = LocalDensity.current
    return if (with(density) { widthPx.toDp() } >= MULTI_COLUMN_WIDTH_DP.dp) 2 else 1
}

const val THUMBNAIL_RATIO = 16f / 9f
const val POSTER_RATIO = 3f / 4f
