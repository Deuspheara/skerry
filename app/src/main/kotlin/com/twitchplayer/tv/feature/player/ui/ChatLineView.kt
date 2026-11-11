package com.twitchplayer.tv.feature.player.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.twitchplayer.tv.core.designsystem.theme.TvColors
import com.twitchplayer.tv.core.designsystem.theme.TvType
import com.twitchplayer.tv.feature.player.presentation.ChatLine
import com.twitchplayer.tv.feature.player.presentation.ChatLineKind
import com.twitchplayer.tv.feature.player.presentation.ChatPiece
import kotlin.math.roundToInt
import coil3.size.Size as CoilSize

private const val BADGE = "b"
private const val EMOTE = "e"

/**
 * One message as a single [Text]: nickname, badges and emote slots are inline placeholders in the text layout, so
 * wrapping is the platform's and there is no row of child composables per message. [animated] is how many of this
 * line's animated emotes may currently play (read inside the emote slots only, so a change recomposes them, not the line).
 */
@Composable
fun ChatLineView(line: ChatLine, style: ChatStyle, animated: State<Int>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val built = remember(line, style, density) {
        fun px(dp: Float) = (dp * density.density).roundToInt().coerceAtLeast(1)
        val inline = HashMap<String, InlineTextContent>()
        var animatedIndex = 0
        val text = buildAnnotatedString {
            if (style.showTimestamps) {
                withStyle(SpanStyle(color = TvColors.Grey, fontSize = (style.textSp - 2f).sp)) {
                    append(line.time)
                    append("  ")
                }
            }
            if (style.showBadges) {
                line.badgeUrls.forEachIndexed { index, url ->
                    val id = "$BADGE$index"
                    appendInlineContent(id, " ")
                    val size = px(style.badgeDp)
                    inline[id] = InlineTextContent(Placeholder(with(density) { style.badgeDp.dp.toSp() }, with(density) { style.badgeDp.dp.toSp() }, PlaceholderVerticalAlign.TextCenter)) {
                        val request = remember(url, size) { ImageRequest.Builder(context).data(url).size(CoilSize(size, size)).build() }
                        AsyncImage(request, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    }
                    append(" ")
                }
            }
            if (line.kind == ChatLineKind.System) {
                // Twitch's own sentence (sub, raid...): quieter than a chatter's line.
                val sentence = (line.pieces.firstOrNull() as? ChatPiece.Text)?.text.orEmpty()
                withStyle(SpanStyle(color = TvColors.TextSecondary, fontStyle = FontStyle.Italic)) { append(sentence) }
                return@buildAnnotatedString
            }
            withStyle(SpanStyle(color = Color(line.color), fontWeight = FontWeight.SemiBold)) { append(line.nick) }
            append(": ")
            if (line.bits > 0) withStyle(SpanStyle(color = TvColors.ChatBits, fontWeight = FontWeight.Bold)) { append("◆${line.bits} ") }
            line.pieces.forEachIndexed { index, piece ->
                when (piece) {
                    is ChatPiece.Text -> append(piece.text)

                    is ChatPiece.Strong -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(piece.text) }

                    is ChatPiece.Emote -> {
                        val id = "$EMOTE$index"
                        appendInlineContent(id, piece.name)
                        val height = style.emoteDp
                        val aspect = piece.layers.first().let { it.aspect.takeIf { a -> a > 0f } ?: EmoteAspects[it.url] ?: 1f }.coerceIn(0.5f, 3f)
                        val width = height * aspect
                        // Animated layers of this line are numbered in reading order; the first `animated.value` play.
                        val firstAnimated = animatedIndex
                        animatedIndex += piece.layers.count { it.animated }
                        inline[id] = InlineTextContent(
                            Placeholder(with(density) { width.dp.toSp() }, with(density) { height.dp.toSp() }, PlaceholderVerticalAlign.TextCenter),
                        ) {
                            val wPx = px(width)
                            val hPx = px(height)
                            var seen = 0
                            Box(Modifier.fillMaxSize()) {
                                piece.layers.forEach { layer ->
                                    val slot = if (layer.animated) firstAnimated + seen++ else -1
                                    EmoteLayer(layer, animate = slot >= 0 && slot < animated.value, widthPx = wPx, heightPx = hPx)
                                }
                            }
                        }
                    }
                }
            }
        }
        text to inline
    }
    val rule = when (line.kind) {
        ChatLineKind.Mention -> TvColors.Purple
        ChatLineKind.Highlight -> TvColors.Focus
        else -> null
    }
    val tint = when (line.kind) {
        ChatLineKind.Mention -> TvColors.ChatMention
        ChatLineKind.Highlight -> TvColors.ChatHighlight
        else -> null
    }
    Text(
        text = built.first,
        inlineContent = built.second,
        style = TvType.chat(style.textSp.sp, style.lineHeightSp.sp).let { if (style.textShadow) it.copy(shadow = OverlayShadow) else it },
        color = TvColors.Text,
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (tint == null) {
                    Modifier
                } else {
                    Modifier.drawBehind {
                        drawRect(tint)
                        drawRect(rule!!, Offset.Zero, Size(2.dp.toPx(), size.height))
                    }
                },
            )
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

// Wide, dense shadow: lines stay readable over bright video without a panel behind them.
private val OverlayShadow = androidx.compose.ui.graphics.Shadow(androidx.compose.ui.graphics.Color.Black, androidx.compose.ui.geometry.Offset(0f, 1.5f), 10f)
