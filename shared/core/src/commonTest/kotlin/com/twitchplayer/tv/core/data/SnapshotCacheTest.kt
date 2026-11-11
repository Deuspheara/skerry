package com.twitchplayer.tv.core.data

import com.twitchplayer.tv.core.data.repository.SnapshotCache
import com.twitchplayer.tv.core.platform.SnapshotStorage
import com.twitchplayer.tv.core.platform.WallClock
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private class MemoryStorage : SnapshotStorage {
    val files = mutableMapOf<String, String>()
    override suspend fun read(name: String): String? = files[name]
    override suspend fun write(name: String, text: String) {
        files[name] = text
    }
    override suspend fun clear() = files.clear()
}

class SnapshotCacheTest {
    private val storage = MemoryStorage()
    private var now = 1_000L
    private val cache = SnapshotCache(storage, WallClock { now })
    private val serializer = ListSerializer(String.serializer())

    @Test fun roundTripsPerAccount() = runTest {
        cache.write("home", "u1", serializer, listOf("a", "b"))
        assertEquals(listOf("a", "b"), cache.read("home", "u1", serializer))
        assertNull(cache.read("home", "u2", serializer))
    }

    @Test fun staleEntriesAreIgnored() = runTest {
        cache.write("home", "u1", serializer, listOf("a"))
        now += 61_000
        assertNull(cache.read("home", "u1", serializer, maxAgeMs = 60_000))
        assertEquals(listOf("a"), cache.read("home", "u1", serializer, maxAgeMs = 62_000))
    }

    @Test fun clearAndDamagedEntriesNeverThrow() = runTest {
        cache.write("home", "u1", serializer, listOf("a"))
        cache.clear()
        assertNull(cache.read("home", "u1", serializer))
        storage.files["bad"] = "{not json"
        assertNull(cache.read("bad", "u1", serializer))
    }
}
