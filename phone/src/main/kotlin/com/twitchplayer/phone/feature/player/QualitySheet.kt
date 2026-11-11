package com.twitchplayer.phone.feature.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.twitchplayer.phone.R
import com.twitchplayer.tv.feature.player.playback.VideoQualities

/** The list of video qualities in a bottom sheet; the one in use is ticked. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QualitySheet(qualities: VideoQualities, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                stringResource(R.string.player_quality_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            if (qualities.options.isEmpty()) {
                Text(
                    stringResource(R.string.player_quality_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
            } else {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    qualities.options.forEach { option ->
                        val selected = option.id == qualities.selected
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .selectable(selected = selected, role = Role.RadioButton, onClick = { onPick(option.id) })
                                .padding(horizontal = 24.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(option.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}
