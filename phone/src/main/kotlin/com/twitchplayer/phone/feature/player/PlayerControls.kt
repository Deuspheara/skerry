package com.twitchplayer.phone.feature.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.twitchplayer.phone.R
import com.twitchplayer.phone.ui.components.PhoneIcons
import com.twitchplayer.phone.ui.theme.LocalReducedMotion
import com.twitchplayer.phone.ui.theme.PhoneColors

/** One icon button of the control layer: 48 dp target, white on the scrim. */
@Composable
fun ControlButton(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = PhoneColors.OnScrim) {
    IconButton(onClick = onClick, modifier = modifier) { Icon(icon, contentDescription = description, tint = tint) }
}

/** The big round play / pause button in the middle of the picture; a spinner while the player is buffering. */
@Composable
fun CenterPlayButton(playing: Boolean, buffering: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.size(CENTER_BUTTON), contentAlignment = Alignment.Center) {
        if (buffering && !playing) {
            CircularProgressIndicator(color = PhoneColors.OnScrim, modifier = Modifier.size(CENTER_BUTTON / 2))
        } else {
            IconButton(onClick = onClick, modifier = Modifier.size(CENTER_BUTTON).clip(CircleShape).background(PhoneColors.Scrim)) {
                Icon(
                    if (playing) PhoneIcons.Pause else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(if (playing) R.string.player_pause else R.string.player_play),
                    tint = PhoneColors.OnScrim,
                    modifier = Modifier.size(CENTER_BUTTON / 2),
                )
            }
        }
    }
}

/**
 * The control layer over the picture: a scrim, a top bar (back, title, extra actions), the centre ([center], normally play / pause)
 * and a bottom bar ([bottom]). It fades in and out ([visible]); with reduced motion it just appears.
 */
@Composable
fun PlayerControlsLayer(
    visible: Boolean,
    title: String,
    subtitle: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * The system areas (cutout, bars) the controls keep clear of. The scrim itself ignores them and always reaches every edge of the
     * picture; only the buttons and texts on it are inset.
     */
    contentInsets: WindowInsets = WindowInsets(0),
    /** The top-left button: an arrow back by default, a chevron down when it minimises the player. */
    backIcon: ImageVector? = null,
    backDescription: String? = null,
    topActions: @Composable RowScope.() -> Unit = {},
    center: @Composable () -> Unit = {},
    bottom: @Composable ColumnScope.() -> Unit = {},
) {
    val reduced = LocalReducedMotion.current
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = if (reduced) androidx.compose.animation.EnterTransition.None else fadeIn(),
        exit = if (reduced) androidx.compose.animation.ExitTransition.None else fadeOut(),
    ) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to PhoneColors.ControlsEdge,
                    CONTROLS_EDGE_STOP to PhoneColors.ControlsDim,
                    1f - CONTROLS_EDGE_STOP to PhoneColors.ControlsDim,
                    1f to PhoneColors.ControlsEdge,
                ),
            ),
        ) {
            Box(Modifier.fillMaxSize().windowInsetsPadding(contentInsets)) {
                Row(Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    ControlButton(backIcon ?: Icons.AutoMirrored.Filled.ArrowBack, backDescription ?: stringResource(R.string.player_back), onBack)
                    Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleSmall,
                            color = PhoneColors.OnScrim,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.semantics { heading() },
                        )
                        if (subtitle != null) {
                            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = PhoneColors.OnScrim.copy(alpha = SUBTITLE_ALPHA), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    topActions()
                }
                // A little above the middle: the bottom bar keeps its own space on a short (16:9 portrait) picture.
                Box(Modifier.align(Alignment.Center).offset(y = CENTER_LIFT)) { center() }
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 4.dp), verticalArrangement = Arrangement.Bottom) { bottom() }
            }
        }
    }
}

private val CENTER_BUTTON = 64.dp
private val CENTER_LIFT = (-12).dp
private const val CONTROLS_EDGE_STOP = 0.4f
private const val SUBTITLE_ALPHA = 0.8f
