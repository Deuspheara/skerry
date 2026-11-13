package com.twitchplayer.phone.feature.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.twitchplayer.phone.R

/** One line of the player's settings: what it is, its current value (when it has one) and what a tap opens. */
@Immutable
class PlayerSettingRow(val icon: ImageVector, val label: String, val value: String?, val onClick: () -> Unit)

/**
 * The one place for the choices that are not worth a button over the picture each: quality, sleep timer. The picture's bar keeps the
 * sound, this gear and fullscreen, so the controls stay few and equal in weight. Rows are 56 dp and say their current value.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSettingsSheet(rows: List<PlayerSettingRow>, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(stringResource(R.string.phone_player_settings), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            rows.forEach { row ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = ROW_HEIGHT).clickable(role = Role.Button, onClick = row.onClick).padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(row.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                    Text(row.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(start = 16.dp))
                    if (row.value != null) {
                        Text(row.value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private val ROW_HEIGHT = 56.dp
