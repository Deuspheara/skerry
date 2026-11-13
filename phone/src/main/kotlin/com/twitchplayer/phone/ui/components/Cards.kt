package com.twitchplayer.phone.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDirection
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
 * title (up to two lines), so six or seven streams fit on a screen instead of two. From [MULTI_COLUMN_WIDTH_DP] the grid has several columns and each
 * card is a **vertical card** (thumbnail over text). Compact titles take two lines (the row is as tall as the thumbnail), the vertical card's one; category names are one line.
 */
@Immutable
data class StreamLayout(val compact: Boolean) {
    companion object {
        val Compact = StreamLayout(compact = true)
        val Wide = StreamLayout(compact = false)
        val WideSingleColumn = Wide
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
 * channel's own page, where the header already says whose stream it is. [showLive] is false in a list where every stream is live: the red
 * badge would carry no information there (the viewer count with its dot still says it).
 */
@Composable
fun StreamCard(stream: LiveStream, onClick: () -> Unit, modifier: Modifier = Modifier, layout: StreamLayout = StreamLayout.WideSingleColumn, showChannel: Boolean = true, showLive: Boolean = true) {
    val viewers = remember(stream.viewers) { formatViewers(stream.viewers) }
    val description = stringResource(R.string.cd_stream, stream.name, viewers, stream.title)
    val rowModifier = modifier.clip(CardShape).clickableItem(onClick).semantics(mergeDescendants = true) { contentDescription = description }
    if (layout.compact) {
        // The row is as tall as the thumbnail (taller only when a large system font needs it): the title on one line, then the streamer's
        // picture beside the name and the category, the block centred against the picture.
        Row(rowModifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Thumbnail(stream, showLive, Modifier.width(CardSpec.COMPACT_THUMBNAIL_WIDTH))
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
                Text(stream.title, style = MaterialTheme.typography.titleSmall.copy(textDirection = TextDirection.Content), maxLines = CardSpec.COMPACT_TITLE_LINES, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (showChannel) Avatar(stream.avatarUrl, stream.name, CardSpec.COMPACT_AVATAR)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (showChannel) {
                            Text(stream.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(stream.categoryName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    } else {
        Column(rowModifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Thumbnail(stream, showLive, Modifier.fillMaxWidth())
            Row(Modifier.padding(horizontal = 2.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                if (showChannel) Avatar(stream.avatarUrl, stream.name, AvatarSmall)
                Column(Modifier.weight(1f)) {
                    Text(stream.title, style = MaterialTheme.typography.titleSmall.copy(textDirection = TextDirection.Content), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
private fun Thumbnail(stream: LiveStream, showLive: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.aspectRatio(THUMBNAIL_RATIO).clip(ThumbnailShape)) {
        RemoteImage(stream.thumbnailUrl, Modifier.matchParentSize())
        if (showLive) LiveBadge(Modifier.align(Alignment.TopStart).padding(6.dp))
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
            // One line, ellipsized ("World of Warcraft" is cut, never wrapped): every card has the same height without reserving a second line.
            maxLines = CardSpec.CATEGORY_NAME_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

/**
 * A channel row (offline follows, similar channels): avatar, name and its state on one line. A live channel says "Live · 12K viewers" with a
 * red dot; an offline one says when it was last seen live, or nothing ([showOfflineLabel] false: a list that is headed "Offline" does not repeat
 * it on every row). Offline avatars are dimmed. The row has no inset of its own: it aligns with the page's gutter like every other row.
 */
@Composable
fun ChannelListItem(channel: ChannelRef, onClick: () -> Unit, modifier: Modifier = Modifier, showOfflineLabel: Boolean = true) {
    val viewers = remember(channel.viewers) { formatViewers(channel.viewers) }
    val description = if (channel.isLive) stringResource(R.string.cd_live_channel, channel.name) else stringResource(R.string.cd_offline, channel.name)
    Row(
        modifier.fillMaxWidth().clip(CardShape).clickableItem(onClick).padding(vertical = 6.dp).semantics(mergeDescendants = true) { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(channel.avatarUrl, channel.name, AvatarSmall, Modifier.alpha(if (channel.isLive) 1f else OFFLINE_AVATAR_ALPHA))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(channel.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (channel.isLive) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(PhoneColors.Live))
                    Text(stringResource(R.string.phone_channel_live_viewers, viewers), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                val lastSeen = rememberLastSeenText(channel.lastSeenLiveMs) ?: if (showOfflineLabel) stringResource(R.string.phone_channel_offline) else null
                if (lastSeen != null) {
                    Text(lastSeen, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private const val OFFLINE_AVATAR_ALPHA = 0.6f

/** From this width the stream grid has two or more columns. */
private const val MULTI_COLUMN_WIDTH_DP = 600

const val THUMBNAIL_RATIO = 16f / 9f
const val POSTER_RATIO = 3f / 4f

/** Constants that keep cards level; a plain object so a JVM test can pin them. */
object CardSpec {
    /** Lines a category name may take: one, ellipsized. No second line is reserved. */
    const val CATEGORY_NAME_LINES = 1

    /** Lines a title may take in a compact row: two (the row is as tall as the thumbnail, which holds them). */
    const val COMPACT_TITLE_LINES = 2

    /** The streamer's picture beside the name in a compact row. */
    val COMPACT_AVATAR = 32.dp
    val COMPACT_THUMBNAIL_WIDTH = 168.dp

    /** Height of a compact stream row at the default font size: the thumbnail's. */
    val compactRowHeight = COMPACT_THUMBNAIL_WIDTH / THUMBNAIL_RATIO
}

/** "Last seen live 3 days ago" for an offline channel the app has seen live before; null when it never has. */
@Composable
fun rememberLastSeenText(lastSeenMs: Long): String? {
    if (lastSeenMs <= 0L) return null
    val ago = remember(lastSeenMs) {
        android.text.format.DateUtils.getRelativeTimeSpanString(lastSeenMs, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS).toString()
    }
    return stringResource(R.string.phone_following_last_live, ago)
}
