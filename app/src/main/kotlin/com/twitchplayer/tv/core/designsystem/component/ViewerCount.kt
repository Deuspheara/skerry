package com.twitchplayer.tv.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.twitchplayer.tv.core.designsystem.icon.TvIcons
import com.twitchplayer.tv.core.designsystem.theme.TvColors
import com.twitchplayer.tv.core.designsystem.theme.TvType
import com.twitchplayer.tv.core.ui.formatViewers

/** Eye icon + compact viewer count. [overlay] adds the dark pill used on thumbnails. */
@Composable
fun ViewerCount(
    count: Int,
    modifier: Modifier = Modifier,
    overlay: Boolean = false,
    color: Color = TvColors.TextSecondary,
    iconSize: Dp = 13.dp,
) {
    val text = remember(count) { formatViewers(count) }
    Row(
        modifier = if (overlay) {
            modifier.background(TvColors.Black70, RoundedCornerShape(2.dp)).padding(horizontal = 5.dp, vertical = 2.dp)
        } else {
            modifier
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(TvIcons.Eye, contentDescription = null, tint = if (overlay) TvColors.Text else color, modifier = Modifier.size(iconSize))
        Text(
            text = text,
            style = TvType.MetaNumeric,
            color = if (overlay) TvColors.Text else color,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}
