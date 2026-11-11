package com.twitchplayer.tv.feature.player.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text
import com.twitchplayer.tv.core.data.model.LiveStream
import com.twitchplayer.tv.core.designsystem.component.Avatar
import com.twitchplayer.tv.core.designsystem.component.LiveBadge
import com.twitchplayer.tv.core.designsystem.component.ViewerCount
import com.twitchplayer.tv.core.designsystem.theme.TvColors
import com.twitchplayer.tv.core.designsystem.theme.TvShapes
import com.twitchplayer.tv.core.designsystem.theme.TvType
import com.twitchplayer.tv.core.ui.TvFocusable
import com.twitchplayer.tv.core.ui.formatUptime
import kotlinx.coroutines.delay

private val Shadowed = Shadow(TvColors.Black80, Offset(0f, 1f), 3f)

/**
 * Stream info at the top of the bottom overlay: avatar, name, LIVE, uptime, viewers, then the stream title.
 * Focusable: OK opens the channel page. The uptime only ticks while [ticking] (the overlay is visible), so a hidden
 * overlay costs nothing during playback.
 */
@Composable
fun PlayerHeader(
    login: String,
    stream: LiveStream?,
    onOpenChannel: () -> Unit,
    modifier: Modifier = Modifier,
    ticking: Boolean = true,
) {
    val name = stream?.name ?: login
    TvFocusable(onClick = onOpenChannel, shape = TvShapes.Control, description = name, modifier = modifier) {
        Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(name, stream?.avatarUrl, size = 40.dp)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = TvType.PlayerName.copy(shadow = Shadowed), color = TvColors.Text, maxLines = 1)
                    if (stream != null) {
                        LiveBadge()
                        if (stream.startedAtMs > 0) Uptime(stream.startedAtMs, ticking)
                        ViewerCount(stream.viewers, color = TvColors.Text)
                    }
                }
                if (stream != null && stream.title.isNotBlank()) {
                    Text(
                        stream.title,
                        style = TvType.Meta.copy(shadow = Shadowed),
                        color = TvColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun Uptime(startedAtMs: Long, ticking: Boolean) {
    val now by produceState(System.currentTimeMillis(), ticking) {
        while (ticking) {
            value = System.currentTimeMillis()
            delay(1000 - value % 1000)
        }
    }
    // Clock glyph: says the number is the time live, not the time of day.
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        androidx.tv.material3.Icon(com.twitchplayer.tv.core.designsystem.icon.TvIcons.Clock, null, tint = TvColors.Text, modifier = Modifier.size(13.dp))
        Text(formatUptime(now - startedAtMs), style = TvType.MetaNumeric.copy(shadow = Shadowed), color = TvColors.Text, maxLines = 1)
    }
}
