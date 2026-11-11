package com.twitchplayer.tv.core.designsystem.component

import com.twitchplayer.tv.core.designsystem.theme.TvDimens
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.twitchplayer.tv.core.designsystem.theme.TvColors
import com.twitchplayer.tv.core.designsystem.theme.TvShapes
import com.twitchplayer.tv.core.designsystem.theme.TvType
import com.twitchplayer.tv.core.ui.TvFocusable
import com.twitchplayer.tv.core.ui.focusFill

/** Short labelled button: one icon and one word. */
@Composable
fun TvButton(
    label: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    iconTint: androidx.compose.ui.graphics.Color = TvColors.Text,
) {
    TvFocusable(
        onClick = onClick,
        shape = TvShapes.Control,
        pressOverlay = if (primary) TvColors.PurplePressed else TvColors.PressTone,
        focusScale = TvDimens.FocusScaleControl,
        modifier = modifier.height(40.dp).background(if (primary) TvColors.Purple else TvColors.Alt2, TvShapes.Control),
    ) { focus ->
        Row(
            Modifier
                .fillMaxHeight()
                // The primary button also changes its own fill on focus (the ring alone is easy to miss on a purple button).
                .then(if (primary) Modifier.focusFill(focus, TvColors.PurpleFocused, TvShapes.Control) else Modifier)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(16.dp))
            Text(label, style = TvType.Title, color = TvColors.Text, maxLines = 1)
        }
    }
}
