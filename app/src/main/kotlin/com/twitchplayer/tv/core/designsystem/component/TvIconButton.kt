package com.twitchplayer.tv.core.designsystem.component

import com.twitchplayer.tv.core.designsystem.theme.TvDimens
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.tv.material3.Icon
import com.twitchplayer.tv.core.designsystem.theme.TvColors
import com.twitchplayer.tv.core.ui.TvFocusable
import androidx.compose.foundation.layout.Box

/** Round icon-only button. Always needs a [description] (screen readers, no visible label). */
@Composable
fun TvIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    iconSize: Dp = 20.dp,
    container: Color = TvColors.Alt2,
    tint: Color = TvColors.Text,
    outlined: Boolean = false,
    selected: Boolean = false,
) {
    TvFocusable(
        onClick = onClick,
        shape = CircleShape,
        focusScale = TvDimens.FocusScaleControl,
        description = description,
        role = Role.Button,
        modifier = modifier
            .size(size)
            .background(if (selected) TvColors.Purple else if (outlined) Color.Transparent else container, CircleShape)
            .then(if (outlined && !selected) Modifier.border(1.dp, TvColors.TextSecondary, CircleShape) else Modifier),
    ) {
        Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
        }
    }
}
