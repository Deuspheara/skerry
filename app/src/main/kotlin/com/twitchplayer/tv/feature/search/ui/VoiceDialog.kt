package com.twitchplayer.tv.feature.search.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.twitchplayer.tv.R
import com.twitchplayer.tv.core.designsystem.component.LocalDialogClose
import com.twitchplayer.tv.core.designsystem.component.TvDialog
import com.twitchplayer.tv.core.designsystem.component.TvIconButton
import com.twitchplayer.tv.core.designsystem.icon.TvIcons
import com.twitchplayer.tv.core.designsystem.theme.LocalMotion
import com.twitchplayer.tv.core.designsystem.theme.TvColors
import com.twitchplayer.tv.core.designsystem.theme.TvType
import com.twitchplayer.tv.feature.search.presentation.VoiceState

private const val BAR_COUNT = 14

/** Listening / heard / not-recognized states. The close button holds focus; Back also cancels. */
@Composable
fun VoiceDialog(state: VoiceState, onCancel: () -> Unit) {
    val close = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { close.requestFocus() } }
    TvDialog(
        description = stringResource(R.string.voice_dialog),
        onDismiss = onCancel,
        width = 320.dp,
        scrim = Color.Black.copy(alpha = 0.5f),
    ) {
        Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) { Microphone(state is VoiceState.Listening) }
        Box(Modifier.height(40.dp), contentAlignment = Alignment.Center) {
            when (state) {
                VoiceState.Listening, VoiceState.Idle -> Bars(animated = true)

                is VoiceState.Heard -> Text(
                    state.text,
                    style = TvType.Title.copy(fontSize = androidx.compose.ui.unit.TextUnit(16f, androidx.compose.ui.unit.TextUnitType.Sp)),
                    color = TvColors.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.background(TvColors.Alt2, RoundedCornerShape(2.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
                )

                VoiceState.Failed -> Icon(TvIcons.MicOff, stringResource(R.string.voice_not_heard), tint = TvColors.TextSecondary, modifier = Modifier.size(32.dp))
            }
        }
        val dismiss = LocalDialogClose.current
        TvIconButton(
            TvIcons.Close,
            stringResource(R.string.voice_close),
            { dismiss(onCancel) },
            Modifier.focusRequester(close),
            size = 44.dp,
            iconSize = 22.dp,
            container = TvColors.Alt2,
        )
    }
}

@Composable
private fun Microphone(listening: Boolean) {
    val motion = LocalMotion.current
    val pulse = if (listening && motion.enabled) {
        rememberInfiniteTransition(label = "pulse").animateFloat(
            0f,
            1f,
            infiniteRepeatable(tween(1600), RepeatMode.Restart),
            label = "ring",
        )
    } else {
        null
    }
    Box(Modifier.size(80.dp), contentAlignment = Alignment.Center) {
        if (pulse != null) {
            Box(
                Modifier.matchParentSize().graphicsLayer {
                    val p = pulse.value
                    scaleX = 1f + 0.2f * p
                    scaleY = 1f + 0.2f * p
                    alpha = 0.35f * (1f - p)
                }.background(TvColors.Purple, CircleShape),
            )
        }
        Box(Modifier.matchParentSize().background(TvColors.Purple, CircleShape), contentAlignment = Alignment.Center) {
            Icon(TvIcons.Mic, null, tint = Color.White, modifier = Modifier.size(36.dp))
        }
    }
}

/** Equalizer bars: scaleY animates in the layer phase; centre bars are taller. */
@Composable
internal fun Bars(animated: Boolean) {
    val motion = LocalMotion.current
    Row(Modifier.fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(BAR_COUNT) { index ->
            val centre = 1f - kotlin.math.abs(index - (BAR_COUNT - 1) / 2f) / (BAR_COUNT / 2f)
            val amplitude = (0.3f + 0.7f * centre).coerceIn(0.3f, 1f)
            val scale = if (animated && motion.enabled) {
                rememberInfiniteTransition(label = "bar$index").animateFloat(
                    0.2f,
                    amplitude,
                    infiniteRepeatable(tween(600 + (index * 37) % 500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "scale",
                )
            } else {
                null
            }
            Box(
                Modifier.width(6.dp).fillMaxHeight()
                    .graphicsLayer {
                        val s = scale?.value ?: 0.5f
                        scaleY = s
                    }
                    .background(TvColors.Purple, RoundedCornerShape(2.dp)),
            )
        }
    }
}
