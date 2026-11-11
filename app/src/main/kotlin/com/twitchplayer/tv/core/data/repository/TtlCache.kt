package com.twitchplayer.tv.core.data.repository

import android.os.SystemClock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Single-value cache with a time-to-live; concurrent callers share one in-flight load. */
internal class TtlCache<T>(private val ttlMs: Long) {
    private val mutex = Mutex()
    private var value: T? = null
    private var loadedAt = 0L

    suspend fun get(force: Boolean = false, load: suspend () -> T): T = mutex.withLock {
        val cached = value
        val now = SystemClock.elapsedRealtime()
        if (!force && cached != null && now - loadedAt < ttlMs) return cached
        load().also {
            value = it
            loadedAt = SystemClock.elapsedRealtime()
        }
    }

    suspend fun invalidate() = mutex.withLock { value = null }
}
