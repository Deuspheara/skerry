package com.twitchplayer.tv.core.data

import com.twitchplayer.tv.core.data.repository.AvatarStore
import com.twitchplayer.tv.core.data.repository.SnapshotCache
import com.twitchplayer.tv.core.platform.SnapshotStorage
import com.twitchplayer.tv.core.platform.WallClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AvatarStoreTest {
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
    private fun store() = AvatarStore(SnapshotCache(storage, clock, Dispatchers.Unconfined), clock, CoroutineScope(Dispatchers.Unconfined), debounceMs = 0)

    @Test fun knownIdsAreNotMissingAndTheRestIsDeduplicated() = runTest {
        val avatars = store()
        avatars.ensureLoaded()
        avatars.put(mapOf("1" to "u1"))
        assertEquals(listOf("2", "3"), avatars.missing(listOf("1", "2", "3", "2")))
        assertEquals(mapOf("1" to "u1"), avatars.urls())
    }

    @Test fun theMapSurvivesARestartSoACardNeedsNoRequest() = runTest {
        store().apply {
            ensureLoaded()
            put(mapOf("1" to "u1", "2" to "u2"))
        }
        val next = store()
        next.ensureLoaded()
        assertEquals(emptyList(), next.missing(listOf("1", "2")))
        assertEquals("u2", next.urls()["2"])
    }

    @Test fun anEntryOlderThanAWeekIsFetchedAgain() = runTest {
        store().apply {
            ensureLoaded()
            put(mapOf("1" to "u1"))
        }
        now += AvatarStore.TTL_MS + 1
        val next = store()
        next.ensureLoaded()
        assertEquals(listOf("1"), next.missing(listOf("1")))
        assertTrue(next.urls().isEmpty())
    }

    @Test fun anEntryYoungerThanAWeekIsKept() = runTest {
        store().apply {
            ensureLoaded()
            put(mapOf("1" to "u1"))
        }
        now += AvatarStore.TTL_MS - 1
        val next = store()
        next.ensureLoaded()
        assertEquals(emptyList(), next.missing(listOf("1")))
    }

    @Test fun whatWasFetchedWhileTheDiskCopyLoadsWins() = runTest {
        store().apply {
            ensureLoaded()
            put(mapOf("1" to "old"))
        }
        val next = store()
        next.put(mapOf("1" to "new"))
        next.ensureLoaded()
        assertEquals("new", next.urls()["1"])
    }

    @Test fun aDamagedFileIsIgnored() = runTest {
        storage.files["avatars"] = "not json"
        val avatars = store()
        avatars.ensureLoaded()
        assertEquals(listOf("1"), avatars.missing(listOf("1")))
    }

    @Test fun withoutADiskCopyItStillWorksInMemory() = runTest {
        val avatars = AvatarStore(null, clock)
        avatars.ensureLoaded()
        avatars.put(mapOf("1" to "u1"))
        assertEquals("u1", avatars.urls()["1"])
    }

    @Test fun aBurstOfBatchesIsOneWriteOfTheNewestMapAndNeverBlocksTheCaller() = runTest {
        var writes = 0
        val counting = object : SnapshotStorage {
            override suspend fun read(name: String): String? = storage.files[name]
            override suspend fun write(name: String, text: String) {
                writes++
                storage.files[name] = text
            }
            override suspend fun clear() = storage.files.clear()
        }
        val writer = CoroutineScope(coroutineContext + kotlinx.coroutines.Job())
        val avatars = AvatarStore(SnapshotCache(counting, clock, Dispatchers.Unconfined), clock, writer, debounceMs = 500)
        avatars.put(mapOf("1" to "u1"))
        avatars.put(mapOf("2" to "u2"))
        avatars.put(mapOf("3" to "u3"))
        assertEquals(0, writes, "the caller did not wait for the disk")
        testScheduler.advanceTimeBy(501)
        testScheduler.runCurrent()
        assertEquals(1, writes)
        val reread = AvatarStore(SnapshotCache(counting, clock, Dispatchers.Unconfined), clock, CoroutineScope(Dispatchers.Unconfined), debounceMs = 0)
        reread.ensureLoaded()
        assertEquals(mapOf("1" to "u1", "2" to "u2", "3" to "u3"), reread.urls())
        writer.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    @Test fun expiredEntriesAreNotWrittenBack() = runTest {
        val avatars = store()
        avatars.put(mapOf("old" to "u"))
        now += AvatarStore.TTL_MS + 1
        avatars.put(mapOf("new" to "u2"))
        val reread = store()
        reread.ensureLoaded()
        assertEquals(mapOf("new" to "u2"), reread.urls())
        assertTrue("old" !in storage.files["avatars"].orEmpty())
    }

    @Test fun clearForgetsEverythingInMemory() = runTest {
        val avatars = store()
        avatars.put(mapOf("1" to "u1"))
        avatars.clear()
        assertTrue(avatars.urls().isEmpty())
    }
}
