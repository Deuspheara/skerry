package com.twitchplayer.tv.feature.player.presentation

/** The `@name` being typed at the end of the text before the cursor; [name] is what follows the `@` (at least one character). */
data class MentionQuery(val from: Int, val to: Int, val name: String)

/** The `@name` being typed before [cursor]: `@` must start the text or follow a space. Null when the cursor is not in one. */
fun mentionQueryAt(text: String, cursor: Int): MentionQuery? {
    if (cursor !in 1..text.length) return null
    var start = cursor
    while (start > 0 && isLoginChar(text[start - 1])) start--
    if (start == 0 || text[start - 1] != '@') return null
    val at = start - 1
    if (at > 0 && !text[at - 1].isWhitespace()) return null
    val name = text.substring(start, cursor)
    return if (name.isNotEmpty()) MentionQuery(at, cursor, name) else null
}

private fun isLoginChar(c: Char) = c.isLetterOrDigit() || c == '_'

/** A chatter the viewer may mention: the display name Twitch shows and the login to type. */
data class Chatter(val login: String, val name: String)

/**
 * The people who wrote in [lines], newest first, whose login or display name starts with [prefix] (ignoring case), at most [limit].
 * [selfLogin] is left out: nobody mentions themselves. System lines have no author.
 */
fun chatterSuggestions(lines: List<ChatLine>, prefix: String, selfLogin: String?, limit: Int = 5): List<Chatter> {
    val p = prefix.trim().trimStart('@')
    if (p.isEmpty()) return emptyList()
    return lines.asReversed().asSequence()
        .filter { it.login.isNotEmpty() && !it.login.equals(selfLogin, true) }
        .distinctBy { it.login }
        .filter { it.login.startsWith(p, true) || it.nick.startsWith(p, true) }
        .take(limit)
        .map { Chatter(it.login, it.nick) }
        .toList()
}

/** [text] with the `@query` replaced by `@login` and a trailing space. Returns the new text and cursor. */
fun completeMention(text: String, query: MentionQuery, login: String): Pair<String, Int> {
    val after = text.substring(query.to)
    val trail = if (after.startsWith(" ")) "" else " "
    val before = text.substring(0, query.from)
    val inserted = "@$login"
    return (before + inserted + trail + after) to (before.length + inserted.length + 1)
}

/** What the box starts with when the viewer replies to [login]: the mention Twitch shows in front of an answer. */
fun replyPrefix(login: String): String = "@$login "
