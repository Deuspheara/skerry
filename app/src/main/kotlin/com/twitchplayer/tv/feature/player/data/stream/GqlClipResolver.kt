package com.twitchplayer.tv.feature.player.data.stream

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One playable file of a clip: [height] in pixels, [url] already signed for the viewer's request. */
data class ClipSource(val height: Int, val frameRate: Float, val url: String)

fun interface ClipResolver {
    /** Playable files of the clip with Helix id [slug], best first. Throws when Twitch refuses or the clip is gone. */
    suspend fun resolve(slug: String): List<ClipSource>
}

/**
 * Resolves a clip like Twitch's web player: one GraphQL query returns an access token (value + signature) and the MP4
 * address of each quality; the files only answer when the token is appended (`?sig=...&token=...`).
 * Helix cannot do this: it lists clips but gives no video address. Same unofficial interface, client id and failure
 * rule as [GqlPlaybackResolver]: a refusal throws and the screen says the clip cannot be played.
 */
class GqlClipResolver(
    private val http: HttpClient,
    private val clientId: String = GqlPlaybackResolver.WEB_CLIENT_ID,
    private val gqlUrl: String = GqlPlaybackResolver.GQL_URL,
) : ClipResolver {
    override suspend fun resolve(slug: String): List<ClipSource> {
        require(SLUG.matches(slug)) { "Invalid clip id." }
        val body = Json.encodeToString(GqlPlaybackResolver.GqlRequest.serializer(), GqlPlaybackResolver.GqlRequest(QUERY, mapOf("slug" to slug)))
        val response = http.post(gqlUrl) {
            header("Client-Id", clientId)
            setBody(TextContent(body, ContentType.Application.Json))
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            Log.w(TAG, "clip $slug refused: HTTP ${response.status.value} ${text.take(500)}")
            throw StreamAccessException("gql", response.status.value, text)
        }
        return parseClipSources(text).ifEmpty {
            Log.w(TAG, "clip $slug: no playable file in ${text.take(500)}")
            throw StreamAccessException("gql", response.status.value, text)
        }
    }

    companion object {
        private const val TAG = "ClipResolver"
        private val SLUG = Regex("[A-Za-z0-9_-]{1,100}")
        const val QUERY =
            "query ClipAccess(\$slug: ID!) { clip(slug: \$slug) { " +
                "playbackAccessToken(params: {platform: \"web\", playerType: \"site\"}) { value signature } " +
                "videoQualities { quality sourceURL frameRate } } }"
    }
}

@Serializable private class ClipResponse(val data: ClipData? = null)
@Serializable private class ClipData(val clip: ClipNode? = null)
@Serializable private class ClipNode(val playbackAccessToken: ClipToken? = null, val videoQualities: List<ClipQuality>? = null)
@Serializable private class ClipToken(val value: String = "", val signature: String = "")
@Serializable private class ClipQuality(val quality: String = "", val sourceURL: String = "", val frameRate: Float = 0f)

private val lenient = Json { ignoreUnknownKeys = true }

/** The signed files of a clip answer, highest quality first; empty when the clip or its token is missing. */
internal fun parseClipSources(json: String): List<ClipSource> {
    val clip = runCatching { lenient.decodeFromString(ClipResponse.serializer(), json) }.getOrNull()?.data?.clip ?: return emptyList()
    val token = clip.playbackAccessToken?.takeIf { it.value.isNotEmpty() && it.signature.isNotEmpty() } ?: return emptyList()
    return clip.videoQualities.orEmpty()
        .filter { it.sourceURL.isNotEmpty() }
        .map { ClipSource(it.quality.toIntOrNull() ?: 0, it.frameRate, signedClipUrl(it.sourceURL, token.value, token.signature)) }
        .sortedByDescending { it.height }
}

internal fun signedClipUrl(source: String, token: String, signature: String): String =
    source + (if ('?' in source) '&' else '?') + "sig=" + signature + "&token=" + java.net.URLEncoder.encode(token, "UTF-8")
