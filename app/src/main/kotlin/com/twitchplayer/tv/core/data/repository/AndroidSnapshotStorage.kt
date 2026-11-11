package com.twitchplayer.tv.core.data.repository

import android.content.Context
import com.twitchplayer.tv.core.platform.SnapshotStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Snapshots as JSON files in the app cache directory. */
class AndroidSnapshotStorage(context: Context) : SnapshotStorage {
    private val dir = File(context.cacheDir, "snapshots")

    override suspend fun read(name: String): String? = withContext(Dispatchers.IO) {
        File(dir, "$name.json").takeIf { it.isFile }?.readText()
    }

    override suspend fun write(name: String, text: String) {
        withContext(Dispatchers.IO) {
            dir.mkdirs()
            val tmp = File(dir, "$name.json.tmp")
            tmp.writeText(text)
            tmp.renameTo(File(dir, "$name.json"))
        }
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) { dir.deleteRecursively() }
    }
}
