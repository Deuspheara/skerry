package com.twitchplayer.tv.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.twitchplayer.tv.core.designsystem.theme.TvColors
import com.twitchplayer.tv.core.designsystem.theme.TvType

/**
 * Empty / loading / error block: icon + title + at most one short line. When [action] is given the
 * button is the screen's default focus target so D-pad is never stranded on an empty screen.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    line: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = TvColors.TextSecondary, modifier = Modifier.size(64.dp))
        Text(title, style = TvType.EmptyTitle, color = TvColors.TextSecondary, textAlign = TextAlign.Center)
        if (line != null) Text(line, style = TvType.Meta, color = TvColors.TextSecondary, textAlign = TextAlign.Center)
        action?.invoke()
    }
}
