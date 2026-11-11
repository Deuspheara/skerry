package com.twitchplayer.tv.core.data.repository

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** Single-value cache with a time-to-live; concurrent callers share one in-flight load. */
internal class TtlCache<T>(private val ttlMs: Long, private val timeSource: TimeSource = TimeSource.Monotonic) {
    private val mutex = Mutex()
    private var value: T? = null
    private var loadedAt: TimeMark? = null

    suspend fun get(force: Boolean = false, load: suspend () -> T): T = mutex.withLock {
        val cached = value
        if (!force && cached != null && isFresh()) return cached
        load().also {
            value = it
            loadedAt = timeSource.markNow()
        }
    }

    private fun isFresh(): Boolean = loadedAt?.let { it.elapsedNow().inWholeMilliseconds < ttlMs } == true

    suspend fun invalidate() = mutex.withLock { value = null }
}
