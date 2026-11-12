package com.twitchplayer.tv.feature.player.data

import com.twitchplayer.tv.core.data.repository.SCOPE_READ_EMOTES
import com.twitchplayer.tv.core.data.repository.SessionRepository
import com.twitchplayer.tv.core.data.repository.SessionState
import com.twitchplayer.tv.core.network.api.TwitchApi
import com.twitchplayer.tv.core.network.api.TwitchBadgeSet
import com.twitchplayer.tv.core.network.api.TwitchEmote
import com.twitchplayer.tv.core.platform.WallClock
import com.twitchplayer.tv.feature.player.data.emotes.Emote
import com.twitchplayer.tv.feature.player.data.emotes.EmoteGroup
import com.twitchplayer.tv.feature.player.data.emotes.EmoteRepository
import com.twitchplayer.tv.feature.player.data.emotes.EmoteSection
import com.twitchplayer.tv.feature.player.data.emotes.EmoteSource
import com.twitchplayer.tv.feature.player.data.emotes.UserEmoteAccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * What a chat line needs to be drawn: badge image URLs by "set:version" and third-party emotes by word.
 * Twitch's own emotes need no lookup: the message names the emote id and its formats, and the CDN URL is a template.
 */
data class ChatAssets(
    val badges: Map<String, String>,
    val emotes: Map<String, Emote>,
    /** The same emotes as the picker's lists (Twitch's own first, then 7TV, BTTV, FFZ), with what the viewer may use of Twitch's. */
    val groups: List<EmoteGroup> = emptyList(),
    val access: UserEmoteAccess = UserEmoteAccess.Unavailable,
) {
    companion object {
        val Empty = ChatAssets(emptyMap(), emptyMap())
    }
}

fun interface ChatAssetsSource {
    /** A first snapshot soon after the call, then a new one whenever the channel's emote sets change. Never throws. */
    fun assets(broadcasterId: String): Flow<ChatAssets>
}

