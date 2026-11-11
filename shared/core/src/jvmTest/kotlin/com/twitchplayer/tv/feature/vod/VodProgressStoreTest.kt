package com.twitchplayer.tv.feature.vod

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.twitchplayer.tv.core.platform.WallClock
import com.twitchplayer.tv.feature.vod.data.DataStoreVodProgressStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VodProgressStoreTest {
    private val folder = createTempDirectory("vod-progress").toFile()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var now = 1_000L

    @AfterTest fun stop() {
        scope.cancel()
        folder.deleteRecursively()
    }

    private fun store(max: Int = 150): DataStoreVodProgressStore {
        val file = File(folder, "vod_${now++}.preferences_pb")
        return DataStoreVodProgressStore(PreferenceDataStoreFactory.create(scope = scope) { file }, max, WallClock { now++ })
    }

    @Test fun savedPositionsComeBackPerReplay() = runBlocking {
        val s = store()
        s.save("100", 83_000, 3_600_000)
        s.save("200", 5_000, 900_000)
        val all = s.progress.first()
        assertEquals(83_000L, all["100"]?.positionMs)
        assertEquals(3_600_000L, all["100"]?.durationMs)
        assertEquals(5_000L, all["200"]?.positionMs)
        assertNull(all["300"])
    }

    @Test fun watchingToTheEndForgetsTheReplay() = runBlocking {
        val s = store()
        s.save("100", 83_000, 600_000)
        s.save("100", 595_000, 600_000) // within the last 15 s
        assertNull(s.progress.first()["100"])
    }

    @Test fun clearForgetsOneReplayOnly() = runBlocking {
        val s = store()
        s.save("1", 40_000, 600_000)
        s.save("2", 50_000, 600_000)
        s.clear("1")
        assertEquals(setOf("2"), s.progress.first().keys)
    }

    @Test fun aZeroPositionIsNotStored() = runBlocking {
        val s = store()
        s.save("1", 0, 600_000)
        assertEquals(emptyMap(), s.progress.first())
    }

    @Test fun theOldestEntriesGoWhenTheStoreIsFull() = runBlocking {
        val s = store(max = 3)
        for (i in 1..5) s.save("v$i", 30_000L * i, 6_000_000)
        assertEquals(setOf("v3", "v4", "v5"), s.progress.first().keys)
    }
}
