package com.twitchplayer.tv.core.data

import com.twitchplayer.tv.core.data.repository.LastSeenStore
import com.twitchplayer.tv.core.data.repository.SnapshotCache
import com.twitchplayer.tv.core.platform.SnapshotStorage
import com.twitchplayer.tv.core.platform.WallClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LastSeenStoreTest {
    private class Memory : SnapshotStorage {
        val files = mutableMapOf<String, String>()
        override suspend fun read(name: String): String? = files[name]
        override suspend fun write(name: String, text: String) {
            files[name] = text
        }
        override suspend fun clear() = files.clear()
    }

    private var now = 10_000_000_000L
    private val clock = WallClock { now }
    private val storage = Memory()
    private fun store() = LastSeenStore(SnapshotCache(storage, clock, Dispatchers.Unconfined), clock)

    @Test fun aChannelSeenLiveIsRememberedWithTheTimeItWasSeen() = runTest {
        val store = store()
        val start = now
        assertEquals(mapOf("1" to start), store.record("me", listOf("1")))
        now += 5_000
        val later = store.record("me", listOf("2"))
        assertEquals(start, later["1"], "a channel that went offline keeps the time it was last live")
        assertEquals(start + 5_000, later["2"])
    }

    @Test fun theMemorySurvivesARestart() = runTest {
        val start = now
        store().record("me", listOf("1"))
        now += 3_600_000
        assertEquals(start, store().record("me", emptyList())["1"])
    }

    @Test fun anotherAccountStartsEmpty() = runTest {
        val store = store()
        store.record("me", listOf("1"))
        assertNull(store.record("you", emptyList())["1"])
    }

    @Test fun entriesOlderThanSixtyDaysAreForgotten() = runTest {
        val store = store()
        store.record("me", listOf("1"))
        now += LastSeenStore.TTL_MS + 1
        assertNull(store.record("me", listOf("2"))["1"])
    }

    @Test fun theListIsBounded() = runTest {
        val ids = (1..LastSeenStore.MAX_ENTRIES + 20).map { "id$it" }
        assertEquals(LastSeenStore.MAX_ENTRIES, store().record("me", ids).size)
    }
}
