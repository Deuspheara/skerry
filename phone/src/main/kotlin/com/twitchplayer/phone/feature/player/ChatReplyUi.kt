package com.twitchplayer.phone.feature.player

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.twitchplayer.phone.R
import com.twitchplayer.phone.ui.components.PhoneIcons
import com.twitchplayer.tv.feature.player.data.chat.NoticeKind
import com.twitchplayer.tv.feature.player.presentation.ChatLine
import com.twitchplayer.tv.feature.player.presentation.ChatPiece
import com.twitchplayer.tv.feature.player.presentation.ChatReplyContext
import kotlin.math.abs

/** The line of the message a reply answers, drawn above it: an arrow, the author and the start of the text. */
@Composable
fun ReplyContext(reply: ChatReplyContext, style: PhoneChatStyle, overVideo: Boolean, modifier: Modifier = Modifier) {
    val flip = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    val color = if (overVideo) Color.White.copy(alpha = CONTEXT_ALPHA) else MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier.fillMaxWidth().padding(bottom = 1.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(PhoneIcons.Reply, null, tint = color, modifier = Modifier.size((style.textSp + SMALL_ICON_EXTRA_SP).dp).graphicsLayer { scaleX = flip })
        Text(
            stringResource(R.string.phone_chat_reply_context, reply.name.ifBlank { reply.login }, reply.body),
            modifier = Modifier.padding(start = 4.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = (style.textSp - CONTEXT_TEXT_STEP_SP).coerceAtLeast(CONTEXT_TEXT_MIN_SP).sp),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A line the viewer can answer: dragging it sideways (towards the end of the line, mirrored in a right-to-left layout) past a threshold
 * answers it, with a short haptic tick and an arrow that appears behind it; a long press and TalkBack's actions do the same. The drag only
 * takes horizontal movement, so scrolling the list is untouched. The movement is applied in a graphics layer (no recomposition per frame).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SwipeToReply(line: ChatLine, onReply: (ChatLine) -> Unit, overVideo: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val enabled = line.canReply
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val haptic = LocalHapticFeedback.current
    val threshold = with(androidx.compose.ui.platform.LocalDensity.current) { REPLY_THRESHOLD.toPx() }
    var offset by remember { mutableFloatStateOf(0f) }
    var armed by remember { mutableStateOf(false) }
    val label = stringResource(R.string.phone_chat_reply)
    val tint = if (overVideo) Color.White else MaterialTheme.colorScheme.primary
    val gestures = if (enabled) {
        Modifier
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(label) {
                        onReply(line)
                        true
                    },
                )
            }
            .combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}, onLongClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onReply(line)
            })
            .pointerInput(line.id, rtl) {
                detectHorizontalDragGestures(
                    onDragStart = { armed = false },
                    onDragEnd = {
                        if (armed) onReply(line)
                        offset = 0f
                        armed = false
                    },
                    onDragCancel = {
                        offset = 0f
                        armed = false
                    },
                ) { change, delta ->
                    change.consume()
                    val towardsEnd = if (rtl) -delta else delta
                    offset = (offset + towardsEnd).coerceIn(0f, threshold * MAX_PULL)
                    if (!armed && offset >= threshold) {
                        armed = true
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                }
            }
    } else {
        Modifier
    }
    Box(modifier.fillMaxWidth().then(gestures)) {
        // The arrow sits behind the line and fades in with the pull.
        if (enabled) {
            Icon(
                PhoneIcons.Reply,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp).size(ARROW_SIZE).graphicsLayer { alpha = (offset / threshold).coerceIn(0f, 1f) },
            )
        }
        Box(Modifier.graphicsLayer { translationX = if (rtl) -offset else offset }) { content() }
    }
}

private val REPLY_THRESHOLD = 56.dp
private val ARROW_SIZE = 18.dp
private val REPLY_ICON_SIZE = 16.dp
private const val ICON_EXTRA_SP = 4f
private const val SMALL_ICON_EXTRA_SP = 1f
private const val CONTEXT_TEXT_STEP_SP = 2f
private const val CONTEXT_TEXT_MIN_SP = 10f
private const val CONTEXT_ALPHA = 0.7f
private const val MAX_PULL = 1.4f

/** What a notification card is dressed with: the accent colour and the icon. */
internal data class NoticeStyle(val accent: Color, val icon: ImageVector)

