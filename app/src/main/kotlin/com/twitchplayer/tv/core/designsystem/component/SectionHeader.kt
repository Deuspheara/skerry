package com.twitchplayer.tv.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.twitchplayer.tv.core.designsystem.theme.TvType
import com.twitchplayer.tv.core.designsystem.theme.TvColors

/** Section title: icon plus a short label, so a row is identifiable at a glance from the couch. */
@Composable
fun SectionHeader(
    icon: ImageVector,
    description: String,
    modifier: Modifier = Modifier,
    iconSize: androidx.compose.ui.unit.Dp = 20.dp,
    /** Live channels in the section: a red dot and the number after the title (no words, no translation needed). */
    liveCount: Int? = null,
) {
    Row(
        modifier.fillMaxWidth().height(26.dp).semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = TvColors.IconHigh, modifier = Modifier.size(iconSize))
        Text(description, style = TvType.Section, color = TvColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (liveCount != null && liveCount > 0) {
            Row(
                Modifier.padding(start = 4.dp).height(22.dp).background(TvColors.Alt2, androidx.compose.foundation.shape.RoundedCornerShape(11.dp)).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.size(7.dp).background(TvColors.Live, androidx.compose.foundation.shape.CircleShape))
                Text(liveCount.toString(), style = TvType.MetaNumeric, color = TvColors.Text, maxLines = 1)
            }
        }
    }
}
