package com.twitchplayer.tv.core.data.repository

import com.twitchplayer.tv.core.platform.WallClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

/**
 * Profile pictures by channel id. They almost never change, so the map is kept on disk (through [snapshots], shared by every
 * account) and each entry lives [TTL_MS] (a week): a card shows its streamer's picture at once from a cold start, with no
 * request, and only the ids not seen in the last week are fetched again, in batches (see `TwitchRepositoryImpl.loadAvatars`).
 */
internal class AvatarStore(
    private val snapshots: SnapshotCache?,
    private val clock: WallClock = WallClock.System,
) {
    @Serializable
    class Entry(val url: String, val atMs: Long)

    @Serializable
    private class Persisted(val entries: Map<String, Entry>)

    private val entries = MutableStateFlow<Map<String, Entry>>(emptyMap())
    private val loadLock = Mutex()
    private var loaded = false

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

    /** Remembers [fetched] (id to url) and writes the map to disk. */
    suspend fun put(fetched: Map<String, String>) {
        if (fetched.isEmpty()) return
        val now = clock.nowMillis()
        entries.update { current -> current + fetched.mapValues { Entry(it.value, now) } }
        snapshots?.write(NAME, NO_USER, Persisted.serializer(), Persisted(entries.value))
    }

    companion object {
        const val TTL_MS = 7 * 24 * 60 * 60_000L
        private const val NAME = "avatars"
        private const val NO_USER = ""
    }
}
