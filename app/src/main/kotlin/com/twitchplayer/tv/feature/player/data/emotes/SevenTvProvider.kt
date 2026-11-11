package com.twitchplayer.tv.feature.player.data.emotes

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.readLine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 7TV, REST v3 + EventAPI v3 (https://github.com/SevenTV/EventAPI, server-sent events, the documented default).
 * Zero-width ("overlay") emotes are flagged on the emote (1 shl 8) and on its activation in the set (1).
 */
class SevenTvProvider(private val http: HttpClient, private val eventsHttp: () -> HttpClient = ::eventsClient) : EmoteProvider {
    private val setIds = ConcurrentHashMap<String, String>()

    override suspend fun global(): List<Emote> =
        http.getOrNull<SevenTvSet>("https://7tv.io/v3/emote-sets/global")?.emotes.orEmpty().mapNotNull { it.toEmote() }

    override suspend fun channel(channelId: String): List<Emote> {
        val set = http.getOrNull<SevenTvUser>("https://7tv.io/v3/users/twitch/$channelId")?.emoteSet ?: return emptyList()
        if (set.id.isNotEmpty()) setIds[channelId] = set.id
        return set.emotes.mapNotNull { it.toEmote() }
    }

    /** Follows the channel's set until cancelled; reconnects with backoff, and quietly gives up on a set it never learned. */
    override fun changes(channelId: String): Flow<EmoteChange> = channelFlow {
        val setId = setIds[channelId] ?: return@channelFlow
        val client = eventsHttp()
        try {
            var backoff = 2_000L
            while (true) {
                try {
                    client.prepareGet("https://events.7tv.io/v3@emote_set.update<object_id=$setId>") {
                        header(HttpHeaders.Accept, "text/event-stream")
                    }.execute { response ->
                        val lines = response.bodyAsChannel()
                        var event = ""
                        while (true) {
                            // The server heartbeats every ~46 s: three missed beats means the connection is dead.
                            val line = withTimeoutOrNull(DEAD_AFTER_MS) { lines.readLine() } ?: break
                            when {
                                line.startsWith("event:") -> event = line.removePrefix("event:").trim()
                                line.startsWith("data:") && event == "dispatch" ->
                                    parseSevenTvChange(line.removePrefix("data:").trim())?.let { send(it) }
                                line.startsWith("data:") && event == "hello" -> backoff = 2_000L
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Retry below: live updates are a nicety, the set fetched at start stays valid.
                }
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(60_000L)
            }
        } finally {
            client.close()
        }
    }

    private companion object {
        const val DEAD_AFTER_MS = 150_000L
        fun eventsClient() = HttpClient(OkHttp) { engine { config { readTimeout(0, TimeUnit.SECONDS) } } }
    }
}

@Serializable internal data class SevenTvUser(@SerialName("emote_set") val emoteSet: SevenTvSet? = null)
@Serializable internal data class SevenTvSet(val id: String = "", val emotes: List<SevenTvEmote> = emptyList())
@Serializable internal data class SevenTvEmote(val id: String, val name: String, val flags: Int = 0, val data: SevenTvData? = null)
@Serializable internal data class SevenTvData(val flags: Int = 0, val animated: Boolean = false, val host: SevenTvHost? = null)
@Serializable internal data class SevenTvHost(val url: String = "", val files: List<SevenTvFile> = emptyList())
@Serializable internal data class SevenTvFile(val name: String = "", val width: Int = 0, val height: Int = 0)

private const val ZERO_WIDTH_ACTIVE = 1
private const val ZERO_WIDTH_EMOTE = 1 shl 8

internal fun SevenTvEmote.toEmote(): Emote? {
    if (name.isBlank() || id.isBlank()) return null
    // host.url is protocol-relative ("//cdn.7tv.app/emote/<id>"); fall back to the canonical layout when a push omits it.
    val base = data?.host?.url?.takeIf { it.isNotEmpty() }?.let { if (it.startsWith("//")) "https:$it" else it } ?: "https://cdn.7tv.app/emote/$id"
    val animated = data?.animated == true
    val file = data?.host?.files?.firstOrNull { it.name.startsWith("2x.") }
    return Emote(
        name = name,
        url = if (animated) "$base/2x_static.webp" else "$base/2x.webp",
        animatedUrl = if (animated) "$base/2x.webp" else null,
        zeroWidth = flags and ZERO_WIDTH_ACTIVE != 0 || (data?.flags ?: 0) and ZERO_WIDTH_EMOTE != 0,
        aspect = if (file != null && file.height > 0) file.width.toFloat() / file.height else 0f,
    )
}

@Serializable private data class SevenTvDispatch(val type: String = "", val body: SevenTvBody? = null)
@Serializable private data class SevenTvBody(
    val pushed: List<SevenTvField> = emptyList(), val pulled: List<SevenTvField> = emptyList(), val updated: List<SevenTvField> = emptyList(),
)
@Serializable private data class SevenTvField(
    val key: String = "", val value: JsonElement? = null, @SerialName("old_value") val oldValue: JsonElement? = null,
)

/** One `emote_set.update` dispatch: pushed = added, pulled = removed, updated = renamed / re-flagged. Null when it changes no emote. */
internal fun parseSevenTvChange(data: String): EmoteChange? {
    val dispatch = runCatching { EmoteJson.decodeFromString<SevenTvDispatch>(data) }.getOrNull() ?: return null
    if (dispatch.type != "emote_set.update") return null
    val body = dispatch.body ?: return null
    fun JsonElement?.emote() = this?.let { runCatching { EmoteJson.decodeFromJsonElement<SevenTvEmote>(it) }.getOrNull() }
    val added = ArrayList<Emote>()
    val removed = HashSet<String>()
    body.pulled.filter { it.key == "emotes" }.forEach { f -> f.oldValue.emote()?.let { removed += it.name } }
    body.updated.filter { it.key == "emotes" }.forEach { f ->
        f.oldValue.emote()?.let { removed += it.name }
        f.value.emote()?.toEmote()?.let(added::add)
    }
    body.pushed.filter { it.key == "emotes" }.forEach { f -> f.value.emote()?.toEmote()?.let(added::add) }
    return if (added.isEmpty() && removed.isEmpty()) null else EmoteChange(added, removed)
}
