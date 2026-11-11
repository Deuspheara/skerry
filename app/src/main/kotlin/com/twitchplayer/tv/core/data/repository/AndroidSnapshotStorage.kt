package com.twitchplayer.tv.core.data.repository

import com.twitchplayer.tv.core.platform.SnapshotStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Snapshots as JSON files in [dir] (the app passes its cache directory's `snapshots` folder). */
class AndroidSnapshotStorage(private val dir: File) : SnapshotStorage {
    override suspend fun read(name: String): String? = withContext(Dispatchers.IO) {
        File(dir, "$name.json").takeIf { it.isFile }?.readText()
    }

    override suspend fun write(name: String, text: String) {
        withContext(Dispatchers.IO) {
            dir.mkdirs()
            val tmp = File(dir, "$name.json.tmp")
            tmp.writeText(text)
            check(tmp.renameTo(File(dir, "$name.json"))) { "Could not replace snapshot $name" }
        }
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) { dir.deleteRecursively() }
    }
}
