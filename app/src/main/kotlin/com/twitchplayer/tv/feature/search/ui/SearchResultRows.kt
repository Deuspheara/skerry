package com.twitchplayer.tv.feature.search.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.twitchplayer.tv.R
import com.twitchplayer.tv.core.data.model.Category
import com.twitchplayer.tv.core.data.model.ChannelRef
import com.twitchplayer.tv.core.designsystem.component.Avatar
import com.twitchplayer.tv.core.designsystem.component.LiveBadge
import com.twitchplayer.tv.core.designsystem.component.ViewerCount
import com.twitchplayer.tv.core.designsystem.icon.TvIcons
import com.twitchplayer.tv.core.designsystem.theme.TvColors
import com.twitchplayer.tv.core.designsystem.theme.TvShapes
import com.twitchplayer.tv.core.designsystem.theme.TvType
import com.twitchplayer.tv.core.ui.TvAsyncImage
import com.twitchplayer.tv.core.ui.TvFocusable
import com.twitchplayer.tv.core.ui.focusFill

/** One search hit: avatar, name, optional LIVE tag + viewers, chevron revealed on focus. */
@Composable
fun ChannelResultRow(channel: ChannelRef, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ResultRow(
        description = stringResource(if (channel.isLive) R.string.cd_live_channel else R.string.cd_offline, channel.name),
        onClick = onClick,
        modifier = modifier,
        lead = { Box(Modifier.graphicsLayer { alpha = if (channel.isLive) 1f else 0.55f }) { Avatar(channel.name, channel.avatarUrl, size = 36.dp) } },
        name = channel.name,
        live = channel.isLive,
        viewers = channel.viewers.takeIf { channel.isLive && it > 0 },
        offline = !channel.isLive,
    )
}

@Composable
fun CategoryResultRow(category: Category, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ResultRow(
        description = category.name,
        onClick = onClick,
        modifier = modifier,
        lead = { TvAsyncImage(category.artUrl, null, 30.dp, 40.dp, Modifier.size(30.dp, 40.dp).clip(TvShapes.Card)) },
        name = category.name,
        live = false,
        viewers = null,
    )
}

@Composable
private fun ResultRow(
    description: String,
    onClick: () -> Unit,
    modifier: Modifier,
    lead: @Composable () -> Unit,
    name: String,
    live: Boolean,
    viewers: Int?,
    offline: Boolean = false,
) {
    TvFocusable(
        onClick = onClick,
        shape = TvShapes.Chip,
        description = description,
        focusScale = 1.02f,
        modifier = modifier.fillMaxWidth().height(48.dp).background(TvColors.Base, TvShapes.Chip),
    ) { focus ->
        Row(
            Modifier.matchParentSize().focusFill(focus, TvColors.Alt2, TvShapes.Chip).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            lead()
            Text(name, style = TvType.Title, color = if (offline) TvColors.TextSecondary else TvColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (offline) {
                // Offline in words (same width as LIVE + viewers), so live and offline never differ by a badge's absence alone.
                Text(stringResource(R.string.sec_offline), style = TvType.Meta, color = TvColors.TextSecondary, maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.width(108.dp))
            } else {
                Row(Modifier.width(108.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(44.dp)) { if (live) LiveBadge() }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { if (viewers != null) ViewerCount(viewers) }
                }
            }
            Icon(
                TvIcons.ChevronRight,
                null,
                tint = TvColors.Text,
                modifier = Modifier.size(18.dp).graphicsLayer { alpha = focus.value },
            )
        }
    }
}
