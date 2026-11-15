package com.twitchplayer.tv.feature.player.data.stream

import com.twitchplayer.tv.core.platform.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.random.Random

/**
 * Resolves a live channel the way Twitch's own web player does:
 * 1. POST a raw GraphQL query to gql.twitch.tv for the stream's playback access token (value + signature);
 * 2. GET the master playlist from usher.ttvnw.net with that token;
 * 3. parse its variants.
 *
 * This uses interfaces Twitch does not document (see LEGAL.md). The Client-Id is supplied by whoever builds the app
 * (`-PplaybackClientId`, empty in this repository) and is never hard-coded here; with it empty the resolvers refuse to run. Both steps retry network errors and 5xx with backoff; offline
 * channels and refusals do not retry, and a refusal always logs the exact HTTP status and body.
 */
class GqlPlaybackResolver(
    private val http: HttpClient,
    private val logger: Logger,
    private val clientId: String,
    private val gqlUrl: String = GQL_URL,
    private val usherUrl: String = USHER_URL,
    private val retry: RetryPolicy = RetryPolicy(),
    private val deviceId: String = newDeviceId(),
    private val random: Random = Random.Default,
) : TwitchPlaybackResolver {

    override suspend fun resolve(channel: String): ResolvedStream {
        requireConfigured(clientId)
        val login = channel.lowercase()
        require(LOGIN.matches(login)) { "Invalid Twitch channel login." }
        val token = retry.run { accessToken(login) }
        val url = usherUrlFor(login, token)
        val playlist = retry.run { fetchPlaylist(login, url) }
        val variants = MasterPlaylistParser.parse(playlist)
        if (variants.isEmpty()) throw StreamAccessException("usher", 200, playlist, retryable = false)
        return ResolvedStream(login, url, variants)
    }

    internal fun usherUrlFor(login: String, token: PlaybackToken): String {
        val query = linkedMapOf(
            "sig" to token.signature,
            "token" to token.value,
            "allow_source" to "true",
            "allow_audio_only" to "true",
            "player_backend" to "mediaplayer",
            "playlist_include_framerate" to "true",
            "supported_codecs" to "avc1",
            "p" to random.nextInt(1_000_000, 10_000_000).toString(),
        )
        return "$usherUrl/$login.m3u8?" + query.entries.joinToString("&") { (k, v) -> "$k=${formUrlEncode(v)}" }
    }

    private suspend fun accessToken(login: String): PlaybackToken {
        val body = Json.encodeToString(GqlRequest.serializer(), GqlRequest(TOKEN_QUERY, mapOf("login" to login, "playerType" to PLAYER_TYPE)))
        val response = http.post(gqlUrl) {
            header("Client-Id", clientId)
            header("X-Device-Id", deviceId)
            setBody(TextContent(body, ContentType.Application.Json))
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw refusal("gql", response.status.value, text, retryable = response.status.value >= 500 || response.status.value == 429)
        val parsed = runCatching { lenient.decodeFromString(GqlResponse.serializer(), text) }
            .getOrElse { throw refusal("gql", response.status.value, text) }
        parsed.errors?.firstOrNull()?.let { error ->
            val integrity = error.message.contains("integrity", ignoreCase = true)
            throw refusal("gql", response.status.value, text, integrity = integrity)
        }
        val token = parsed.data?.streamPlaybackAccessToken ?: throw refusal("gql", response.status.value, text)
        return PlaybackToken(token.value, token.signature)
    }

    private suspend fun fetchPlaylist(login: String, url: String): String {
        val response = http.get(url) { header("Client-Id", clientId) }
        val text = response.bodyAsText()
        if (response.status.value == 404 || (response.status.value == 403 && text.contains("transcode_does_not_exist"))) {
            logger.warn(TAG, "usher: $login offline (HTTP ${response.status.value})")
            throw ChannelOfflineException(login)
        }
        if (!response.status.isSuccess()) {
            throw refusal("usher", response.status.value, text, retryable = response.status.value >= 500 || response.status.value == 429)
        }
        return text
    }

    private fun refusal(stage: String, status: Int, body: String, integrity: Boolean = false, retryable: Boolean = false): StreamAccessException {
        // Warnings are logged on purpose: the exact answer is what is needed to diagnose a refused token.
        logger.warn(TAG, "$stage refused: HTTP $status integrity=$integrity body=${body.take(2_000)}")
        return StreamAccessException(stage, status, body, integrity, retryable)
    }

    internal class PlaybackToken(val value: String, val signature: String)

    @Serializable internal class GqlRequest(val query: String, val variables: Map<String, String>)

    @Serializable internal class GqlResponse(val data: GqlData? = null, val errors: List<GqlError>? = null)

    @Serializable internal class GqlData(@SerialName("streamPlaybackAccessToken") val streamPlaybackAccessToken: GqlToken? = null)

    @Serializable internal class GqlToken(val value: String, val signature: String)

    @Serializable internal class GqlError(val message: String = "")

    companion object {
        const val GQL_URL = "https://gql.twitch.tv/gql"
        const val USHER_URL = "https://usher.ttvnw.net/api/channel/hls"

        internal fun requireConfigured(clientId: String) = check(clientId.isNotBlank()) { "Native playback is not configured: set -PplaybackClientId (see LEGAL.md)." }

        private const val PLAYER_TYPE = "site"
        private const val TAG = "PlaybackResolver"
        private val lenient = Json { ignoreUnknownKeys = true }
        private val LOGIN = Regex("[a-z0-9_]{1,25}")

        /** A raw query string, not a persisted-query hash: nothing to keep in sync with the web player's bundle. */
        const val TOKEN_QUERY =
            "query PlaybackAccessToken(\$login: String!, \$playerType: String!) { " +
                "streamPlaybackAccessToken(channelName: \$login, params: {platform: \"web\", playerType: \$playerType}) { value signature } }"
    }
}
