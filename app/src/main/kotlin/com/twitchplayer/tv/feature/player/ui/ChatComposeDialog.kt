package com.twitchplayer.tv.feature.player.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.twitchplayer.tv.R
import com.twitchplayer.tv.core.designsystem.component.TvButton
import com.twitchplayer.tv.core.designsystem.component.TvDialog
import com.twitchplayer.tv.core.designsystem.icon.TvIcons
import com.twitchplayer.tv.core.designsystem.theme.TvColors
import com.twitchplayer.tv.core.designsystem.theme.TvShapes
import com.twitchplayer.tv.core.designsystem.theme.TvType
import com.twitchplayer.tv.feature.search.presentation.VoiceInput
import com.twitchplayer.tv.feature.search.ui.Keyboard
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** Twitch's limit for one chat message. */
internal const val MAX_CHAT_LENGTH = 500

/**
 * Writes a chat message with the on-screen keyboard (the layout follows the app language) or by voice. Send closes the
 * dialog; the player tells the viewer afterwards if Twitch did not post the message.
 */
@Composable
fun ChatComposeDialog(onSend: (String) -> Unit, onDismiss: () -> Unit) {
    val voice = koinInject<VoiceInput>()
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    // One-shot capital: the next letter is upper case, then back to lower case (like a phone keyboard).
    var shift by remember { mutableStateOf(false) }
    val firstKey = remember { FocusRequester() }
    val mic = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstKey.requestFocus() } }
    fun append(s: String) {
        if (text.length + s.length <= MAX_CHAT_LENGTH) text += s
    }
    val title = stringResource(R.string.chat_write)
    val sendable = text.isNotBlank()

    TvDialog(description = title, title = title, onDismiss = onDismiss, width = 580.dp, titleInset = 8.dp) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MessageField(text, listening)
            Keyboard(
                onKey = { key ->
                    append(key)
                    shift = false
                },
                onSpace = { if (text.isNotEmpty() && !text.endsWith(" ")) append(" ") },
                onDelete = { text = text.dropLast(1) },
                onClear = {
                    text = ""
                    shift = false
                },
                onVoice = {
                    if (!listening && voice.isAvailable()) {
                        scope.launch {
                            listening = true
                            try {
                                voice.listen()?.let { append(if (text.isEmpty()) it else " $it") }
                            } finally {
                                listening = false
                            }
                        }
                    }
                },
                micModifier = Modifier.focusRequester(mic),
                firstKeyModifier = Modifier.focusRequester(firstKey),
                shift = shift,
                onShift = { shift = !shift },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                // Purple only when there is something to send: an empty message has no primary action.
                TvButton(stringResource(R.string.chat_send), TvIcons.Send, onClick = { if (sendable) onSend(text) }, primary = sendable)
                // The counter is for the limit: silent until it is near.
                if (text.length >= MAX_CHAT_LENGTH - 100) {
                    Text("${text.length}/$MAX_CHAT_LENGTH", style = TvType.MetaNumeric, color = if (text.length >= MAX_CHAT_LENGTH) TvColors.Text else TvColors.TextSecondary)
                }
            }
        }
    }
}

/** The message so far: the end of it stays in view (what is being typed), with a caret; listening or the hint when empty. */
@Composable
private fun MessageField(text: String, listening: Boolean) {
    val hint = if (listening) stringResource(R.string.chat_listening) else stringResource(R.string.chat_write_hint)
    Row(
        Modifier.fillMaxWidth().height(44.dp).background(TvColors.Alt2, TvShapes.Control).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (listening) {
            // The same equalizer as the voice search: the microphone is open.
            Box(Modifier.height(22.dp).width(96.dp)) { com.twitchplayer.tv.feature.search.ui.Bars(animated = true) }
            Text(hint, style = TvType.Label, color = TvColors.TextSecondary, maxLines = 1, modifier = Modifier.padding(start = 12.dp))
        } else if (text.isEmpty()) {
            Caret()
            Text(hint, style = TvType.Label, color = TvColors.TextSecondary, maxLines = 1)
        } else {
            // A long message shows its last part, led by an ellipsis: the start is not what the viewer is working on.
            val shown = if (text.length > VISIBLE_CHARS) "…" + text.takeLast(VISIBLE_CHARS) else text
            Text(shown, style = TvType.Label.copy(fontSize = 16.sp), color = TvColors.Text, maxLines = 1)
            Caret()
        }
    }
}

@Composable
private fun Caret() {
    val blink = rememberInfiniteTransition(label = "caret").animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(530, easing = LinearEasing), RepeatMode.Reverse),
        label = "caret",
    )
    Box(Modifier.padding(start = 2.dp).width(2.dp).height(20.dp).graphicsLayer { alpha = blink.value }.background(TvColors.Purple))
}

private const val VISIBLE_CHARS = 56