class ChatAssetsRepository(
    private val api: TwitchApi,
    private val emotes: EmoteRepository,
    private val session: SessionRepository,
    private val now: () -> Long = WallClock.System::nowMillis,
) : ChatAssetsSource {
    private var globalBadges: Map<String, String>? = null
    private var globalEmotes: List<TwitchEmote>? = null
    private var userEmotes: CachedUserEmotes? = null

    private class CachedUserEmotes(val userId: String, val at: Long, val emotes: List<TwitchEmote>)

    override fun assets(broadcasterId: String): Flow<ChatAssets> = channelFlow {
        val badges = async { loadBadges(broadcasterId) }
        val twitch = MutableStateFlow(TwitchEmoteLists())
        // Twitch's own lists load next to the providers' and join when they arrive: chat never waits for them.
        launch { twitch.value = loadTwitchEmotes(broadcasterId) }
        combine(emotes.snapshots(broadcasterId), twitch) { snapshot, own ->
            ChatAssets(badges.await(), snapshot.emotes, own.groups + snapshot.groups, own.access)
        }.collect { send(it) }
    }

    private suspend fun loadTwitchEmotes(broadcasterId: String): TwitchEmoteLists = coroutineScope {
        val global = async { globalEmotes ?: guarded { api.globalEmotes().data }?.also { globalEmotes = it } }
        val channel = async { guarded { api.channelEmotes(broadcasterId).data } }
        val account = (session.state.value as? SessionState.SignedIn)?.account
        val access = when {
            account == null -> UserEmoteAccess.Unavailable
            !session.hasScope(SCOPE_READ_EMOTES) -> UserEmoteAccess.NeedsSignIn
            else -> UserEmoteAccess.Known
        }
        val user = if (access == UserEmoteAccess.Known && account != null) async { loadUserEmotes(account.userId) } else null
        val own = user?.await()
        buildTwitchLists(global.await().orEmpty(), channel.await().orEmpty(), own, broadcasterId, if (access == UserEmoteAccess.Known && own == null) UserEmoteAccess.Unavailable else access)
    }

    /** All pages of the viewer's emotes (a long-time subscriber has thousands); null when the call failed. Kept [USER_TTL_MS] per account. */
    private suspend fun loadUserEmotes(userId: String): List<TwitchEmote>? {
        userEmotes?.takeIf { it.userId == userId && now() - it.at < USER_TTL_MS }?.let { return it.emotes }
        val all = guarded {
            val out = ArrayList<TwitchEmote>()
            var cursor: String? = null
            var pages = 0
            do {
                val page = api.userEmotes(userId, cursor)
                out += page.data
                cursor = page.pagination.cursor
            } while (cursor != null && ++pages < MAX_USER_PAGES)
            out
        }
        if (all != null) userEmotes = CachedUserEmotes(userId, now(), all)
        return all
    }

    /** A failing call only loses its own list. */
    private suspend fun <T> guarded(call: suspend () -> T): T? = try {
        call()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private suspend fun loadBadges(broadcasterId: String): Map<String, String> = coroutineScope {
        val global = async { globalBadges ?: runCatching { badgeUrls(api.globalBadges().data) }.getOrNull()?.also { globalBadges = it } ?: emptyMap() }
        val channel = async { runCatching { badgeUrls(api.channelBadges(broadcasterId).data) }.getOrDefault(emptyMap()) }
        global.await() + channel.await()
    }

    private fun badgeUrls(sets: List<TwitchBadgeSet>): Map<String, String> = sets.flatMap { set -> set.versions.map { "${set.setId}:${it.id}" to it.image2x } }.toMap()

    private companion object {
        const val USER_TTL_MS = 10 * 60_000L
        const val MAX_USER_PAGES = 30
    }
}

/** Twitch's own picker lists once loaded ([groups] is empty until then) and how much of the viewer's emotes are known. */
data class TwitchEmoteLists(val groups: List<EmoteGroup> = emptyList(), val access: UserEmoteAccess = UserEmoteAccess.Unavailable)

private val SUBSCRIBER_TYPES = setOf("subscriptions", "bitstier", "follower")

/**
 * Twitch's lists for the picker. With [user] (the viewer's own emotes) the channel's emotes are split into those the viewer may use
 * and [EmoteSection.Locked] ones, and the subscriber emotes of other channels are listed; without it every channel emote is
 * listed as usable (sending a name the viewer may not use just posts the word). Follower emotes are never called locked: the viewer's list may
 * not hold them.
 */
fun buildTwitchLists(
    global: List<TwitchEmote>,
    channel: List<TwitchEmote>,
    user: List<TwitchEmote>?,
    broadcasterId: String,
    access: UserEmoteAccess,
): TwitchEmoteLists {
    val groups = ArrayList<EmoteGroup>()
    fun add(section: EmoteSection, list: List<TwitchEmote>) {
        if (list.isNotEmpty()) groups += EmoteGroup(EmoteSource.Twitch, section, list.map { it.toEmote() })
    }
    if (user == null) {
        add(EmoteSection.Channel, channel)
    } else {
        val mine = user.mapTo(HashSet()) { it.id }

        // Follower emotes may be missing from the viewer's list (the endpoint does not promise every type): they are not called locked.
        fun usable(emote: TwitchEmote) = emote.id in mine || emote.emoteType == "follower"
        add(EmoteSection.Channel, channel.filter(::usable))
        add(EmoteSection.Locked, channel.filterNot(::usable))
    }
    add(EmoteSection.Global, global)
    if (user != null) {
        val known = (global + channel).mapTo(HashSet()) { it.id }
        add(EmoteSection.OtherChannels, user.filter { it.id !in known && it.ownerId != broadcasterId && it.emoteType in SUBSCRIBER_TYPES })
    }
    return TwitchEmoteLists(groups, access)
}

/** The documented CDN template; `static` always exists, `animated` only where the API lists it. */
internal fun TwitchEmote.toEmote(): Emote = Emote(
    name = name,
    url = "$EMOTE_CDN/$id/static/dark/2.0",
    animatedUrl = if ("animated" in format) "$EMOTE_CDN/$id/animated/dark/2.0" else null,
)

private const val EMOTE_CDN = "https://static-cdn.jtvnw.net/emoticons/v2"
