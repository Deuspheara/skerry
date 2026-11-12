package com.twitchplayer.phone.feature.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.twitchplayer.phone.ui.theme.PhoneColors
import com.twitchplayer.tv.feature.player.presentation.ChatLine
import com.twitchplayer.tv.feature.player.presentation.ChatLineKind
import com.twitchplayer.tv.feature.player.presentation.ChatPiece
import com.twitchplayer.tv.feature.player.ui.EmoteAspects
import com.twitchplayer.tv.feature.player.ui.EmoteLayer
import kotlin.math.roundToInt
import coil3.size.Size as CoilSize

private const val BADGE = "b"
private const val EMOTE = "e"
private const val MIN_ASPECT = 0.5f
private const val MAX_ASPECT = 3f
private const val MENTION_ALPHA = 0.30f
private const val HIGHLIGHT_ALPHA = 0.16f
private const val RULE_WIDTH_DP = 2
private const val SHADOW_OFFSET_Y = 1.5f
private const val SHADOW_BLUR = 8f

/**
 * One message as a single [Text]: nickname, badges and emote slots are inline placeholders in the text layout, so wrapping is the
 * platform's and there is no row of child composables per message. [animated] is how many of this line's animated emotes may play now
 * (read inside the emote slots only, so a change recomposes them and not the line). [overVideo] draws white text with a soft shadow,
 * for chat laid directly over the picture.
 */
@Composable
fun PhoneChatLine(line: ChatLine, style: PhoneChatStyle, animated: () -> Int, overVideo: Boolean, modifier: Modifier = Modifier) {
    // A subscription, gift, raid or announcement is a card, not a line.
    if (line.kind == ChatLineKind.System && line.notice != null) {
        ChatNoticeCard(line, style, overVideo, modifier)
        return
    }
    val context = LocalContext.current
    val density = LocalDensity.current
    val secondary = if (overVideo) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
    val bitsColor = MaterialTheme.colorScheme.primary
    // Over the picture the panel is dark, whatever the theme; on the page the surface follows the theme.
    val surface = if (overVideo) Color.Black else MaterialTheme.colorScheme.background
    val built = remember(line, style, density, secondary, bitsColor, surface) {
        fun px(dp: Float) = (dp * density.density).roundToInt().coerceAtLeast(1)
        val inline = HashMap<String, InlineTextContent>()
        var animatedIndex = 0
        val text = buildAnnotatedString {
            if (style.showTimestamps) {
                withStyle(SpanStyle(color = secondary, fontSize = (style.textSp - 2f).sp)) {
                    append(line.time)
                    append("  ")
                }
            }
            if (style.showBadges) {
                line.badgeUrls.forEachIndexed { index, url ->
                    val id = "$BADGE$index"
                    appendInlineContent(id, " ")
                    val size = px(style.badgeDp)
                    val sp = with(density) { style.badgeDp.dp.toSp() }
                    inline[id] = InlineTextContent(Placeholder(sp, sp, PlaceholderVerticalAlign.TextCenter)) {
                        val request = remember(url, size) { ImageRequest.Builder(context).data(url).size(CoilSize(size, size)).build() }
                        AsyncImage(request, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    }
                    append(" ")
                }
            }
            if (line.kind == ChatLineKind.System) {
                val sentence = (line.pieces.firstOrNull() as? ChatPiece.Text)?.text.orEmpty()
                withStyle(SpanStyle(color = secondary, fontStyle = FontStyle.Italic)) { append(sentence) }
                return@buildAnnotatedString
            }
            withStyle(SpanStyle(color = readableOn(surface, Color(line.color)), fontWeight = FontWeight.SemiBold)) { append(line.nick) }
            append(": ")
            if (line.bits > 0) withStyle(SpanStyle(color = bitsColor, fontWeight = FontWeight.Bold)) { append("◆${line.bits} ") }
            line.pieces.forEachIndexed { index, piece ->
                when (piece) {
                    is ChatPiece.Text -> append(piece.text)

                    is ChatPiece.Strong -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(piece.text) }

                    is ChatPiece.Emote -> {
                        val id = "$EMOTE$index"
                        appendInlineContent(id, piece.name)
                        val height = style.emoteDp
                        val aspect = piece.layers.first().let { it.aspect.takeIf { a -> a > 0f } ?: EmoteAspects[it.url] ?: 1f }.coerceIn(MIN_ASPECT, MAX_ASPECT)
                        val width = height * aspect
                        // Animated layers of this line are numbered in reading order; the first `animated.value` play.
                        val slots = piece.layers.map { layer -> if (layer.animated) animatedIndex++ else -1 }
                        inline[id] = InlineTextContent(
                            Placeholder(with(density) { width.dp.toSp() }, with(density) { height.dp.toSp() }, PlaceholderVerticalAlign.TextCenter),
                        ) {
                            val wPx = px(width)
                            val hPx = px(height)
                            Box(Modifier.fillMaxSize()) {
                                piece.layers.forEachIndexed { layerIndex, layer ->
                                    EmoteLayer(layer, animate = slots[layerIndex] in 0 until animated(), widthPx = wPx, heightPx = hPx)
                                }
                            }
                        }
                    }
                }
            }
        }
        text to inline
    }
    val rule = MaterialTheme.colorScheme.primary
    val tint = when (line.kind) {
        ChatLineKind.Mention -> rule.copy(alpha = MENTION_ALPHA)
        ChatLineKind.Highlight -> rule.copy(alpha = HIGHLIGHT_ALPHA)
        else -> null
    }
    // Each message takes its own direction from its text: an English or French line stays left-to-right inside an Arabic layout.
    val base = TextStyle(
        fontSize = style.textSp.sp,
        lineHeight = style.lineHeightSp.sp,
        color = if (overVideo) Color.White else MaterialTheme.colorScheme.onSurface,
        textDirection = TextDirection.Content,
    )
    Column(
        modifier
            .fillMaxWidth()
            .then(
                if (tint == null) {
                    Modifier
                } else {
                    Modifier.drawBehind {
                        drawRect(tint)
                        drawRect(rule, Offset.Zero, Size(RULE_WIDTH_DP.dp.toPx(), size.height))
                    }
                },
            )
            .padding(horizontal = 8.dp, vertical = 1.dp),
    ) {
        line.reply?.let { ReplyContext(it, style, overVideo) }
        Text(
            text = built.first,
            inlineContent = built.second,
            style = if (overVideo) base.copy(shadow = Shadow(PhoneColors.Shadow, Offset(0f, SHADOW_OFFSET_Y), SHADOW_BLUR)) else base,
        )
    }
}
