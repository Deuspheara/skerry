package com.twitchplayer.tv.core.data.repository

import com.twitchplayer.tv.core.platform.WallClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

/**
 * Profile pictures by channel id. They almost never change, so the map is kept on disk (through [snapshots], shared by every
 * account) and each entry lives [TTL_MS] (a week): a card shows its streamer's picture at once from a cold start, with no
 * request, and only the ids not seen in the last week are fetched again, in batches (see `TwitchRepositoryImpl.loadAvatars`).
 *
 * The disk copy is written off the request path: [put] only updates memory and signals a single writer, which waits [debounceMs] (a burst of
 * batches becomes one write), then writes the newest map without its expired entries. One writer means the writes happen in order.
 */
internal class AvatarStore(
    private val snapshots: SnapshotCache?,
    private val clock: WallClock = WallClock.System,
    private val writeScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val debounceMs: Long = DEBOUNCE_MS,
) {
    @Serializable
    class Entry(val url: String, val atMs: Long)

    @Serializable
    private class Persisted(val entries: Map<String, Entry>)

    private val entries = MutableStateFlow<Map<String, Entry>>(emptyMap())
    private val loadLock = Mutex()
    private var loaded = false

    /** Conflated: any number of [put]s while the writer sleeps count as one pending write. */
    private val pendingWrite = Channel<Unit>(Channel.CONFLATED)
    private var writerStarted = false

    /** Reads the persisted copy once (off the caller's thread: [SnapshotCache] does its IO on its own dispatcher). */
    suspend fun ensureLoaded() {
        if (loaded) return
        loadLock.withLock {
            if (loaded) return
            val stored = snapshots?.read(NAME, NO_USER, Persisted.serializer(), maxAgeMs = Long.MAX_VALUE)?.entries.orEmpty()
            val now = clock.nowMillis()
            val fresh = stored.filterValues { now - it.atMs < TTL_MS }
            // Anything fetched while the read was running wins over the disk copy.
            entries.update { current -> fresh + current }
            loaded = true
        }
    }

    /** Picture URLs by channel id, entries past their TTL left out. */
    fun urls(): Map<String, String> {
        val now = clock.nowMillis()
        return entries.value.filterValues { now - it.atMs < TTL_MS }.mapValues { it.value.url }
    }

    /** The ids of [ids] that have no usable picture yet. */
    fun missing(ids: Collection<String>): List<String> {
        val known = urls()
        return ids.filter { it !in known }.distinct()
    }

    /** Remembers [fetched] (id to url) and has the map written to disk soon; returns without waiting for the write. */
    fun put(fetched: Map<String, String>) {
        if (fetched.isEmpty()) return
        val now = clock.nowMillis()
        entries.update { current -> current + fetched.mapValues { Entry(it.value, now) } }
        if (snapshots == null) return
        startWriter(snapshots)
        pendingWrite.trySend(Unit)
    }

    /** Forgets every picture (the account ended): memory now, the disk copy goes with the snapshots. */
    fun clear() {
        entries.value = emptyMap()
        loaded = false
    }

    private fun startWriter(target: SnapshotCache) {
        if (writerStarted) return
        writerStarted = true
        writeScope.launch {
            while (pendingWrite.receiveCatching().isSuccess) {
                delay(debounceMs)
                val now = clock.nowMillis()
                target.write(NAME, NO_USER, Persisted.serializer(), Persisted(entries.value.filterValues { now - it.atMs < TTL_MS }))
            }
        }
    }

    companion object {
        const val TTL_MS = 7 * 24 * 60 * 60_000L
        const val DEBOUNCE_MS = 500L
        private const val NAME = "avatars"
        private const val NO_USER = ""
    }
}
