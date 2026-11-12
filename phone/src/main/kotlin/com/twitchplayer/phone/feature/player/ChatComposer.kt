package com.twitchplayer.phone.feature.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.twitchplayer.tv.feature.player.data.emotes.Emote
import com.twitchplayer.tv.feature.player.presentation.ChatLine
import com.twitchplayer.tv.feature.player.presentation.EmotePickerState
import com.twitchplayer.tv.feature.player.presentation.chatterSuggestions
import com.twitchplayer.tv.feature.player.presentation.completeEmote
import com.twitchplayer.tv.feature.player.presentation.completeMention
import com.twitchplayer.tv.feature.player.presentation.emoteQueryAt
import com.twitchplayer.tv.feature.player.presentation.insertEmote
import com.twitchplayer.tv.feature.player.presentation.mentionQueryAt
import com.twitchplayer.tv.feature.player.presentation.replyPrefix
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Everything under the chat messages that the viewer writes with: the `:name` suggestions, the message pill and the emote picker that
 * takes the keyboard's place (the keyboard goes away, the panel takes its height, so the chat above does not jump; the pill's emote
 * button, a tap on the field or Back brings the keyboard back). A picked or suggested emote goes in at the cursor and into the viewer's
 * recents; sending is the plain text, so a name only turns into a picture for those who see it with the same provider.
 *
 * [handleInsets] is true where the composer sits at the bottom of the screen (portrait: it clears the navigation bar and rides on the
 * keyboard); false inside a column that already does (the landscape side panel), where the panel also gets a fixed [sidePanelHeight].
 */
@Composable
fun ChatComposer(
    onSend: (String) -> Unit,
    picker: EmotePickerState,
    animateEmotes: Boolean,
    onEmotePick: (String) -> Unit,
    modifier: Modifier = Modifier,
    handleInsets: Boolean = true,
    sidePanelHeight: Dp = SIDE_PANEL_HEIGHT,
    /** The line being answered (shown above the box, the message starts with its author's mention), or null. */
    replyTo: ChatLine? = null,
    onClearReply: () -> Unit = {},
    /** The chat so far and the viewer's own login: `@name` suggestions come from who wrote here. */
    lines: ImmutableList<ChatLine> = persistentListOf(),
    selfLogin: String? = null,
) {
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    var emotesOpen by rememberSaveable { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val source = remember { MutableInteractionSource() }

    // The panel is as tall as the keyboard was the last time it was up, so opening the picker does not move the chat.
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val navBottom = WindowInsets.navigationBars.getBottom(density)
    var keyboardPx by rememberSaveable { mutableIntStateOf(0) }
    SideEffect {
        if (imeBottom > navBottom) keyboardPx = imeBottom - navBottom
    }
    val panelHeight = if (handleInsets) EmotePanelSpec.panelHeight(keyboardPx.takeIf { it > 0 }?.let { with(density) { it.toDp() } }) else sidePanelHeight

    fun showKeyboard() {
        emotesOpen = false
        focus.requestFocus()
        keyboard?.show()
    }

    fun toggleEmotes() {
        if (emotesOpen) {
            showKeyboard()
        } else {
            emotesOpen = true
            focus.requestFocus()
            keyboard?.hide()
        }
    }
    // Touching the field while the picker is up means "type": the keyboard comes back in the panel's place.
    LaunchedEffect(source) {
        source.interactions.collect { if (it is PressInteraction.Press && emotesOpen) showKeyboard() }
    }
    BackHandler(enabled = emotesOpen) { emotesOpen = false }

    // Answering a line puts its author's mention in the box and brings the keyboard up, once per answered line.
    DisposableEffect(replyTo?.id) {
        replyTo?.let { target ->
            val prefix = replyPrefix(target.login)
            if (!field.text.startsWith(prefix)) {
                val text = (prefix + field.text.trimStart()).take(ChatInputSpec.MAX_MESSAGE)
                field = TextFieldValue(text, TextRange(text.length))
            }
            emotesOpen = false
            focus.requestFocus()
            keyboard?.show()
        }
        onDispose { }
    }
    val mention = remember(field.text, field.selection) { if (field.selection.collapsed) mentionQueryAt(field.text, field.selection.end) else null }
    val chatters = remember(mention?.name, lines.size, selfLogin) { mention?.let { chatterSuggestions(lines, it.name, selfLogin) }.orEmpty() }

    val query = remember(field.text, field.selection) { if (field.selection.collapsed) emoteQueryAt(field.text, field.selection.end) else null }
    val suggestions = remember(query?.name, picker) { query?.let { picker.search(it.name, EmotePanelSpec.SUGGESTIONS) }.orEmpty() }

    fun put(text: String, caret: Int, name: String) {
        if (text.length > ChatInputSpec.MAX_MESSAGE) return
        field = TextFieldValue(text, TextRange(caret))
        onEmotePick(name)
    }

    val send = {
        ChatInputSpec.outgoing(field.text)?.let { message ->
            onSend(message)
            field = TextFieldValue()
            if (replyTo != null) onClearReply()
        }
        Unit
    }
    val insets = if (!handleInsets) {
        Modifier
    } else if (emotesOpen) {
        Modifier.navigationBarsPadding()
    } else {
        Modifier.navigationBarsPadding().imePadding()
    }
    Column(modifier.then(insets)) {
        replyTo?.let { ReplyBar(it, onClearReply) }
        MentionSuggestionStrip(chatters, onPick = { chatter -> mention?.let { q -> completeMention(field.text, q, chatter.login).let { (text, caret) -> field = TextFieldValue(text, TextRange(caret)) } } })
        EmoteSuggestionStrip(suggestions, onPick = { emote: Emote -> query?.let { q -> completeEmote(field.text, q, emote.name).let { (text, caret) -> put(text, caret, emote.name) } } })
        ChatInputRow(
            value = field,
            onValueChange = { field = it },
            onSend = send,
            emotesOpen = emotesOpen,
            onToggleEmotes = ::toggleEmotes,
            interactionSource = source,
            focusRequester = focus,
        )
        if (emotesOpen) {
            EmotePanel(
                picker = picker,
                animate = animateEmotes,
                onPick = { emote -> insertEmote(field.text, field.selection.start, field.selection.end, emote.name).let { (text, caret) -> put(text, caret, emote.name) } },
                modifier = Modifier.height(panelHeight),
            )
        }
    }
}

private val SIDE_PANEL_HEIGHT = 220.dp
