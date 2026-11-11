package com.twitchplayer.tv.feature.player.presentation

import com.twitchplayer.tv.feature.player.data.ChatAssets
import com.twitchplayer.tv.feature.player.data.chat.ChatEmote
import com.twitchplayer.tv.feature.player.data.chat.ChatEvent
import com.twitchplayer.tv.feature.player.data.chat.ChatFragment
import com.twitchplayer.tv.feature.player.data.chat.ChatMessage
import com.twitchplayer.tv.feature.player.data.emotes.Emote
import kotlin.math.pow

/** A piece of a message, in reading order. Everything the renderer needs is here: it knows nothing of providers or transport. */
sealed interface ChatPiece {
    data class Text(val text: String) : ChatPiece

    /** Mentions and cheermotes: plain words with emphasis. */
    data class Strong(val text: String) : ChatPiece

    /** One emote slot. Zero-width emotes that follow it are extra [layers], drawn on top of the first. */
    data class Emote(val layers: List<com.twitchplayer.tv.feature.player.data.emotes.Emote>) : ChatPiece {
        val name: String get() = layers.first().name
    }
}

enum class ChatLineKind { Normal, Highlight, Mention, System }

/** A chat event resolved against the channel's assets, ready to draw. Immutable, so a list of them is cheap to diff. */
data class ChatLine(
    val id: String,
    val kind: ChatLineKind,
    val nick: String,
    /** ARGB, already adjusted to be readable on the chat background. */
    val color: Long,
    val badgeUrls: List<String>,
    val pieces: List<ChatPiece>,
    /** Arrival time as "HH:mm". */
    val time: String,
    val bits: Int = 0,
    /** Lower-case login of the author ("" for system lines): a timeout removes all of a viewer's lines. */
    val login: String = "",
)

private const val TWITCH_EMOTE_CDN = "https://static-cdn.jtvnw.net/emoticons/v2/"

/** The emote image of [id] in [format] ("static" / "animated"); the CDN path is `<id>/<format>/dark/2.0`. */
private fun twitchEmoteUrl(id: String, format: String) = "$TWITCH_EMOTE_CDN$id/$format/dark/2.0"

internal fun ChatEvent.toLine(assets: ChatAssets, selfLogin: String?, nowMs: Long): ChatLine = when (this) {
    is ChatEvent.Message -> message.toLine(assets, selfLogin, nowMs)
    is ChatEvent.Notice -> ChatLine(id, ChatLineKind.System, "", 0, emptyList(), listOf(ChatPiece.Text(text)), clockText(nowMs))
    else -> error("not a chat line: $this")
}

internal fun ChatMessage.toLine(assets: ChatAssets, selfLogin: String?, nowMs: Long): ChatLine {
    val mentionsSelf = selfLogin != null && !login.equals(selfLogin, true) &&
        (
            content.fragments.any { it.type == "mention" && it.mention?.login.equals(selfLogin, true) } ||
                content.text.contains("@$selfLogin", ignoreCase = true)
            )
    return ChatLine(
        id = id,
        kind = when {
            messageType == "channel_points_highlighted" || messageType == "user_intro" -> ChatLineKind.Highlight
            mentionsSelf -> ChatLineKind.Mention
            else -> ChatLineKind.Normal
        },
        nick = username,
        color = parseColor(color)?.let(::readableColor) ?: defaultNickColor(login),
        badgeUrls = badges.mapNotNull { assets.badges["${it.setId}:${it.id}"] },
        pieces = parsePieces(content.fragments.ifEmpty { listOf(ChatFragment("text", content.text)) }, assets.emotes),
        time = clockText(nowMs),
        bits = cheer?.bits ?: 0,
        login = login.lowercase(),
    )
}

/**
 * Splits a message into text and emote slots.
 * - Twitch emotes come from the message itself (id + formats) and are never looked up again.
 * - Every other word is matched, case-sensitively and as a whole word, against [thirdParty] (already merged by precedence).
 * - A zero-width emote directly after an emote (only blanks between) joins that emote's slot as an overlay layer;
 *   after text, or first in the message, it is drawn as an ordinary emote.
 */
