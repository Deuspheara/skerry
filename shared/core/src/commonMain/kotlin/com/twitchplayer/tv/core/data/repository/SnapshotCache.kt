package com.twitchplayer.tv.core.data.repository

import com.twitchplayer.tv.core.platform.SnapshotStorage
import com.twitchplayer.tv.core.platform.WallClock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Last good copy of a screen's data on disk, per account, so the screen can show it on the next start while the fresh
 * copy loads (stale-while-revalidate). Lives in the platform cache area ([SnapshotStorage]): the system may clear it,
 * which only costs one normal load. Entries older than [maxAgeMs] are ignored. Reads and writes never throw.
 */
class SnapshotCache(
    private val storage: SnapshotStorage,
    private val clock: WallClock = WallClock.System,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun <T> read(name: String, userId: String, serializer: KSerializer<T>, maxAgeMs: Long = DAY_MS): T? = runCatching {
        val text = storage.read(name) ?: return@runCatching null
        val entry = json.decodeFromString(Entry.serializer(serializer), text)
        entry.data.takeIf { entry.userId == userId && clock.nowMillis() - entry.savedAtMs < maxAgeMs }
    }.getOrNull()

    suspend fun <T> write(name: String, userId: String, serializer: KSerializer<T>, data: T) {
        runCatching {
            storage.write(name, json.encodeToString(Entry.serializer(serializer), Entry(userId, clock.nowMillis(), data)))
        }
    }

    /** Drops every snapshot (sign-out). */
    suspend fun clear() {
        runCatching { storage.clear() }
    }

    @Serializable
    private class Entry<T>(val userId: String, val savedAtMs: Long, val data: T)

    private companion object {
        const val DAY_MS = 24 * 60 * 60_000L
    }
}
