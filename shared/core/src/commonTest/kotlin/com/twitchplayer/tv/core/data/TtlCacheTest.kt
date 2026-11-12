package com.twitchplayer.tv.core.data

import com.twitchplayer.tv.core.data.repository.TtlCache
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource

class TtlCacheTest {
    private val time = TestTimeSource()
    private var loads = 0
    private val cache = TtlCache<Int>(ttlMs = 1_000, timeSource = time)
    private val load: suspend () -> Int = { ++loads }

    @Test fun cachedWithinTheTtlAndReloadedAfterIt() = runTest {
        assertEquals(1, cache.get(load = load))
        time += 999.milliseconds
        assertEquals(1, cache.get(load = load))
        time += 1.milliseconds
        assertEquals(2, cache.get(load = load))
        assertEquals(2, loads)
    }

    @Test fun forceReloadsEvenWhenFresh() = runTest {
        cache.get(load = load)
        assertEquals(2, cache.get(force = true, load = load))
    }

    @Test fun invalidateDropsTheValue() = runTest {
        cache.get(load = load)
        cache.invalidate()
        assertEquals(2, cache.get(load = load))
    }

    @Test fun concurrentCallersShareOneLoad() = runTest {
        val results = List(5) { async { cache.get { delay(100).also { loads++ }.let { loads } } } }.awaitAll()
        assertEquals(1, loads)
        assertEquals(List(5) { 1 }, results)
    }

    @Test fun aForcedReloadRightAfterALoadIsCoalescedAndALaterOneIsNot() = runTest {
        val coalescing = TtlCache<Int>(ttlMs = 60_000, timeSource = time, forceCoalesceMs = 3_000)
        assertEquals(1, coalescing.get(load = load))
        time += 2_999.milliseconds
        assertEquals(1, coalescing.get(force = true, load = load), "two screens refreshing together share one load")
        time += 1.milliseconds
        assertEquals(2, coalescing.get(force = true, load = load))
        assertEquals(2, loads)
    }
}
