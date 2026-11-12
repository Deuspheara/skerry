package com.twitchplayer.tv.feature.player.presentation

import com.twitchplayer.tv.feature.player.data.emotes.Emote
import com.twitchplayer.tv.feature.player.data.emotes.EmoteGroup
import com.twitchplayer.tv.feature.player.data.emotes.EmoteSection
import com.twitchplayer.tv.feature.player.data.emotes.EmoteSource
import com.twitchplayer.tv.feature.player.data.emotes.UserEmoteAccess
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * What the emote picker and the `:` suggestions work from: every list of every provider for the channel being watched, the viewer's
 * recents, and how much is known about which Twitch emotes the viewer may use.
 */
data class EmotePickerState(
    val groups: ImmutableList<EmoteGroup> = persistentListOf(),
    /** The emotes the viewer picked last, newest first (only those that exist for this channel). */
    val recent: ImmutableList<Emote> = persistentListOf(),
    val access: UserEmoteAccess = UserEmoteAccess.Unavailable,
) {
    val isEmpty: Boolean get() = groups.isEmpty()

    /** The sources that have at least one list, in the picker's tab order. */
    val sources: List<EmoteSource> get() = SOURCE_ORDER.filter { source -> groups.any { it.source == source } }

    fun groupsOf(source: EmoteSource): List<EmoteGroup> = groups.filter { it.source == source }

    /**
     * Emotes whose name contains [query] (ignoring case), prefix matches first, then in the lists' order; locked emotes are left out and a name
     * that exists in several lists is returned once (the first one, which is the one chat shows). Blank [query]: nothing.
     */
    fun search(query: String, limit: Int = Int.MAX_VALUE): List<Emote> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val usable = groups.asSequence().filter { it.section != EmoteSection.Locked }.flatMap { it.emotes }
        val seen = HashSet<String>()
        val matches = usable.filter { it.name.contains(q, ignoreCase = true) && seen.add(it.name) }.toList()
        val (prefix, inside) = matches.partition { it.name.startsWith(q, ignoreCase = true) }
        val recentNames = recent.mapTo(HashSet()) { it.name }
        val ordered = prefix.sortedBy { if (it.name in recentNames) 0 else 1 } + inside
        return ordered.take(limit)
    }

    companion object {
        val SOURCE_ORDER = listOf(EmoteSource.Twitch, EmoteSource.SevenTv, EmoteSource.Bttv, EmoteSource.Ffz, EmoteSource.Other)
    }
}

/** The `:name` being typed at the end of the text before [cursor], or null: `:` must start the text or follow a space, then at least two name characters. */
fun emoteQueryAt(text: String, cursor: Int): EmoteQuery? {
    if (cursor !in 1..text.length) return null
    var start = cursor
    while (start > 0 && isEmoteChar(text[start - 1])) start--
    if (start == 0 || text[start - 1] != ':') return null
    val colon = start - 1
    if (colon > 0 && !text[colon - 1].isWhitespace()) return null
    val name = text.substring(start, cursor)
    return if (name.length >= MIN_QUERY) EmoteQuery(colon, cursor, name) else null
}

/** The text between [from] (the colon) and [to] (the cursor) is replaced by an emote name when a suggestion is picked. */
data class EmoteQuery(val from: Int, val to: Int, val name: String)

private const val MIN_QUERY = 2

private fun isEmoteChar(c: Char) = c.isLetterOrDigit() || c == '_'

/** [text] with [name] inserted at [cursor] (replacing the selection [selectionStart]..[cursor] if any), surrounded by single spaces. Returns the new text and cursor. */
fun insertEmote(text: String, selectionStart: Int, cursor: Int, name: String): Pair<String, Int> {
    val start = minOf(selectionStart, cursor).coerceIn(0, text.length)
    val end = maxOf(selectionStart, cursor).coerceIn(0, text.length)
    val before = text.substring(0, start)
    val after = text.substring(end)
    val lead = if (before.isEmpty() || before.last().isWhitespace()) "" else " "
    val trail = if (after.startsWith(" ")) "" else " "
    val inserted = "$lead$name$trail"
    val caret = before.length + lead.length + name.length + 1
    return (before + inserted + after) to caret
}

/** [text] with the `:query` replaced by the emote [name] and a trailing space. Returns the new text and cursor. */
fun completeEmote(text: String, query: EmoteQuery, name: String): Pair<String, Int> {
    val after = text.substring(query.to)
    val trail = if (after.startsWith(" ")) "" else " "
    val before = text.substring(0, query.from)
    return (before + name + trail + after) to (before.length + name.length + 1)
}
