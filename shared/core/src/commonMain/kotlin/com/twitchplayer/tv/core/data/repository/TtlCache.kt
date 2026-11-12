package com.twitchplayer.tv.core.data.repository

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Single-value cache with a time-to-live; concurrent callers share one in-flight load. Time is the wall clock by default
 * ([WallClockTimeSource]), which keeps running while the device sleeps: a value cached before the phone went in a pocket is stale when
 * it comes out. (The JVM's monotonic clock stops in deep sleep.)
 */
internal class TtlCache<T>(
    private val ttlMs: Long,
    private val timeSource: TimeSource = WallClockTimeSource(),
    /** A forced reload is skipped when the value was loaded less than this ago: two screens refreshing together share one request. */
    private val forceCoalesceMs: Long = 0,
) {
    private val mutex = Mutex()
    private var value: T? = null
    private var loadedAt: TimeMark? = null

    suspend fun get(force: Boolean = false, load: suspend () -> T): T = mutex.withLock {
        val cached = value
        if (cached != null && (if (force) isYoungerThan(forceCoalesceMs) else isFresh())) return cached
        load().also {
            value = it
            loadedAt = timeSource.markNow()
        }
    }

    private fun isFresh(): Boolean = isYoungerThan(ttlMs)

    private fun isYoungerThan(ms: Long): Boolean = loadedAt?.let { it.elapsedNow().inWholeMilliseconds < ms } == true

    suspend fun invalidate() = mutex.withLock { value = null }
}
