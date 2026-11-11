package com.twitchplayer.tv.feature.player.data.emotes

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json

internal val EmoteJson = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

internal class ProviderHttpException(val status: Int) : Exception("Emote provider answered $status")

/** GET and decode; a 404 is the provider's way to say "nothing for this channel" and returns null, any other failure throws. */
internal suspend inline fun <reified T> HttpClient.getOrNull(url: String): T? {
    val response = get(url)
    if (response.status == HttpStatusCode.NotFound) return null
    if (!response.status.isSuccess()) throw ProviderHttpException(response.status.value)
    return EmoteJson.decodeFromString<T>(response.bodyAsText())
}
