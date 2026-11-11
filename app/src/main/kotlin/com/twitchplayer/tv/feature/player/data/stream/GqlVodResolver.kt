package com.twitchplayer.tv.feature.player.data.stream

import android.util.Log
import androidx.compose.runtime.Immutable
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.random.Random

/**
 * What the player needs to start a replay. [masterUrl] is the adaptive playlist (usher, token embedded), [variants] its
 * renditions best first. [durationMs] is 0 when Twitch did not say; [recording] is true while the broadcast is still being
 * recorded (the playlist then has no end tag and grows).
 */
@Immutable
data class ResolvedVod(
    val vodId: String,
    val masterUrl: String,
    val variants: List<StreamVariant>,
    val durationMs: Long = 0L,
    val recording: Boolean = false,
)

fun interface VodResolver {
    /**
     * Throws [VodAccessException] when the replay is gone, restricted or Twitch refuses, and `java.io.IOException` once the
     * network stayed down through all retries.
     */
    suspend fun resolve(vodId: String): ResolvedVod
}

/** Why a replay cannot be played; each maps to one short line on the player screen. */
enum class VodFailure { NOT_FOUND, SUBSCRIBER_ONLY, TOKEN, UNAVAILABLE }

/** [status] and [body] are exactly what Twitch answered (empty when it answered nothing useful); [integrity] marks the integrity-check refusal. */
class VodAccessException(
    val failure: VodFailure,
    val stage: String,
    val status: Int = 0,
    val body: String = "",
    val integrity: Boolean = false,
) : Exception("vod $stage: $failure HTTP $status${if (integrity) " (integrity check)" else ""}: ${body.take(300)}")

/**
 * Resolves a replay the way Twitch's web player does, like [GqlPlaybackResolver] does for live channels:
 * 1. POST a raw GraphQL query for the video's access token (and its length) to gql.twitch.tv;
 * 2. GET the master playlist from usher.ttvnw.net/vod/<id>.m3u8 (the older `/vod/v2/<id>.m3u8` with `nauth` / `nauthsig`,
 *    which Streamlink and Xtra use today, is the one fallback when the first answers anything but "restricted");
 * 3. parse its variants.
 *
 * Checked live on 2026-11-08 (102 public VODs, muted and still-recording ones included): no integrity token needed. The
 * Client-Id is Twitch's public web player id and no viewer token is sent, same as the live and clip resolvers.
 * Subscriber-only replays cannot be played: Twitch's unofficial interface rejects the app's own login token (HTTP 401 "token is
 * invalid" with either client id, tried on a real account on 2026-11-08), so the request stays anonymous and usher answers 403.
 * Same retry rule: network errors and 5xx / 429 retry with backoff, everything else surfaces at once with the exact answer logged.
 */
