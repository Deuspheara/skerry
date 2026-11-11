package com.twitchplayer.tv.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.twitchplayer.tv.core.designsystem.theme.TvColors
import com.twitchplayer.tv.core.designsystem.theme.TvShapes
import com.twitchplayer.tv.core.designsystem.theme.TvType
import com.twitchplayer.tv.core.ui.TvFocusable
import com.twitchplayer.tv.core.ui.focusFill

/** Settings tile: big icon, one-word label and a state slot (chevron, switch, segmented control). */
@Composable
fun TileCard(
    icon: ImageVector,
    label: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color = Color.White,
    width: androidx.compose.ui.unit.Dp = 280.dp,
    state: @Composable () -> Unit,
) {
    TvFocusable(
        onClick = onClick,
        shape = TvShapes.Control,
        description = description,
        modifier = modifier
            .width(width)
            .height(156.dp)
            .background(TvColors.Tile, TvShapes.Control)
            .border(0.5.dp, TvColors.Hair, TvShapes.Control),
    ) { focus ->
        Box(
            Modifier.fillMaxSize().focusFill(focus, TvColors.TileFocused, TvShapes.Control).padding(top = 18.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(32.dp))
                Text(label, style = TvType.Tile, color = TvColors.Text, maxLines = 1)
                Box(Modifier.height(28.dp), contentAlignment = Alignment.Center) { state() }
            }
        }
    }
}
