package com.twitchplayer.tv.feature.player.data.emotes

import io.ktor.client.HttpClient
import kotlinx.serialization.Serializable

/**
 * BetterTTV REST v3 (`cached` endpoints, https://api.betterttv.net). The CDN serves an animated WebP at
 * `/emote/<id>/2x.webp` and a still one at `/emote/<id>/static/2x.webp`, as the BetterTTV client itself does.
 * Updates are refreshed by the repository's periodic reload (the BTTV socket is not used).
 */
class BttvProvider(private val http: HttpClient) : EmoteProvider {
    override suspend fun global(): List<Emote> =
        http.getOrNull<List<BttvEmote>>("https://api.betterttv.net/3/cached/emotes/global").orEmpty().map { it.toEmote() }

    override suspend fun channel(channelId: String): List<Emote> {
        val channel = http.getOrNull<BttvChannel>("https://api.betterttv.net/3/cached/users/twitch/$channelId") ?: return emptyList()
        return (channel.channelEmotes + channel.sharedEmotes).map { it.toEmote() }
    }
}

@Serializable internal data class BttvEmote(
    val id: String, val code: String, val animated: Boolean = false, val width: Int = 0, val height: Int = 0, val modifier: Boolean = false,
)
@Serializable internal data class BttvChannel(val channelEmotes: List<BttvEmote> = emptyList(), val sharedEmotes: List<BttvEmote> = emptyList())

/** BTTV zero-width emotes are the ones its API flags `modifier`. */
internal fun BttvEmote.toEmote(): Emote {
    val animatedUrl = "https://cdn.betterttv.net/emote/$id/2x.webp"
    return Emote(
        name = code,
        url = if (animated) "https://cdn.betterttv.net/emote/$id/static/2x.webp" else animatedUrl,
        animatedUrl = if (animated) animatedUrl else null,
        zeroWidth = modifier,
        aspect = if (height > 0) width.toFloat() / height else 0f,
    )
}