class GqlVodResolver(
    private val http: HttpClient,
    private val clientId: String = GqlPlaybackResolver.WEB_CLIENT_ID,
    private val gqlUrl: String = GqlPlaybackResolver.GQL_URL,
    private val usherUrl: String = USHER_URL,
    private val retry: RetryPolicy = RetryPolicy(),
    private val deviceId: String = UUID.randomUUID().toString().replace("-", ""),
    private val random: Random = Random.Default,
    private val log: (String) -> Unit = { Log.w(TAG, it) },
) : VodResolver {

    override suspend fun resolve(vodId: String): ResolvedVod {
        require(ID.matches(vodId)) { "Invalid video id." }
        try {
            return resolveAs(vodId)
        } catch (e: StreamAccessException) {
            // Retries ran out on a 5xx / 429.
            throw VodAccessException(VodFailure.UNAVAILABLE, e.stage, e.status, e.body)
        }
    }

    private suspend fun resolveAs(vodId: String): ResolvedVod {
        val access = retry.run { accessToken(vodId) }
        val master = retry.run { fetchMaster(vodId, access) }
        val variants = MasterPlaylistParser.parse(master.text)
        if (variants.isEmpty()) throw refusal(VodFailure.UNAVAILABLE, "usher", 200, master.text)
        return ResolvedVod(vodId, master.url, variants, access.durationMs, access.recording)
    }

    internal fun usherUrlFor(vodId: String, token: VodAccess, v2: Boolean): String {
        val query = linkedMapOf(
            if (v2) "nauthsig" to token.signature else "sig" to token.signature,
            if (v2) "nauth" to token.value else "token" to token.value,
            "allow_source" to "true",
            "player_backend" to "mediaplayer",
            "playlist_include_framerate" to "true",
            "supported_codecs" to "avc1",
            "p" to random.nextInt(1_000_000, 10_000_000).toString(),
        )
        val base = if (v2) "$usherUrl/v2/$vodId.m3u8" else "$usherUrl/$vodId.m3u8"
        return base + "?" + query.entries.joinToString("&") { (k, v) -> "$k=${java.net.URLEncoder.encode(v, "UTF-8")}" }
    }

    private suspend fun accessToken(vodId: String): VodAccess {
        val body = Json.encodeToString(GqlPlaybackResolver.GqlRequest.serializer(), GqlPlaybackResolver.GqlRequest(QUERY, mapOf("id" to vodId)))
        val response = http.post(gqlUrl) {
            header("Client-Id", clientId)
            header("X-Device-Id", deviceId)
            setBody(TextContent(body, ContentType.Application.Json))
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw if (response.status.value >= 500 || response.status.value == 429) {
                log("vod gql refused: HTTP ${response.status.value} ${text.take(500)}")
                StreamAccessException("gql", response.status.value, text, retryable = true)
            } else {
                refusal(VodFailure.TOKEN, "gql", response.status.value, text)
            }
        }
        return parseVodAccess(text) ?: throw refusal(VodFailure.TOKEN, "gql", response.status.value, text)
    }

    private suspend fun fetchMaster(vodId: String, access: VodAccess): Answer {
        val first = master(usherUrlFor(vodId, access, v2 = false))
        if (first.status in 200..299) return first
        if (isRestricted(first.status, first.text)) throw refusal(VodFailure.SUBSCRIBER_ONLY, "usher", first.status, first.text)
        if (first.status >= 500 || first.status == 429) throw StreamAccessException("usher", first.status, first.text, retryable = true)
        // The one fallback: the newer path with the renamed parameters.
        val second = master(usherUrlFor(vodId, access, v2 = true))
        if (second.status in 200..299) return second
        if (isRestricted(second.status, second.text)) throw refusal(VodFailure.SUBSCRIBER_ONLY, "usher", second.status, second.text)
        if (second.status >= 500 || second.status == 429) throw StreamAccessException("usher", second.status, second.text, retryable = true)
        throw refusal(if (second.status == 404) VodFailure.NOT_FOUND else VodFailure.UNAVAILABLE, "usher", second.status, second.text)
    }

    private class Answer(val status: Int, val text: String, val url: String)

    private suspend fun master(url: String): Answer {
        val response = http.get(url) { header("Client-Id", clientId) }
        return Answer(response.status.value, response.bodyAsText(), url)
    }

    private fun refusal(failure: VodFailure, stage: String, status: Int, body: String, integrity: Boolean = false): VodAccessException {
        // warn survives R8's log stripping: the exact answer is what is needed to diagnose a refusal.
        log("vod $stage refused: $failure HTTP $status integrity=$integrity body=${body.take(2_000)}")
        return VodAccessException(failure, stage, status, body, integrity)
    }

    internal class VodAccess(val value: String, val signature: String, val durationMs: Long, val recording: Boolean)

    companion object {
        const val USHER_URL = "https://usher.ttvnw.net/vod"
        private const val TAG = "VodResolver"
        private val ID = Regex("[0-9]{1,20}")

        /** Raw query, no persisted-query hash. The video node carries the length for the seek bar (and RECORDING while live). */
        const val QUERY =
            "query VodAccess(\$id: ID!) { " +
                "videoPlaybackAccessToken(id: \$id, params: {platform: \"web\", playerBackend: \"mediaplayer\", playerType: \"site\"}) { value signature } " +
                "video(id: \$id) { lengthSeconds status } }"

        /** Usher answers HTTP 403 `vod_manifest_restricted` for subscriber-only replays (checked 2026-11-08). */
        internal fun isRestricted(status: Int, body: String): Boolean = status == 403 && body.contains("vod_manifest_restricted")
    }
}

@Serializable private class VodResponse(val data: VodData? = null, val errors: List<VodError>? = null)

@Serializable private class VodData(val videoPlaybackAccessToken: VodToken? = null, val video: VodVideo? = null)

@Serializable private class VodToken(val value: String = "", val signature: String = "")

@Serializable private class VodVideo(val lengthSeconds: Long = 0, val status: String = "")

@Serializable private class VodError(val message: String = "")

@Serializable private class VodTokenValue(val authorization: VodAuthorization? = null)

@Serializable private class VodAuthorization(val forbidden: Boolean = false, val reason: String = "")

private val vodJson = Json { ignoreUnknownKeys = true }

/**
 * Reads the answer of the access query. Null when the video or its token is missing (deleted, never existed, private).
 * Throws [VodAccessException] for the integrity refusal and for a token that says the viewer is forbidden.
 */
internal fun parseVodAccess(json: String): GqlVodResolver.VodAccess? {
    val response = runCatching { vodJson.decodeFromString(VodResponse.serializer(), json) }.getOrNull() ?: return null
    response.errors?.firstOrNull { it.message.contains("integrity", ignoreCase = true) }?.let {
        throw VodAccessException(VodFailure.TOKEN, "gql", 200, json, integrity = true)
    }
    val data = response.data ?: return null
    val token = data.videoPlaybackAccessToken?.takeIf { it.value.isNotEmpty() && it.signature.isNotEmpty() }
        ?: throw VodAccessException(VodFailure.NOT_FOUND, "gql", 200, json)
    val forbidden = runCatching { vodJson.decodeFromString(VodTokenValue.serializer(), token.value) }.getOrNull()?.authorization?.forbidden == true
    if (forbidden) throw VodAccessException(VodFailure.SUBSCRIBER_ONLY, "gql", 200, json)
    return GqlVodResolver.VodAccess(token.value, token.signature, (data.video?.lengthSeconds ?: 0L) * 1000, data.video?.status.equals("RECORDING", ignoreCase = true))
}
