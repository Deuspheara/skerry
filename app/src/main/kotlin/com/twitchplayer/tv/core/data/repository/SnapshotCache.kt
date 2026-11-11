package com.twitchplayer.tv.core.data.repository

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Last good copy of a screen's data on disk, per account, so the screen can show it on the next start while the fresh
 * copy loads (stale-while-revalidate). Lives in the cache directory: the system may clear it, which only costs one
 * normal load. Entries older than [maxAgeMs] are ignored. Reads and writes never throw.
 */
class SnapshotCache(context: Context) {
    private val dir = File(context.cacheDir, "snapshots")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun <T> read(name: String, userId: String, serializer: KSerializer<T>, maxAgeMs: Long = DAY_MS): T? = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(dir, "$name.json").takeIf { it.isFile } ?: return@runCatching null
            val entry = json.decodeFromString(Entry.serializer(serializer), file.readText())
            entry.data.takeIf { entry.userId == userId && System.currentTimeMillis() - entry.savedAtMs < maxAgeMs }
        }.getOrNull()
    }

    suspend fun <T> write(name: String, userId: String, serializer: KSerializer<T>, data: T) {
        withContext(Dispatchers.IO) {
            runCatching {
                dir.mkdirs()
                val tmp = File(dir, "$name.json.tmp")
                tmp.writeText(json.encodeToString(Entry.serializer(serializer), Entry(userId, System.currentTimeMillis(), data)))
                tmp.renameTo(File(dir, "$name.json"))
            }
        }
    }

    /** Drops every snapshot (sign-out). */
    suspend fun clear() {
        withContext(Dispatchers.IO) { runCatching { dir.deleteRecursively() } }
    }

    @Serializable
    private class Entry<T>(val userId: String, val savedAtMs: Long, val data: T)

    private companion object {
        const val DAY_MS = 24 * 60 * 60_000L
    }
}
