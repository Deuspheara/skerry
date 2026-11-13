package com.twitchplayer.phone.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.twitchplayer.phone.R
import com.twitchplayer.phone.ui.components.PhoneIcons

private const val SUBTITLE_MAX_LINES = 1
private const val HAIRLINE_DP = 1
private const val SUBTITLE_MAX_FONT_SCALE = 1.15f
private val BUTTON_GAP = 12.dp

/** What sits beside the small picture while the sheet is minimised: what plays, a play / pause button and the close button. A tap anywhere else expands. */
@Composable
fun PlayerStrip(
    title: String,
    subtitle: String?,
    playing: Boolean,
    onTogglePlay: () -> Unit,
    onExpand: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    showPlay: Boolean = true,
) {
    val expand = stringResource(R.string.phone_player_expand)
    val hairline = MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier.fillMaxSize()
            // A hairline on top: on the light theme the strip and the bar under it are almost the same tone.
            .drawBehind { drawLine(hairline, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = HAIRLINE_DP.dp.toPx()) }
            .clickable(onClickLabel = expand, role = Role.Button, onClick = onExpand).padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // The strip stays a thin bar at large text sizes: it keeps the title (one line) and drops the subtitle, rather than growing over the list.
            if (subtitle != null && LocalDensity.current.fontScale <= SUBTITLE_MAX_FONT_SCALE) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = SUBTITLE_MAX_LINES, overflow = TextOverflow.Ellipsis)
            }
        }
        if (showPlay) {
            IconButton(onClick = onTogglePlay) {
                Icon(
                    if (playing) PhoneIcons.Pause else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(if (playing) R.string.player_pause else R.string.player_play),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        // Room between pause and close: a slip of the thumb must not end the stream.
        Spacer(Modifier.width(BUTTON_GAP))
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.phone_player_close), tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** What a replay or clip shows as the strip before it has a picture (still resolving, or failed): its title and the close button. */
@Composable
fun PendingStrip(title: String, sheet: PlayerSheet, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        PlayerStrip(title, null, playing = false, onTogglePlay = {}, onExpand = sheet.expand, onClose = sheet.close, showPlay = false)
    }
}
