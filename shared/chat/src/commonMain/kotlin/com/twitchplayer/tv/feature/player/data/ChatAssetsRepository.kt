package com.twitchplayer.tv.feature.player.data

import com.twitchplayer.tv.core.network.api.TwitchApi
import com.twitchplayer.tv.core.network.api.TwitchBadgeSet
import com.twitchplayer.tv.feature.player.data.emotes.Emote
import com.twitchplayer.tv.feature.player.data.emotes.EmoteRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.map

/**
 * What a chat line needs to be drawn: badge image URLs by "set:version" and third-party emotes by word.
 * Twitch's own emotes need no lookup: the message names the emote id and its formats, and the CDN URL is a template.
 */
data class ChatAssets(val badges: Map<String, String>, val emotes: Map<String, Emote>) {
    companion object {
        val Empty = ChatAssets(emptyMap(), emptyMap())
    }
}

fun interface ChatAssetsSource {
    /** A first snapshot soon after the call, then a new one whenever the channel's emote sets change. Never throws. */
    fun assets(broadcasterId: String): Flow<ChatAssets>
}

class ChatAssetsRepository(private val api: TwitchApi, private val emotes: EmoteRepository) : ChatAssetsSource {
    private var globalBadges: Map<String, String>? = null

    override fun assets(broadcasterId: String): Flow<ChatAssets> = channelFlow {
        val badges = async { loadBadges(broadcasterId) }
        emotes.emotes(broadcasterId).map { ChatAssets(badges.await(), it) }.collect { send(it) }
    }

    private suspend fun loadBadges(broadcasterId: String): Map<String, String> = coroutineScope {
        val global = async { globalBadges ?: runCatching { badgeUrls(api.globalBadges().data) }.getOrNull()?.also { globalBadges = it } ?: emptyMap() }
        val channel = async { runCatching { badgeUrls(api.channelBadges(broadcasterId).data) }.getOrDefault(emptyMap()) }
        global.await() + channel.await()
    }

    private fun badgeUrls(sets: List<TwitchBadgeSet>): Map<String, String> = sets.flatMap { set -> set.versions.map { "${set.setId}:${it.id}" to it.image2x } }.toMap()
}