@Composable
internal fun noticeStyle(kind: NoticeKind, accentName: String): NoticeStyle {
    val scheme = MaterialTheme.colorScheme
    return when (kind) {
        NoticeKind.Sub, NoticeKind.Resub -> NoticeStyle(scheme.primary, PhoneIcons.Star)

        NoticeKind.Gift, NoticeKind.GiftCommunity -> NoticeStyle(scheme.tertiary, PhoneIcons.Gift)

        NoticeKind.Raid -> NoticeStyle(NOTICE_GREEN, PhoneIcons.ArrowForward)

        NoticeKind.Bits -> NoticeStyle(NOTICE_GOLD, PhoneIcons.Diamond)

        NoticeKind.Announcement -> NoticeStyle(
            when (accentName.uppercase()) {
                "BLUE" -> NOTICE_BLUE
                "GREEN" -> NOTICE_GREEN
                "ORANGE" -> NOTICE_ORANGE
                "PURPLE" -> scheme.tertiary
                else -> scheme.primary
            },
            PhoneIcons.Info,
        )

        NoticeKind.Other -> NoticeStyle(scheme.onSurfaceVariant, PhoneIcons.Info)
    }
}

private val NOTICE_GREEN = Color(0xFF2EB872)
private val NOTICE_GOLD = Color(0xFFDAA520)
private val NOTICE_BLUE = Color(0xFF5C7CFF)
private val NOTICE_ORANGE = Color(0xFFE6572E)

/**
 * A subscription, gift, raid or announcement as a card in the chat: an accent bar, an icon, and the sentence Twitch wrote (with the
 * viewer's own words after it when there are some). The accent says what happened without relying on the colour alone: the icon differs too.
 */
@Composable
fun ChatNoticeCard(line: ChatLine, style: PhoneChatStyle, overVideo: Boolean, modifier: Modifier = Modifier) {
    val kind = line.notice ?: return
    val look = noticeStyle(kind, line.accent)
    val sentence = (line.pieces.firstOrNull() as? ChatPiece.Text)?.text.orEmpty()
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp).clip(shape)
            .background(look.accent.copy(alpha = if (overVideo) 0.28f else 0.14f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(look.accent))
        Icon(look.icon, null, tint = look.accent, modifier = Modifier.padding(start = 8.dp).size((style.textSp + ICON_EXTRA_SP).dp))
        Text(
            sentence,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 6.dp),
            // The sentence is Twitch's, in the language of the chat: it takes its direction from its own text (a French notice in an Arabic layout ends with its "!").
            style = MaterialTheme.typography.bodySmall.copy(fontSize = style.textSp.sp, fontWeight = FontWeight.Medium, textDirection = androidx.compose.ui.text.style.TextDirection.Content),
            color = if (overVideo) Color.White else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** "Replying to name" and what they said, above the box, with a button that drops the reply. */
@Composable
fun ReplyBar(line: ChatLine, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val flip = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    Row(
        modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(PhoneIcons.Reply, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(REPLY_ICON_SIZE).graphicsLayer { scaleX = flip })
        Column(Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(stringResource(R.string.phone_chat_replying_to, line.nick), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val said = line.pieces.joinToString("") { piece ->
                when (piece) {
                    is ChatPiece.Text -> piece.text
                    is ChatPiece.Strong -> piece.text
                    is ChatPiece.Emote -> piece.name
                }
            }
            Text(said, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        androidx.compose.material3.IconButton(onClick = onCancel, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Filled.Close, stringResource(R.string.phone_chat_cancel_reply), modifier = Modifier.size(18.dp))
        }
    }
}

/** The chatters whose name starts with what follows the `@` being typed: one tap completes the mention. */
@Composable
fun MentionSuggestionStrip(chatters: List<com.twitchplayer.tv.feature.player.presentation.Chatter>, onPick: (com.twitchplayer.tv.feature.player.presentation.Chatter) -> Unit, modifier: Modifier = Modifier) {
    if (chatters.isEmpty()) return
    val title = stringResource(R.string.phone_chat_mention_suggestions)
    androidx.compose.foundation.lazy.LazyRow(
        modifier.fillMaxWidth().semantics { contentDescription = title },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(chatters.size, key = { chatters[it].login }) { index ->
            val chatter = chatters[index]
            Text(
                "@" + chatter.name,
                modifier = Modifier.heightIn(min = 32.dp).clip(androidx.compose.foundation.shape.CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .combinedClickable(onClick = { onPick(chatter) }, role = androidx.compose.ui.semantics.Role.Button)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
        }
    }
}
