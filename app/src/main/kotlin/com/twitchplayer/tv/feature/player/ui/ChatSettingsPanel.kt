package com.twitchplayer.tv.feature.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.twitchplayer.tv.R
import com.twitchplayer.tv.core.data.model.AnimatedEmoteLimit
import com.twitchplayer.tv.core.data.model.ChatDensity
import com.twitchplayer.tv.core.data.model.ChatMode
import com.twitchplayer.tv.core.data.model.ChatSettings
import com.twitchplayer.tv.core.data.model.ChatTextSize
import com.twitchplayer.tv.core.designsystem.icon.TvIcons
import com.twitchplayer.tv.core.designsystem.theme.TvColors
import com.twitchplayer.tv.core.designsystem.theme.TvType
import com.twitchplayer.tv.core.ui.TvFocusable
import com.twitchplayer.tv.feature.player.presentation.ChatLine
import com.twitchplayer.tv.feature.player.presentation.ChatLineKind
import com.twitchplayer.tv.feature.player.presentation.ChatPiece

/**
 * Chat options as one short list of rows (icon, name, value). Up / Down move between rows, Left / Right step the
 * value, OK steps it forward (toggles flip). Changes are saved at once and show in the one-line preview on top, and in the
 * chat itself when this panel sits in the player. Hosted in the chat column of the player and in a dialog in Settings.
 */
@Composable
fun ChatSettingsPanel(
    settings: ChatSettings,
    onChange: ((ChatSettings) -> ChatSettings) -> Unit,
    modifier: Modifier = Modifier,
    firstFocus: FocusRequester = remember { FocusRequester() },
) {
    val style = remember(settings) { settings.toStyle() }
    val preview = remember {
        ChatLine("preview", ChatLineKind.Normal, "", 0xFFB57BFF, emptyList(), listOf(ChatPiece.Text("")), "12:34")
    }
    val previewLine = preview.copy(nick = stringResource(R.string.chat_preview_nick), pieces = listOf(ChatPiece.Text(stringResource(R.string.chat_preview_text))))
    val noAnimation = remember { object : State<Int> { override val value = 0 } }
    val on = stringResource(R.string.opt_on); val off = stringResource(R.string.opt_off)
    fun flag(v: Boolean) = if (v) on else off

    Column(modifier.focusGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ChatLineView(previewLine, style, noAnimation, Modifier.padding(start = 8.dp, bottom = 2.dp))
        var first = true
        fun Modifier.initial(): Modifier = if (first) { first = false; focusRequester(firstFocus) } else this

        OptionRow(
            TvIcons.TextSize, stringResource(R.string.chat_opt_text),
            stringResource(when (settings.textSize) { ChatTextSize.Small -> R.string.opt_small; ChatTextSize.Medium -> R.string.opt_medium; ChatTextSize.Large -> R.string.opt_large }),
            onStep = { d -> onChange { it.copy(textSize = step(ChatTextSize.entries, it.textSize, d)) } }, modifier = Modifier.initial(),
        )
        OptionRow(
            TvIcons.SortPopular, stringResource(R.string.chat_opt_spacing),
            stringResource(when (settings.density) { ChatDensity.Compact -> R.string.opt_tight; ChatDensity.Standard -> R.string.opt_normal; ChatDensity.Relaxed -> R.string.opt_loose }),
            onStep = { d -> onChange { it.copy(density = step(ChatDensity.entries, it.density, d)) } }, modifier = Modifier.initial(),
        )
        OptionRow(
            TvIcons.Eye, stringResource(R.string.chat_opt_background), "${settings.backgroundOpacity}%",
            onStep = { d -> onChange { it.copy(backgroundOpacity = step(ChatSettings.BACKGROUND_OPACITIES, it.backgroundOpacity, d)) } }, modifier = Modifier.initial(),
        )
        OptionRow(TvIcons.Shield, stringResource(R.string.chat_opt_badges), flag(settings.showBadges), onStep = { onChange { it.copy(showBadges = !it.showBadges) } }, modifier = Modifier.initial())
        OptionRow(TvIcons.Clock, stringResource(R.string.chat_opt_time), flag(settings.showTimestamps), onStep = { onChange { it.copy(showTimestamps = !it.showTimestamps) } }, modifier = Modifier.initial())
        OptionRow(TvIcons.Sparkles, stringResource(R.string.chat_opt_animated), flag(settings.animatedEmotes), onStep = { onChange { it.copy(animatedEmotes = !it.animatedEmotes) } }, modifier = Modifier.initial())
        OptionRow(
            TvIcons.Equalizer, stringResource(R.string.chat_opt_limit), settings.animatedLimit.count.toString(), enabled = settings.animatedEmotes,
            onStep = { d -> onChange { it.copy(animatedLimit = step(AnimatedEmoteLimit.entries, it.animatedLimit, d)) } }, modifier = Modifier.initial(),
        )
    }
}

/** Next / previous entry, stopping at the ends for an explicit direction would trap the user, so it wraps. */
internal fun <T> step(values: List<T>, current: T, delta: Int): T =
    values[Math.floorMod(values.indexOf(current).coerceAtLeast(0) + delta, values.size)]

@Composable
private fun OptionRow(
    icon: ImageVector,
    label: String,
    value: String,
    onStep: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TvFocusable(
        onClick = { onStep(1) },
        shape = RoundedCornerShape(2.dp),
        description = "$label: $value",
        role = Role.Button,
        enabled = enabled,
        focusScale = 1f,
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp)
            .then(if (enabled) Modifier else Modifier.alpha(0.5f))
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> { onStep(-1); true }
                    Key.DirectionRight -> { onStep(1); true }
                    else -> false
                }
            },
    ) { focus ->
        Row(
            Modifier.fillMaxSize().background(TvColors.Tile, RoundedCornerShape(2.dp)).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = TvColors.TextSecondary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, style = TvType.Label, color = TvColors.Text, maxLines = 1, modifier = Modifier.weight(1f))
            // Arrows fade in with focus: Left / Right step the value.
            Icon(TvIcons.ChevronLeft, null, tint = TvColors.Text, modifier = Modifier.size(14.dp).graphicsLayer { alpha = focus.value })
            Text(value, style = TvType.Label, color = TvColors.TextSecondary, maxLines = 1, modifier = Modifier.padding(horizontal = 4.dp))
            Icon(TvIcons.ChevronRight, null, tint = TvColors.Text, modifier = Modifier.size(14.dp).graphicsLayer { alpha = focus.value })
        }
    }
}
