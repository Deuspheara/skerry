package com.twitchplayer.tv.feature.search.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.twitchplayer.tv.R
import com.twitchplayer.tv.core.designsystem.icon.TvIcons
import com.twitchplayer.tv.core.designsystem.theme.TvColors
import com.twitchplayer.tv.core.designsystem.theme.TvShapes
import com.twitchplayer.tv.core.designsystem.theme.TvType
import com.twitchplayer.tv.core.ui.TvFocusable
import com.twitchplayer.tv.core.ui.focusFill

private val KEY = 48.dp
private val KEY_GAP = 6.dp
private val KEY_HEIGHT = 52.dp
/**
 * Letter rows for the app language: AZERTY for French, QWERTZ for German, QWERTY elsewhere with the language's most
 * common extra letters in the last row. Always Latin: Twitch logins are Latin and category names are mostly English;
 * other scripts type through voice or a hardware keyboard.
 */
internal fun keyboardRows(language: String): List<List<String>> {
    val digits = "1234567890".map(Char::toString)
    fun row(s: String) = s.map(Char::toString)
    return when (language) {
        "fr" -> listOf(digits, row("azertyuiop"), row("qsdfghjklm"), listOf("w", "x", "c", "v", "b", "n", "é", "è", "'", "-"))
        "de" -> listOf(digits, row("qwertzuiop"), row("asdfghjklö"), row("yxcvbnmäüß"))
        else -> {
            val extras = when (language) {
                "es" -> "ñ_-"
                "pt" -> "çãé"
                "it" -> "àèò"
                "sv", "fi" -> "åäö"
                "da", "nb", "no" -> "æøå"
                "pl" -> "łżś"
                "tr" -> "şğı"
                "cs" -> "čřž"
                "hu" -> "áéö"
                "ro" -> "ășț"
                else -> "_-'"
            }
            listOf(digits, row("qwertyuiop"), row("asdfghjkl") + "-", row("zxcvbnm") + row(extras))
        }
    }
}

private fun spanWidth(span: Int): Dp = KEY * span + KEY_GAP * (span - 1)

/** On-screen keyboard (10 columns, layout follows the app language): the fallback when voice or a hardware keyboard is not used. */
@Composable
fun Keyboard(
    onKey: (String) -> Unit,
    onSpace: () -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
    onVoice: () -> Unit,
    micModifier: Modifier,
    modifier: Modifier = Modifier,
    /** Attached to the first letter key: where focus starts. */
    firstKeyModifier: Modifier = Modifier,
    /** Non-null adds a Shift key (the chat composer): true = the next letter is a capital. */
    shift: Boolean? = null,
    onShift: () -> Unit = {},
) {
    val language = androidx.compose.ui.platform.LocalConfiguration.current.locales[0].language
    val rows = androidx.compose.runtime.remember(language) { keyboardRows(language) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(KEY_GAP)) {
        rows.forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(KEY_GAP)) {
                row.forEachIndexed { index, key ->
                    val shown = if (shift == true) key.uppercase() else key
                    KeyCap(label = shown, onClick = { onKey(shown) }, modifier = if (rowIndex == 1 && index == 0) firstKeyModifier else Modifier)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(KEY_GAP)) {
            if (shift == null) {
                IconKey(TvIcons.Space, stringResource(R.string.key_space), 4, onSpace)
                IconKey(TvIcons.Backspace, stringResource(R.string.key_delete), 2, onDelete)
                IconKey(TvIcons.Close, stringResource(R.string.key_clear), 2, onClear)
            } else {
                IconKey(TvIcons.Shift, stringResource(R.string.key_shift), 2, onShift, active = shift)
                IconKey(TvIcons.Space, stringResource(R.string.key_space), 3, onSpace)
                IconKey(TvIcons.Backspace, stringResource(R.string.key_delete), 2, onDelete)
                IconKey(TvIcons.Close, stringResource(R.string.key_clear), 1, onClear)
            }
            IconKey(TvIcons.Mic, stringResource(R.string.key_voice), 2, onVoice, voice = true, modifier = micModifier)
        }
    }
}

@Composable
private fun KeyCap(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TvFocusable(
        onClick = onClick,
        shape = TvShapes.Chip,
        description = label,
        modifier = modifier.size(KEY, KEY_HEIGHT).keyBackground(),
    ) { focus ->
        Box(Modifier.matchParentSize().focusFill(focus, TvColors.FocusedKey, TvShapes.Chip), contentAlignment = Alignment.Center) {
            Text(label, style = TvType.Key, color = TvColors.Text)
        }
    }
}

@Composable
private fun IconKey(
    icon: ImageVector,
    description: String,
    span: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    voice: Boolean = false,
    /** A toggled key (Shift on): purple glyph on the key's fill, never confused with focus. */
    active: Boolean = false,
) {
    TvFocusable(
        onClick = onClick,
        shape = TvShapes.Chip,
        description = description,
        modifier = modifier
            .size(spanWidth(span), KEY_HEIGHT)
            // Voice stands out by its purple glyph, not by an outline that would read as focus.
            .keyBackground(),
    ) { focus ->
        Box(
            Modifier.matchParentSize().focusFill(focus, if (voice) TvColors.Purple else TvColors.FocusedKey, TvShapes.Chip),
            contentAlignment = Alignment.Center,
        ) {
            if (voice) {
                Icon(icon, null, tint = TvColors.Tint, modifier = Modifier.size(26.dp).graphicsLayer { alpha = 1f - focus.value })
                Icon(icon, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(26.dp).graphicsLayer { alpha = focus.value })
            } else {
                Icon(icon, null, tint = if (active) TvColors.Purple else TvColors.Text, modifier = Modifier.size(22.dp))
            }
        }
    }
}



private fun Modifier.keyBackground(): Modifier =
    this.background(TvColors.KeyFill, TvShapes.Chip).border(0.5.dp, TvColors.KeyLine, TvShapes.Chip)
