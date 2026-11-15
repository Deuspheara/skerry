package com.twitchplayer.tv.feature.player.data.chat

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The last messages of a channel's chat, so opening a stream does not start from an empty chat (the Twitch website shows them; the live
 * connection only delivers what is said from now on). Twitch has no public API for it: this reads the community service
 * recent-messages.robotty.de, which keeps the last lines of every channel as raw IRC. Best effort: any failure returns nothing.
 */
class RecentMessages(private val http: HttpClient, private val baseUrl: String = "https://recent-messages.robotty.de") {
    suspend fun load(channelLogin: String, limit: Int = LIMIT): List<ChatEvent.Message> {
        if (channelLogin.isBlank()) return emptyList()
        val response = http.get("$baseUrl/api/v2/recent-messages/${channelLogin.lowercase()}?limit=$limit&hide_moderation_messages=true")
        if (!response.status.isSuccess()) return emptyList()
        val body = JSON.decodeFromString<Reply>(response.bodyAsText())
        return body.messages.mapNotNull { parsePrivmsg(it) }.map { ChatEvent.Message(it) }
    }

    @Serializable private class Reply(val messages: List<String> = emptyList())

    companion object {
        const val LIMIT = 80
        private val JSON = Json { ignoreUnknownKeys = true }

        /** One raw IRC line (`@tags :user!user@user.tmi.twitch.tv PRIVMSG #channel :text`) as a chat message; null for anything else. */
        fun parsePrivmsg(raw: String): ChatMessage? {
            val tagEnd = raw.indexOf(' ')
            val rest = if (raw.startsWith("@") && tagEnd > 0) raw.substring(tagEnd + 1) else return null
            val marker = rest.indexOf(" PRIVMSG ")
            val textStart = if (marker >= 0) rest.indexOf(" :", marker) else -1
            if (textStart < 0) return null
            val tags = parseTags(raw.substring(1, tagEnd))
            val id = tags["id"].orEmpty()
            if (id.isEmpty()) return null
            val login = rest.substringBefore('!').removePrefix(":")
            val text = rest.substring(textStart + 2).removePrefix("\u0001ACTION ").removeSuffix("\u0001")
            return ChatMessage(
                id = id,
                broadcasterId = tags["room-id"].orEmpty(),
                userId = tags["user-id"].orEmpty(),
                username = tags["display-name"].orEmpty().ifEmpty { login },
                login = login,
                content = ChatMessageContent(text, fragments(text, tags["emotes"].orEmpty())),
                badges = parseBadges(tags["badges"].orEmpty()),
                color = tags["color"]?.takeIf { it.isNotEmpty() },
                reply = parseReply(tags),
            )
        }

        private fun parseTags(raw: String): Map<String, String> = raw.split(';').associate { pair ->
            val eq = pair.indexOf('=')
            if (eq < 0) pair to "" else pair.substring(0, eq) to unescape(pair.substring(eq + 1))
        }

        private fun parseBadges(raw: String): List<ChatBadge> = raw.split(',').filter { it.isNotEmpty() }.map {
            ChatBadge(it.substringBefore('/'), it.substringAfter('/', ""))
        }

        private fun parseReply(tags: Map<String, String>): ChatReply? = tags["reply-parent-msg-id"]?.takeIf { it.isNotEmpty() }?.let {
            ChatReply(it, tags["reply-parent-msg-body"].orEmpty(), tags["reply-parent-display-name"].orEmpty(), tags["reply-parent-user-login"].orEmpty())
        }

        /** Splits [text] into text and emote fragments with IRC's `id:start-end,start-end/id:...` ranges (counted in code points). */
        internal fun fragments(text: String, emotes: String): List<ChatFragment> {
            if (emotes.isEmpty()) return listOf(ChatFragment("text", text))
            // Code point index -> char index.
            val starts = ArrayList<Int>()
            var i = 0
            while (i < text.length) {
                starts += i
                i += if (text[i].isHighSurrogate() && i + 1 < text.length) 2 else 1
            }
            starts += text.length
            val ranges = emotes.split('/').flatMap { entry ->
                val id = entry.substringBefore(':')
                entry.substringAfter(':', "").split(',').mapNotNull { range ->
                    val (from, to) = codePointRange(range) ?: return@mapNotNull null
                    if (to < from || to >= starts.size - 1) null else Triple(starts[from], starts[to + 1], id)
                }
            }.sortedBy { it.first }
            val out = ArrayList<ChatFragment>()
            var cursor = 0
            for ((from, to, id) in ranges) {
                if (from < cursor) continue
                if (from > cursor) out += ChatFragment("text", text.substring(cursor, from))
                out += ChatFragment("emote", text.substring(from, to), emote = ChatEmote(id))
                cursor = to
            }
            if (cursor < text.length) out += ChatFragment("text", text.substring(cursor))
            return out
        }

        private fun codePointRange(range: String): Pair<Int, Int>? {
            val from = range.substringBefore('-').toIntOrNull()
            val to = range.substringAfter('-', "").toIntOrNull()
            return if (from == null || to == null) null else from to to
        }

        private fun unescape(value: String): String {
            if ('\\' !in value) return value
            val out = StringBuilder()
            var i = 0
            while (i < value.length) {
                val c = value[i]
                if (c == '\\' && i + 1 < value.length) {
                    out.append(
                        when (value[i + 1]) {
                            's' -> ' '
                            ':' -> ';'
                            'r' -> '\r'
                            'n' -> '\n'
                            else -> value[i + 1]
                        },
                    )
                    i += 2
                } else {
                    out.append(c)
                    i++
                }
            }
            return out.toString()
        }
    }
}
