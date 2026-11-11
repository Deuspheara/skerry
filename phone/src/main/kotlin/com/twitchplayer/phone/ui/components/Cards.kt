package com.twitchplayer.phone.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.twitchplayer.phone.R
import com.twitchplayer.phone.ui.theme.PhoneColors
import com.twitchplayer.tv.core.data.model.Category
import com.twitchplayer.tv.core.data.model.ChannelRef
import com.twitchplayer.tv.core.data.model.LiveStream
import com.twitchplayer.tv.core.ui.formatViewers

private val ThumbnailShape = RoundedCornerShape(12.dp)
private val CardShape = RoundedCornerShape(12.dp)

/**
 * How stream cards are laid out on this window. A phone (one column) gets a **compact row**: a small 16:9 thumbnail beside the
 * title, so six or seven streams fit on a screen instead of two. From [MULTI_COLUMN_WIDTH_DP] the grid has several columns and each
 * card is a **vertical card** (thumbnail over text) with two title lines reserved so the cards of a row stay level.
 */
@Immutable
data class StreamLayout(val compact: Boolean, val minTitleLines: Int) {
    companion object {
        val Compact = StreamLayout(compact = true, minTitleLines = 1)
        val Wide = StreamLayout(compact = false, minTitleLines = 2)
        val WideSingleColumn = StreamLayout(compact = false, minTitleLines = 1)
    }
}

/** Read once per screen, not per card. */
@Composable
@ReadOnlyComposable
fun streamLayout(): StreamLayout {
    val widthPx = LocalWindowInfo.current.containerSize.width
    val density = LocalDensity.current
    return if (with(density) { widthPx.toDp() } >= MULTI_COLUMN_WIDTH_DP.dp) StreamLayout.Wide else StreamLayout.Compact
}

/**
 * A live stream. No card container: the text sits on the page surface (a tonal strip under a dark thumbnail looked pasted on in the
 * light theme), the thumbnail carries LIVE and the viewer count. One described, focusable target. [showChannel] is false on the
 * channel's own page, where the header already says whose stream it is.
 */
@Composable
fun StreamCard(stream: LiveStream, onClick: () -> Unit, modifier: Modifier = Modifier, layout: StreamLayout = StreamLayout.WideSingleColumn, showChannel: Boolean = true) {
    val viewers = remember(stream.viewers) { formatViewers(stream.viewers) }
    val description = stringResource(R.string.cd_stream, stream.name, viewers, stream.title)
    val rowModifier = modifier.clip(CardShape).clickableItem(onClick).semantics(mergeDescendants = true) { contentDescription = description }
    if (layout.compact) {
        Row(rowModifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Thumbnail(stream, Modifier.width(COMPACT_THUMBNAIL_WIDTH))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stream.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (showChannel) {
                    Text(stream.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(stream.categoryName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    } else {
        Column(rowModifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Thumbnail(stream, Modifier.fillMaxWidth())
            Row(Modifier.padding(horizontal = 2.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                if (showChannel) Avatar(stream.avatarUrl, stream.name, AvatarSmall)
                Column(Modifier.weight(1f)) {
                    Text(stream.title, style = MaterialTheme.typography.titleSmall, minLines = layout.minTitleLines, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        text = if (showChannel) "${stream.name} · ${stream.categoryName}" else stream.categoryName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun Thumbnail(stream: LiveStream, modifier: Modifier = Modifier) {
    Box(modifier.aspectRatio(THUMBNAIL_RATIO).clip(ThumbnailShape)) {
        RemoteImage(stream.thumbnailUrl, Modifier.matchParentSize())
        LiveBadge(Modifier.align(Alignment.TopStart).padding(6.dp))
        ViewersBadge(stream.viewers, Modifier.align(Alignment.BottomStart).padding(6.dp))
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

/**
 * A channel row (search results, offline follows, similar channels): avatar, name and its live state. The state is one tone and one
 * line, "Live · 12K viewers" with a red dot, not a second accent colour: it keeps AA contrast on both themes.
 */
@Composable
fun ChannelListItem(channel: ChannelRef, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val viewers = remember(channel.viewers) { formatViewers(channel.viewers) }
    val description = if (channel.isLive) stringResource(R.string.cd_live_channel, channel.name) else stringResource(R.string.cd_offline, channel.name)
    ListItem(
        headlineContent = { Text(channel.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            if (channel.isLive) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(PhoneColors.Live))
                    Text(stringResource(R.string.phone_channel_live_viewers, viewers), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Text(stringResource(R.string.phone_channel_offline), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        leadingContent = { Avatar(channel.avatarUrl, channel.name, AvatarSmall) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier.clip(CardShape).clickableItem(onClick).semantics { contentDescription = description },
    )
}

/** From this width the stream grid has two or more columns. */
private const val MULTI_COLUMN_WIDTH_DP = 600
private val COMPACT_THUMBNAIL_WIDTH = 168.dp

const val THUMBNAIL_RATIO = 16f / 9f
const val POSTER_RATIO = 3f / 4f
