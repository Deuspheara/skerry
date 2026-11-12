package com.twitchplayer.tv.platform.apple

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.twitchplayer.tv.core.platform.ioDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import okio.Path.Companion.toPath

/**
 * The two preference files, as in the Android app (`settings.preferences_pb` and `vod_progress.preferences_pb`, see
 * `StorageNames`), in the Application Support directory. One instance per file per process: DataStore forbids two active
 * instances on the same file, so the Koin bindings are singletons.
 */
object AppleDataStores {
    fun create(name: String, directory: String = ApplePaths.supportDirectory()): DataStore<Preferences> = PreferenceDataStoreFactory.createWithPath(
        scope = CoroutineScope(ioDispatcher + SupervisorJob()),
        produceFile = { "$directory/$name.preferences_pb".toPath() },
    )
}
