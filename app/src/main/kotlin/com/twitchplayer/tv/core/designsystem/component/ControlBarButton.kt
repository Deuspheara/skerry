package com.twitchplayer.tv.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.tv.material3.Text
import com.twitchplayer.tv.core.designsystem.theme.TvType
import androidx.tv.material3.Icon
import com.twitchplayer.tv.core.designsystem.theme.TvColors
import com.twitchplayer.tv.core.designsystem.theme.TvDimens
import com.twitchplayer.tv.core.ui.TvFocusable

/**
 * Player control-bar button (round, 40 dp). Focus fills it purple (plus the shared ring); the
 * caption pill is drawn by the bar so buttons never move. Disabled stays focusable (D-pad never skips a
 * slot) but is dimmed with a struck-through glyph, and its focus state is quieter: no fill, no scale, a dimmed ring.
 * Dimming is done with colours, never `Modifier.alpha`: an alpha layer is an offscreen buffer the size of the button,
 * which clips the ring and the caption drawn outside it.
 */
@Composable
fun ControlBarButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
) {
    val contentAlpha = if (enabled) 1f else DISABLED_ALPHA
    TvFocusable(
        onClick = { if (enabled) onClick() },
        shape = CircleShape,
        description = description,
        onLongClick = if (enabled) onLongClick else null,
        focusScale = if (enabled) TvDimens.FocusScaleControl else 1f,
        ringStrength = if (enabled) 1f else DISABLED_ALPHA,
        modifier = modifier
            .size(TvDimens.ControlButton)
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .then(if (enabled) Modifier else Modifier.semantics { disabled() }),
    ) { focus ->
        Box(
            Modifier
                .matchParentSize()
                .background(TvColors.Black60.copy(alpha = TvColors.Black60.alpha * contentAlpha), CircleShape)
                .drawBehind {
                    if (enabled && focus.value > 0f) drawCircle(TvColors.Purple, alpha = focus.value)
                }
                // Disabled: no fill edge (dashed look) and a dimmed glyph; its focus caption still names it.
                .then(if (enabled) Modifier.border(0.5.dp, TvColors.ChipLine, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = TvColors.Text.copy(alpha = contentAlpha), modifier = Modifier.size(TvDimens.ControlIcon))
        }
        // Caption pill below the button: fades in with focus, never affects layout.
        Text(
            text = description,
            style = TvType.ControlLabel,
            color = TvColors.Text.copy(alpha = if (enabled) 1f else 0.7f),
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .wrapContentSize(Alignment.TopCenter, unbounded = true)
                .offset(y = TvDimens.ControlButton + 6.dp)
                .graphicsLayer { alpha = focus.value }
                .background(TvColors.Black80, RoundedCornerShape(11.dp))
                .border(0.5.dp, TvColors.Hair, RoundedCornerShape(11.dp))
                .padding(horizontal = 12.dp),
        )
    }
}

private const val DISABLED_ALPHA = 0.35f
