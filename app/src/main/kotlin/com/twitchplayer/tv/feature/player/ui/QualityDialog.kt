package com.twitchplayer.tv.feature.player.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.twitchplayer.tv.R
import com.twitchplayer.tv.core.designsystem.component.LocalDialogClose
import com.twitchplayer.tv.core.designsystem.component.TvButton
import com.twitchplayer.tv.core.designsystem.component.TvDialog
import com.twitchplayer.tv.core.designsystem.icon.TvIcons
import com.twitchplayer.tv.core.designsystem.theme.TvColors
import com.twitchplayer.tv.core.designsystem.theme.TvType
import com.twitchplayer.tv.feature.player.playback.VideoQualities

/**
 * Picker for the video quality (1080p60, 720p, ... as offered by Twitch for this stream). Focus starts on the
 * quality in use; Back closes the dialog. The choice is stored by the hosted page and kept for later streams.
 */
@Composable
fun QualityDialog(qualities: VideoQualities, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val selectedFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { selectedFocus.requestFocus() } }
    val title = stringResource(R.string.player_quality_title)
    TvDialog(description = title, onDismiss = onDismiss, width = 300.dp) {
        Text(title, style = TvType.Title, color = TvColors.TextSecondary)
        val close = LocalDialogClose.current
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = 300.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(qualities.options, key = { it.id }) { option ->
                val selected = option.id == qualities.selected
                TvButton(
                    label = option.label,
                    icon = if (selected) TvIcons.Check else TvIcons.Quality,
                    onClick = { close { onPick(option.id) } },
                    modifier = Modifier.fillMaxWidth().then(if (selected) Modifier.focusRequester(selectedFocus) else Modifier),
                    primary = selected,
                )
            }
        }
    }
}
