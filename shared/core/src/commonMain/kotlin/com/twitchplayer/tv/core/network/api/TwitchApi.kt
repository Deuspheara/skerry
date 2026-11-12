package com.twitchplayer.tv.core.network.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

val TwitchJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
}

/** The HTTP engine is chosen per platform (OkHttp on Android) and injected. */
fun twitchHttpClient(engine: HttpClientEngineFactory<*>): HttpClient = HttpClient(engine) {
    install(ContentNegotiation) { json(TwitchJson) }
}

class TwitchApiException(val status: Int) : Exception("Twitch API error ($status)")

/**
 * Source of the bearer token; [refresh] is called once after a 401 and returns the new token or null.
 * [rejected] is the token the server refused: when it is no longer the current one, another caller already refreshed
 * and the current token is returned without a new refresh (parallel requests share one rotation).
 */
interface TokenSource {
    fun current(): String?
    suspend fun refresh(rejected: String? = null): String?
}

class TwitchApi(
    private val http: HttpClient,
    private val clientId: String,
    private val tokens: TokenSource,
) {
    private suspend fun call(path: String, token: String, params: HttpRequestBuilder.() -> Unit): HttpResponse = http.get("$BASE/$path") {
        header("Client-Id", clientId)
        header(HttpHeaders.Authorization, "Bearer $token")
        params()
    }

    private suspend fun raw(path: String, params: HttpRequestBuilder.() -> Unit): HttpResponse {
        val token = tokens.current() ?: throw TwitchApiException(401)
        var response = call(path, token, params)
        if (response.status.value == 401) {
            response = call(path, tokens.refresh(rejected = token) ?: throw TwitchApiException(401), params)
        }
        if (response.status.value !in 200..299) throw TwitchApiException(response.status.value)
        return response
    }

    /** POST with a JSON body; same one-refresh-on-401 rule as [raw]. Non-2xx throws [TwitchApiException]. */
    private suspend fun post(path: String, body: SendChatRequest): HttpResponse {
        val token = tokens.current() ?: throw TwitchApiException(401)
        suspend fun send(t: String) = http.post("$BASE/$path") {
            header("Client-Id", clientId)
            header(HttpHeaders.Authorization, "Bearer $t")
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        var response = send(token)
        if (response.status.value == 401) response = send(tokens.refresh(rejected = token) ?: throw TwitchApiException(401))
        if (response.status.value !in 200..299) throw TwitchApiException(response.status.value)
        return response
    }

    private suspend inline fun <reified T> request(path: String, noinline params: HttpRequestBuilder.() -> Unit = {}): T = raw(path, params).body()

    suspend fun me(): TwitchUser? = request<HelixPage<TwitchUser>>("users").data.firstOrNull()

    suspend fun usersByIds(ids: List<String>): List<TwitchUser> = if (ids.isEmpty()) emptyList() else request<HelixPage<TwitchUser>>("users") { ids.forEach { parameter("id", it) } }.data

    suspend fun userByLogin(login: String): TwitchUser? = request<HelixPage<TwitchUser>>("users") { parameter("login", login) }.data.firstOrNull()

    suspend fun followedChannels(userId: String, first: Int = 100): HelixPage<FollowedChannel> = request("channels/followed") {
        parameter("user_id", userId)
        parameter("first", first.coerceIn(1, 100))
    }

    suspend fun followedStreams(userId: String, first: Int = 100): HelixPage<TwitchStream> = request("streams/followed") {
        parameter("user_id", userId)
        parameter("first", first.coerceIn(1, 100))
    }

    suspend fun topCategories(first: Int = 20, cursor: String? = null): HelixPage<TwitchGame> = request("games/top") {
        parameter("first", first.coerceIn(1, 100))
        cursor?.let { parameter("after", it) }
    }

    suspend fun streams(first: Int = 20, categoryId: String? = null, language: String? = null): HelixPage<TwitchStream> = request("streams") {
        parameter("first", first.coerceIn(1, 100))
        categoryId?.let { parameter("game_id", it) }
        language?.let { parameter("language", it) }
    }

    suspend fun streamByLogin(login: String): TwitchStream? = request<HelixPage<TwitchStream>>("streams") { parameter("user_login", login) }.data.firstOrNull()

    suspend fun searchChannels(query: String, first: Int = 8): List<TwitchChannelSearch> = request<HelixPage<TwitchChannelSearch>>("search/channels") {
        parameter("query", query)
        parameter("first", first)
    }.data

    suspend fun searchCategories(query: String, first: Int = 6): List<TwitchGame> = request<HelixPage<TwitchGame>>("search/categories") {
        parameter("query", query)
        parameter("first", first)
    }.data

    /** One page of the channel's past broadcasts, newest first; pass the previous page's cursor as [after] for the next one. */
    suspend fun videos(userId: String, first: Int = 12, after: String? = null): HelixPage<TwitchVideo> = request("videos") {
        parameter("user_id", userId)
        parameter("type", "archive")
        parameter("first", first.coerceIn(1, 100))
        after?.let { parameter("after", it) }
    }

    /** The channel's most viewed clips (Helix default order). No scope needed. */
    suspend fun clips(broadcasterId: String, first: Int = 8): List<TwitchClip> = request<HelixPage<TwitchClip>>("clips") {
        parameter("broadcaster_id", broadcasterId)
        parameter("first", first.coerceIn(1, 100))
    }.data

    suspend fun chatSettings(broadcasterId: String): TwitchChatSettings? = request<HelixPage<TwitchChatSettings>>("chat/settings") { parameter("broadcaster_id", broadcasterId) }.data.firstOrNull()

    /** Needs `user:write:chat`. The caller must read `is_sent` / `drop_reason`: a 200 does not mean the message went out. */
    suspend fun sendChat(broadcasterId: String, senderId: String, message: String, replyToMessageId: String? = null): SendChatResult = post("chat/messages", SendChatRequest(broadcasterId, senderId, message, replyToMessageId)).body<HelixPage<SendChatResult>>().data.firstOrNull() ?: SendChatResult()

    /** Upcoming segments; empty when the channel has no schedule (Twitch answers 404 then). */
    suspend fun schedule(broadcasterId: String): List<ScheduleSegment> = try {
        request<ScheduleEnvelope>("schedule") {
            parameter("broadcaster_id", broadcasterId)
            parameter("first", 5)
        }.data.segments.orEmpty()
    } catch (e: TwitchApiException) {
        if (e.status == 404) emptyList() else throw e
    }

    suspend fun globalBadges(): HelixPage<TwitchBadgeSet> = request("chat/badges/global")
    suspend fun channelBadges(broadcasterId: String): HelixPage<TwitchBadgeSet> = request("chat/badges") { parameter("broadcaster_id", broadcasterId) }

    /** Twitch's global emotes (smilies, Kappa...). No scope. */
    suspend fun globalEmotes(): HelixPage<TwitchEmote> = request("chat/emotes/global")

    /** The channel's own emotes: subscriber tiers, follower and bits emotes. No scope; says nothing about what the viewer may use. */
    suspend fun channelEmotes(broadcasterId: String): HelixPage<TwitchEmote> = request("chat/emotes") { parameter("broadcaster_id", broadcasterId) }

    /**
     * The emotes [userId] may use across channels (own subscriptions, bits tiers...). Needs `user:read:emotes`; paged by [after]. Twitch removed
     * the `broadcaster_id` parameter in July 2024 and the list may not hold every emote type (follower emotes in particular).
     */
    suspend fun userEmotes(userId: String, after: String? = null): HelixPage<TwitchEmote> = request("chat/emotes/user") {
        parameter("user_id", userId)
        after?.let { parameter("after", it) }
    }

    private companion object {
        const val BASE = "https://api.twitch.tv/helix"
    }
}
