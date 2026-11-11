package com.twitchplayer.tv.feature.player.data.emotes

import io.ktor.client.HttpClient
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * FrankerFaceZ REST v1 (https://api.frankerfacez.com): `/set/global` for the default sets, `/room/id/<twitch id>` for a
 * channel. `urls` are stills; an `animated` map (WebP) exists for animated emotes. FFZ "modifier" emotes transform the
 * previous emote (flip, rotate...) and are not supported: they stay plain text.
 */
class FfzProvider(private val http: HttpClient) : EmoteProvider {
    override suspend fun global(): List<Emote> {
        val global = http.getOrNull<FfzGlobal>("https://api.frankerfacez.com/v1/set/global") ?: return emptyList()
        return global.defaultSets.flatMap { global.sets[it.toString()]?.emoticons.orEmpty() }.mapNotNull { it.toEmote() }
    }

    override suspend fun channel(channelId: String): List<Emote> {
        val room = http.getOrNull<FfzRoom>("https://api.frankerfacez.com/v1/room/id/$channelId") ?: return emptyList()
        val set = room.room?.set ?: return emptyList()
        return room.sets[set.toString()]?.emoticons.orEmpty().mapNotNull { it.toEmote() }
    }
}

@Serializable internal data class FfzGlobal(@SerialName("default_sets") val defaultSets: List<Int> = emptyList(), val sets: Map<String, FfzSet> = emptyMap())

@Serializable internal data class FfzRoom(val room: FfzRoomInfo? = null, val sets: Map<String, FfzSet> = emptyMap())

@Serializable internal data class FfzRoomInfo(val set: Int? = null)

@Serializable internal data class FfzSet(val emoticons: List<FfzEmote> = emptyList())

@Serializable internal data class FfzEmote(
    val name: String,
    val width: Int = 0,
    val height: Int = 0,
    val modifier: Boolean = false,
    val urls: Map<String, String> = emptyMap(),
    val animated: Map<String, String>? = null,
)

private fun Map<String, String>.at2x(): String? = this["2"] ?: this["1"] ?: this["4"]

internal fun FfzEmote.toEmote(): Emote? {
    if (modifier) return null
    val still = urls.at2x() ?: return null
    return Emote(name, still, animatedUrl = animated?.at2x(), aspect = if (height > 0) width.toFloat() / height else 0f)
}
