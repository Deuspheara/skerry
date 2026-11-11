package com.twitchplayer.tv.core.data

import com.twitchplayer.tv.core.data.repository.AndroidSnapshotStorage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class AndroidSnapshotStorageTest {
    private val root = createTempDirectory("snapshots").toFile()
    private val dir = File(root, "snapshots")
    private val storage = AndroidSnapshotStorage(dir)

    @After fun cleanUp() {
        root.deleteRecursively()
    }

    @Test fun missingEntriesReadAsNull() = runBlocking {
        assertNull(storage.read("home"))
    }

    @Test fun writesAreReadBackAndReplaced() = runBlocking {
        storage.write("home", "one")
        storage.write("home", "two")
        assertEquals("two", storage.read("home"))
        assertEquals("no temporary file is left behind", listOf("home.json"), dir.list()!!.sorted())
    }

    @Test fun usesTheDocumentedFileNames() = runBlocking {
        storage.write("follows", "x")
        assertTrue(File(dir, "follows.json").isFile)
    }

    @Test fun clearRemovesEverythingAndTheStorageKeepsWorking() = runBlocking {
        storage.write("a", "1")
        storage.write("b", "2")
        storage.clear()
        assertNull(storage.read("a"))
        storage.write("c", "3")
        assertEquals("3", storage.read("c"))
    }
}
