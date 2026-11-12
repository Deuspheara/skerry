package com.twitchplayer.tv.core.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.twitchplayer.tv.core.data.model.Account
import com.twitchplayer.tv.core.data.model.ChatSettings
import com.twitchplayer.tv.core.network.auth.TokenStore
import com.twitchplayer.tv.core.network.auth.TwitchTokens
import com.twitchplayer.tv.core.platform.SettingsDefaults
import com.twitchplayer.tv.core.platform.SnapshotStorage
import com.twitchplayer.tv.feature.search.presentation.VoiceInput
import kotlinx.coroutines.CoroutineScope
import org.koin.core.module.Module
import org.koin.dsl.module
import java.io.File

/**
 * Replaces the bindings of [coreModule] and [viewModelModule] that need an Android `Context` (secure token store, cache directory,
 * DataStores in the app's files directory, speech recognizer) with in-memory or temp-file fakes, so the real production graph can be
 * started in a plain JVM test. Used by the TV app's and the phone app's graph tests with [productionModules].
 *
 * @param directory a writable temp directory for the two DataStore files; @param scope the scope the DataStores run in (cancel it in `tearDown`).
 */
fun androidPlatformFakes(directory: File, scope: CoroutineScope): Module {
    fun dataStore(name: String): DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) { File(directory, "$name.preferences_pb") }
    return module {
        single<TokenStore> {
            object : TokenStore {
                override suspend fun save(tokens: TwitchTokens) = Unit
                override suspend fun read(): TwitchTokens? = null
                override suspend fun saveAccount(account: Account) = Unit
                override suspend fun readAccount(): Account? = null
                override suspend fun clear() = Unit
            }
        }
        single<SnapshotStorage> {
            object : SnapshotStorage {
                override suspend fun read(name: String): String? = null
                override suspend fun write(name: String, text: String) = Unit
                override suspend fun clear() = Unit
            }
        }
        single<com.twitchplayer.tv.core.platform.NetworkStatus> { com.twitchplayer.tv.core.platform.NetworkStatus.Unmetered }
        single(DataStores.Settings) { dataStore("settings") }
        single(DataStores.VodProgress) { dataStore("vod") }
        single { SettingsDefaults(ChatSettings()) }
        factory<VoiceInput> {
            object : VoiceInput {
                override fun isAvailable() = false
                override suspend fun listen(): String? = null
            }
        }
    }
}
