package com.twitchplayer.tv.platform.apple

import com.twitchplayer.tv.core.platform.SnapshotStorage
import com.twitchplayer.tv.core.platform.StorageNames
import com.twitchplayer.tv.core.platform.ioDispatcher
import kotlinx.cinterop.BooleanVar
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.withContext
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.OsFamily
import kotlin.native.Platform

/** Where the apps keep their files; one place so tests and Swift agree. */
@OptIn(ExperimentalNativeApi::class)
object ApplePaths {
    /**
     * Settings and replay positions: backed up, not purgeable ("Application Support") on iOS and macOS. tvOS apps may only write to Caches and
     * tmp (the system can purge them under storage pressure), so there the same files live in a `support` folder of Caches: a purge resets the
     * settings and the replay positions (the account is in the Keychain, which is not affected).
     */
    fun supportDirectory(): String = if (Platform.osFamily == OsFamily.TVOS) {
        (directory(NSCachesDirectory) + "/support").also(::ensureDirectory)
    } else {
        directory(NSApplicationSupportDirectory).also(::ensureDirectory)
    }

    /** Screen snapshots: the system may purge it ("Caches"). */
    fun snapshotDirectory(): String = (directory(NSCachesDirectory) + "/" + StorageNames.SNAPSHOT_DIRECTORY).also(::ensureDirectory)

    private fun directory(kind: ULong): String = (NSSearchPathForDirectoriesInDomains(kind, NSUserDomainMask, true).firstOrNull() as? String) ?: error("No directory $kind")
}

/** Creates [path] (and its parents) when missing; a failure is reported with the system's reason instead of surfacing later as a missing file. */
internal fun ensureDirectory(path: String) {
    memScoped {
        val isDirectory = alloc<BooleanVar>()
        val exists = NSFileManager.defaultManager.fileExistsAtPath(path, isDirectory.ptr)
        if (!exists || !isDirectory.value) {
            val error = alloc<ObjCObjectVar<NSError?>>()
            val created = NSFileManager.defaultManager.createDirectoryAtPath(path, withIntermediateDirectories = true, attributes = null, error = error.ptr)
            check(created) { "Could not create the directory $path: ${error.value?.localizedDescription}" }
        }
    }
}

/** Snapshots as JSON files in [directory] (the Caches `snapshots` folder). Never throws for a missing entry. */
class AppleSnapshotStorage(private val directory: String = ApplePaths.snapshotDirectory()) : SnapshotStorage {
    override suspend fun read(name: String): String? = withContext(ioDispatcher) {
        NSString.stringWithContentsOfFile(path(name), NSUTF8StringEncoding, null)
    }

    override suspend fun write(name: String, text: String) {
        withContext(ioDispatcher) {
            ensureDirectory(directory)
            val written = NSString.create(string = text).writeToFile(path(name), atomically = true, encoding = NSUTF8StringEncoding, error = null)
            check(written) { "Could not write snapshot $name" }
        }
    }

    override suspend fun clear() {
        withContext(ioDispatcher) {
            NSFileManager.defaultManager.removeItemAtPath(directory, null)
        }
    }

    private fun path(name: String) = "$directory/$name.json"
}
