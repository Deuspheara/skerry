package com.twitchplayer.tv.core.data

import com.twitchplayer.tv.core.data.repository.AvatarStore
import com.twitchplayer.tv.core.data.repository.SnapshotCache
import com.twitchplayer.tv.core.platform.SnapshotStorage
import com.twitchplayer.tv.core.platform.WallClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
    private fun store() = AvatarStore(SnapshotCache(storage, clock, Dispatchers.Unconfined), clock)

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
}
