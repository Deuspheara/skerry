package com.twitchplayer.phone.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.twitchplayer.phone.R
import com.twitchplayer.tv.feature.player.presentation.ChatLine
import com.twitchplayer.tv.feature.player.ui.animationAllowance
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The chat messages, newest at the bottom. It follows the newest line; when the viewer scrolls up it stops following and a pill offers
 * to jump back to live (with the number of lines that arrived since). Animated emotes play only on the newest visible lines, up to the
 * viewer's limit, and never while the list scrolls, the app is in the background or the picture is a Picture-in-Picture window.
 */
@Composable
fun PhoneChatList(
    lines: ImmutableList<ChatLine>,
    style: PhoneChatStyle,
    overVideo: Boolean,
    modifier: Modifier = Modifier,
    animationsAllowed: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(vertical = 4.dp),
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Index 0 is the newest line: a reversed layout keeps the list glued to the bottom while lines are added above it.
    val newestFirst = remember(lines) { lines.asReversed() }
    val currentLines by rememberUpdatedState(lines)
    var follow by rememberSaveable { mutableStateOf(true) }
    var anchorId by rememberSaveable { mutableStateOf<String?>(null) }
    val atNewest by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 } }

    // Only a gesture decides whether the viewer is following: the list moving because a line was added never counts as scrolling away.
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to atNewest }.filter { !it.first }.map { it.second }.collect { newest ->
            follow = newest
            anchorId = if (newest) null else anchorId ?: currentLines.lastOrNull()?.id
        }
    }
    val newestId = lines.lastOrNull()?.id
    LaunchedEffect(newestId) { if (follow) listState.scrollToItem(0) }

    val started = LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val animationsOn = animationsAllowed && style.animatedEmotes && started.value.isAtLeast(Lifecycle.State.STARTED) && !listState.isScrollInProgress
    val byId = remember(lines) { lines.associateBy { it.id } }
    val allowance by remember(style.animatedLimit, animationsOn) {
        derivedStateOf {
            if (!animationsOn) {
                emptyMap()
            } else {
                // Visible items are listed newest first (index 0 is the newest).
                val visible = listState.layoutInfo.visibleItemsInfo.mapNotNull { byId[it.key] }
                animationAllowance(visible, style.animatedLimit)
            }
        }
    }

    Box(modifier) {
        LazyColumn(
            state = listState,
            reverseLayout = true,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(style.gapDp.dp),
            modifier = Modifier.fillMaxSize().fadeTopEdge(TOP_FADE),
        ) {
            items(newestFirst, key = { it.id }) { line ->
                val animated = remember(line.id) { derivedStateOf { allowance[line.id] ?: 0 } }
                PhoneChatLine(line, style, remember(animated) { { animated.value } }, overVideo)
            }
        }
        val unseen = ChatScrollPolicy.unseen(lines.map { it.id }, anchorId)
        if (ChatScrollPolicy.showJumpPill(atNewest = follow, unseen = unseen)) {
            val description = pluralStringResource(R.plurals.chat_new_messages, unseen.coerceAtMost(MAX_PLURAL), unseen.coerceAtMost(MAX_PLURAL))
            ElevatedAssistChip(
                onClick = {
                    follow = true
                    anchorId = null
                    scope.launch { listState.animateScrollToItem(0) }
                },
                label = { Text(stringResource(R.string.chat_back_to_live) + " · " + ChatScrollPolicy.badge(unseen)) },
                // Opaque: it floats over chat lines, which must not show through the label.
                colors = AssistChipDefaults.elevatedAssistChipColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp).semantics { contentDescription = description },
            )
        }
    }
}

/**
 * The message box under the chat: a single pill (40 dp for one line, up to three lines) like the live apps use, not an outlined
 * field. The send button is a circle that only appears once there is something to send. Send on the keyboard's action and on the
 * button; it clears after sending. The row is [ChatInputSpec.ROW_HEIGHT] tall whether or not the button is shown, so nothing jumps.
 */
@Composable
fun ChatInputRow(onSend: (String) -> Unit, modifier: Modifier = Modifier) {
    var text by rememberSaveable { mutableStateOf("") }
    val send: () -> Unit = {
        ChatInputSpec.outgoing(text)?.let { message ->
            onSend(message)
            text = ""
        }
    }
    val canSend = ChatInputSpec.canSend(text)
    // The pill has no label of its own: TalkBack reads this (the hint text inside is only drawn).
    val hint = stringResource(R.string.phone_player_chat_hint)
    Row(
        modifier.fillMaxWidth().heightIn(min = ChatInputSpec.ROW_HEIGHT).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BasicTextField(
            value = text,
            onValueChange = { text = ChatInputSpec.limit(it) },
            modifier = Modifier.weight(1f).heightIn(min = ChatInputSpec.PILL_HEIGHT).semantics { contentDescription = hint },
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            maxLines = ChatInputSpec.MAX_LINES,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send, capitalization = KeyboardCapitalization.Sentences),
            keyboardActions = KeyboardActions(onSend = { send() }),
            decorationBox = { field ->
                Box(
                    Modifier.clip(RoundedCornerShape(ChatInputSpec.PILL_RADIUS))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .heightIn(min = ChatInputSpec.PILL_HEIGHT)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (text.isEmpty()) {
                        Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    field()
                }
            },
        )
        if (canSend) {
            // A 48 dp touch target around a 36 dp circle.
            IconButton(onClick = send, modifier = Modifier.size(ChatInputSpec.SEND_TARGET)) {
                Box(Modifier.size(ChatInputSpec.SEND_CIRCLE).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.chat_send),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/** Sizes and rules of the chat box; plain values so a JVM test pins them. */
object ChatInputSpec {
    val PILL_HEIGHT = 40.dp
    val ROW_HEIGHT = 52.dp
    val PILL_RADIUS = 20.dp
    val SEND_CIRCLE = 36.dp
    val SEND_TARGET = 48.dp
    const val MAX_LINES = 3
    const val MAX_MESSAGE = 500

    fun limit(text: String): String = text.take(MAX_MESSAGE)

    fun canSend(text: String): Boolean = text.isNotBlank()

    /** What is sent for what was typed: trimmed, or null when there is nothing to send. */
    fun outgoing(text: String): String? = text.trim().takeIf { it.isNotEmpty() }
}

private const val MAX_PLURAL = 99

/** Chat needs a signed-in viewer (Twitch only lets an account read it through the API this app uses). */
@Composable
fun ChatSignInPrompt(onSignIn: () -> Unit, modifier: Modifier = Modifier) {
    com.twitchplayer.phone.ui.components.MessageState(
        title = stringResource(R.string.phone_player_chat_signin),
        modifier = modifier,
        actionLabel = stringResource(R.string.state_signin),
        onAction = onSignIn,
    )
}

/** Style of a panel drawn over the picture: the viewer's background opacity behind the lines. */
@Composable
@ReadOnlyComposable
fun overVideoPanelColor(style: PhoneChatStyle) = MaterialTheme.colorScheme.scrim.copy(alpha = style.backgroundAlpha * OVER_VIDEO_MAX)

private const val OVER_VIDEO_MAX = 0.75f

/** Fades the top [height] of a list into its background, so a line clipped by the edge fades out instead of being cut in half. */
private fun Modifier.fadeTopEdge(height: Dp): Modifier = graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
    drawContent()
    val fade = height.toPx()
    drawRect(
        brush = Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black, startY = 0f, endY = fade),
        size = Size(size.width, fade),
        blendMode = BlendMode.DstIn,
    )
}

private val TOP_FADE = 20.dp
