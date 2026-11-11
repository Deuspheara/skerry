package com.twitchplayer.tv.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.twitchplayer.tv.core.designsystem.icon.TvIcons
import com.twitchplayer.tv.core.designsystem.theme.TvColors
import com.twitchplayer.tv.core.designsystem.theme.TvDimens
import com.twitchplayer.tv.core.designsystem.theme.TvShapes
import com.twitchplayer.tv.core.designsystem.theme.TvType
import com.twitchplayer.tv.core.ui.TvFocusable

/**
 * Text chip. Selected = purple fill + check; focus = the shared ring (selected is not focused).
 * [leadingIcon] replaces the check for non-selectable chips such as recent searches.
 */
@Composable
fun Chip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    leadingIcon: ImageVector? = null,
    compact: Boolean = false,
) {
    // Selected is a tonal purple fill + check, never an outline: only focus draws a ring.
    val fill = if (selected) {
        TvColors.Purple.copy(alpha = 0.34f).compositeOver(TvColors.Alt2)
    } else if (compact) {
        TvColors.KeyFill
    } else {
        TvColors.Alt2
    }
    val line = if (selected) {
        TvColors.Purple
    } else if (compact) {
        TvColors.KeyLine
    } else {
        TvColors.ChipLine
    }
    TvFocusable(
        onClick = onClick,
        shape = TvShapes.Chip,
        focusScale = TvDimens.FOCUS_SCALE_CONTROL,
        pressOverlay = TvColors.PressTone,
        role = Role.RadioButton,
        modifier = modifier
            .height(if (compact) 32.dp else 40.dp)
            .defaultMinSize(minWidth = if (compact) 0.dp else 68.dp)
            .background(fill, TvShapes.Chip)
            .border(0.5.dp, if (selected) TvColors.ChipLine else line, TvShapes.Chip)
            .semantics { this.selected = selected },
    ) {
        Row(
            Modifier.fillMaxHeight().padding(horizontal = if (compact) 12.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        ) {
            val icon = if (selected) TvIcons.Check else leadingIcon
            if (icon != null) Icon(icon, null, tint = if (selected) TvColors.Focus else TvColors.Text, modifier = Modifier.size(14.dp))
            Text(label, style = TvType.Label, color = TvColors.Text, maxLines = 1, softWrap = false)
        }
    }
}

/** Icon-only chip (e.g. sort). [description] is its accessible name. */
@Composable
fun IconChip(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TvFocusable(
        onClick = onClick,
        shape = TvShapes.Chip,
        focusScale = TvDimens.FOCUS_SCALE_CONTROL,
        pressOverlay = TvColors.PressTone,
        description = description,
        modifier = modifier
            .size(48.dp, 40.dp)
            .background(TvColors.Alt2, TvShapes.Chip)
            .border(0.5.dp, TvColors.ChipLine, TvShapes.Chip),
    ) {
        androidx.compose.foundation.layout.Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = TvColors.Text, modifier = Modifier.size(18.dp))
        }
    }
}
