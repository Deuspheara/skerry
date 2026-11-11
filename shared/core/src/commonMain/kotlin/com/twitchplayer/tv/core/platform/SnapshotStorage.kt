package com.twitchplayer.tv.core.platform

/**
 * Named text blobs kept in the platform's cache area (the system may clear it). Implementations never throw for a
 * missing entry: [read] returns null. [write] replaces atomically where the platform allows it.
 */
interface SnapshotStorage {
    suspend fun read(name: String): String?
    suspend fun write(name: String, text: String)

    /** Removes every entry. */
    suspend fun clear()
}
