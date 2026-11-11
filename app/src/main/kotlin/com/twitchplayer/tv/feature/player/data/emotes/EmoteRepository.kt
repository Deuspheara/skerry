package com.twitchplayer.tv.feature.player.data.emotes

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * Third-party emotes of a channel as one name -> [Emote] map that follows the channel's sets.
 *
 * [providers] are in precedence order (first wins a name collision). Channel emotes always beat global ones, as
 * in the providers' own clients. Providers fail independently: a failing one just has no layer (and is retried
 * on the next refresh), the others still show. Metadata is cached in memory (globals [GLOBAL_TTL_MS], channels
 * [CHANNEL_TTL_MS]) so zapping back to a channel does not refetch; images are cached by Coil.
 */
class EmoteRepository(
    private val providers: List<EmoteProvider>,
    private val now: () -> Long = System::currentTimeMillis,
    private val initialWaitMs: Long = INITIAL_WAIT_MS,
    private val refreshMs: Long = REFRESH_MS,
) {
    private class Cached(val at: Long, val emotes: List<Emote>)
    private val cache = ConcurrentHashMap<String, Cached>()

    /**
     * Emits once every provider answered (or [initialWaitMs] passed: slow providers must not hold chat back),
     * then again whenever a set changes. Never throws, never completes.
     */
    fun emotes(channelId: String): Flow<Map<String, Emote>> = channelFlow {
        val layers = MutableStateFlow<Map<Layer, List<Emote>>>(emptyMap())

        suspend fun load(index: Int, scope: Scope, ttl: Long, fetch: suspend () -> List<Emote>) {
            val key = "$index:${scope.name}:${if (scope == Scope.Channel) channelId else ""}"
            val hit = cache[key]?.takeIf { now() - it.at < ttl }
            val emotes = hit?.emotes ?: try {
                fetch().also { cache[key] = Cached(now(), it) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                return // keep whatever this layer had
            }
            layers.update { it + (Layer(index, scope) to emotes) }
        }

        val first = providers.mapIndexed { index, provider ->
            async {
                load(index, Scope.Global, GLOBAL_TTL_MS) { provider.global() }
                load(index, Scope.Channel, CHANNEL_TTL_MS) { provider.channel(channelId) }
            }
        }
        withTimeoutOrNull(initialWaitMs) { first.awaitAll() }
        send(merge(layers.value))
        launch { layers.collect { send(merge(it)) } }

        providers.forEachIndexed { index, provider ->
            // Live pushes (when the provider has them) are applied on top of the channel layer...
            launch {
                provider.changes(channelId).collect { change ->
                    layers.update { all ->
                        val key = Layer(index, Scope.Channel)
                        all + (key to applyChange(all[key].orEmpty(), change))
                    }
                }
            }
            // ...and every provider's channel set is re-read periodically (catches edits where there is no push).
            launch {
                while (true) {
                    delay(refreshMs)
                    load(index, Scope.Channel, 0L) { provider.channel(channelId) }
                }
            }
        }
        awaitClose()
    }.distinctUntilChanged()

    private enum class Scope { Global, Channel }
    private data class Layer(val provider: Int, val scope: Scope)

    private fun merge(layers: Map<Layer, List<Emote>>): Map<String, Emote> = mergeEmoteLayers(
        // Lowest precedence first; later puts win. Channel beats global, then provider order decides.
        Scope.entries.flatMap { scope -> providers.indices.reversed().map { layers[Layer(it, scope)].orEmpty() } },
    )

    companion object {
        const val INITIAL_WAIT_MS = 3_000L
        const val REFRESH_MS = 10 * 60_000L
        const val GLOBAL_TTL_MS = 60 * 60_000L
        const val CHANNEL_TTL_MS = 5 * 60_000L
    }
}

/** Layers from lowest to highest precedence: a name present in several keeps the last one's emote. Names are case-sensitive. */
internal fun mergeEmoteLayers(layers: List<List<Emote>>): Map<String, Emote> {
    val out = HashMap<String, Emote>()
    layers.forEach { layer -> layer.forEach { out[it.name] = it } }
    return out
}

internal fun applyChange(current: List<Emote>, change: EmoteChange): List<Emote> {
    val addedNames = change.added.mapTo(HashSet()) { it.name }
    return current.filter { it.name !in change.removed && it.name !in addedNames } + change.added
}