internal fun parsePieces(fragments: List<ChatFragment>, thirdParty: Map<String, Emote>): List<ChatPiece> {
    val out = ArrayList<ChatPiece>()
    val pending = StringBuilder()
    fun flush() {
        if (pending.isNotEmpty()) {
            out += ChatPiece.Text(pending.toString())
            pending.setLength(0)
        }
    }
    fun addEmote(emote: Emote) {
        val last = out.lastOrNull()
        if (emote.zeroWidth && last is ChatPiece.Emote && pending.isBlank()) {
            pending.setLength(0)
            out[out.lastIndex] = ChatPiece.Emote(last.layers + emote)
        } else {
            flush()
            out += ChatPiece.Emote(listOf(emote))
        }
    }
    for (fragment in fragments) {
        val twitch = fragment.emote
        when {
            fragment.type == "emote" && twitch != null -> addEmote(twitchEmote(fragment.text, twitch))

            fragment.type == "mention" || fragment.type == "cheermote" -> {
                flush()
                out += ChatPiece.Strong(fragment.text)
            }

            else -> {
                val text = fragment.text
                var i = 0
                while (i < text.length) {
                    val blank = text[i].isWhitespace()
                    var j = i
                    while (j < text.length && text[j].isWhitespace() == blank) j++
                    val run = text.substring(i, j)
                    val emote = if (blank) null else thirdParty[run]
                    if (emote != null) addEmote(emote) else pending.append(run)
                    i = j
                }
            }
        }
    }
    flush()
    return out
}

private fun twitchEmote(name: String, emote: ChatEmote) = Emote(
    name = name,
    url = twitchEmoteUrl(emote.id, "static"),
    animatedUrl = if ("animated" in emote.format) twitchEmoteUrl(emote.id, "animated") else null,
    aspect = 1f,
)

/** "#RRGGBB" -> opaque ARGB; null / empty / malformed -> null. */
internal fun parseColor(hex: String?): Long? {
    val clean = hex?.removePrefix("#") ?: return null
    if (clean.length != 6) return null
    return clean.toLongOrNull(16)?.let { 0xFF000000L or it }
}

/** Panel colour usernames are drawn on (TvColors.Base); kept here as raw RGB so the maths stays unit-testable. */
private const val CHAT_BACKGROUND_RGB = 0x18181B

internal fun contrastRatio(a: Long, b: Long): Double {
    val la = luminance(a)
    val lb = luminance(b)
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
}

private fun luminance(argb: Long): Double {
    fun channel(shift: Int): Double {
        val c = ((argb shr shift) and 0xFF) / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
}

/** Lightens a username colour until it reads on the chat background (WCAG AA 4.5:1); Twitch lets users pick near-black blues. */
internal fun readableColor(argb: Long): Long {
    var color = argb
    repeat(12) {
        if (contrastRatio(color, 0xFF000000L or CHAT_BACKGROUND_RGB.toLong()) >= 4.5) return color
        fun mix(shift: Int): Long {
            val c = (color shr shift) and 0xFF
            return c + (255 - c) * 15 / 100
        }
        color = 0xFF000000L or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }
    return color
}

private val DEFAULT_NICK_COLORS = longArrayOf(
    0xFFFF4D4D, 0xFF5C7CFF, 0xFF2EB872, 0xFFE6572E, 0xFFFF8A65, 0xFF9ACD32, 0xFFDAA520, 0xFF4FC3C3, 0xFF1E90FF, 0xFFFF69B4, 0xFFB57BFF, 0xFF00D68F,
)

/** Twitch gives users without a chosen colour a stable one; we do the same so a quiet chatter keeps their hue. */
internal fun defaultNickColor(login: String): Long = readableColor(DEFAULT_NICK_COLORS[login.lowercase().hashCode().mod(DEFAULT_NICK_COLORS.size)])

/** "HH:mm" of [millis] in the zone [zoneId] (the device's time zone when null), as the line's arrival time. */
internal fun clockText(millis: Long, zoneId: String? = null): String {
    val (hour, minute) = timeOfDay(millis, zoneId)
    return "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
}
