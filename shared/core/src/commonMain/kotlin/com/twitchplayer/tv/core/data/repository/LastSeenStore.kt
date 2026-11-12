package com.twitchplayer.tv.core.data.repository

import com.twitchplayer.tv.core.platform.WallClock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

/**
 * When this app last saw each followed channel live, per account. Twitch has no "last streamed" field for a whole follow list
 * (it would take one request per channel), so the follows call records who is live each time it runs; an offline channel's
 * entry is the last time it was in that list. Entries older than [TTL_MS] (60 days) are dropped, and at most [MAX_ENTRIES] are kept.
 */
internal class LastSeenStore(
    private val snapshots: SnapshotCache?,
    private val clock: WallClock = WallClock.System,
) {
    @Serializable
    private class Persisted(val seen: Map<String, Long>)

    private val lock = Mutex()
    private var userId: String? = null
    private var seen: Map<String, Long> = emptyMap()

    /** Marks [liveIds] as seen now and returns every known channel id to its last-seen time. */
    suspend fun record(user: String, liveIds: Collection<String>): Map<String, Long> = lock.withLock {
        if (userId != user) {
            seen = snapshots?.read(NAME, user, Persisted.serializer(), maxAgeMs = Long.MAX_VALUE)?.seen.orEmpty()
            userId = user
        }
        val now = clock.nowMillis()
        val merged = (seen.filterValues { now - it < TTL_MS } + liveIds.associateWith { now })
            .entries.sortedByDescending { it.value }.take(MAX_ENTRIES).associate { it.key to it.value }
        val changed = liveIds.isNotEmpty() || merged.size != seen.size
        seen = merged
        if (changed) snapshots?.write(NAME, user, Persisted.serializer(), Persisted(merged))
        merged
    }

    companion object {
        const val TTL_MS = 60 * 24 * 60 * 60_000L
        const val MAX_ENTRIES = 400
        private const val NAME = "last-seen"
    }
}
