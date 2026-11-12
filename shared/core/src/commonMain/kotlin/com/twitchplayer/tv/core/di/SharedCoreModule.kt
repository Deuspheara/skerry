package com.twitchplayer.tv.core.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.twitchplayer.tv.core.data.repository.DataStoreSettingsRepository
import com.twitchplayer.tv.core.data.repository.LiveWatcher
import com.twitchplayer.tv.core.data.repository.SessionRepository
import com.twitchplayer.tv.core.data.repository.SettingsRepository
import com.twitchplayer.tv.core.data.repository.SnapshotCache
import com.twitchplayer.tv.core.data.repository.TwitchRepository
import com.twitchplayer.tv.core.data.repository.TwitchRepositoryImpl
import com.twitchplayer.tv.core.data.repository.TwitchSessionRepository
import com.twitchplayer.tv.core.data.repository.forgetAccountData
import com.twitchplayer.tv.core.network.api.TokenSource
import com.twitchplayer.tv.core.network.api.TwitchApi
import com.twitchplayer.tv.core.network.api.twitchHttpClient
import com.twitchplayer.tv.core.network.auth.DeviceCodeAuth
import com.twitchplayer.tv.core.network.auth.TokenValidator
import com.twitchplayer.tv.core.platform.AppConfig
import com.twitchplayer.tv.core.platform.ForegroundSignal
import com.twitchplayer.tv.core.platform.SettingsDefaults
import com.twitchplayer.tv.feature.vod.data.DataStoreVodProgressStore
import com.twitchplayer.tv.feature.vod.data.VodProgressStore
import io.ktor.client.engine.HttpClientEngineFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The platform-independent bindings of `:shared:core`. Every app must provide these platform bindings (a test resolves
 * the whole graph against fakes of exactly this list):
 *
 * - [AppConfig]
 * - `HttpClientEngineFactory<*>` (OkHttp on Android)
 * - `TokenStore` (secure storage of the OAuth tokens)
 * - `SnapshotStorage` (cache area for screen snapshots)
 * - [SettingsDefaults] (per-device starting settings)
 * - `DataStore<Preferences>` qualified by [DataStores.Settings]
 * - `DataStore<Preferences>` qualified by [DataStores.VodProgress]
 */
fun sharedCoreModule(): Module = module {
    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single { twitchHttpClient(get<HttpClientEngineFactory<*>>()) }
    single { DeviceCodeAuth(get(), get<AppConfig>().clientId) }
    single { TokenValidator(get(), get<AppConfig>().clientId) }
    single { SnapshotCache(get()) }
    single<SessionRepository> {
        val koin = this
        TwitchSessionRepository(get(), get(), get(), get(), onSignedOut = forgetAccountData(get(), get(), get()) { listOf(koin.get<TwitchRepositoryImpl>()) })
    }
    single<TokenSource> { get<SessionRepository>() }
    single { TwitchApi(get(), get<AppConfig>().clientId, get()) }
    single { TwitchRepositoryImpl(get(), get(), get()) }
    single<TwitchRepository> { get<TwitchRepositoryImpl>() }
    single { ForegroundSignal() }
    single { LiveWatcher(get(), get()) }
    single<SettingsRepository> {
        DataStoreSettingsRepository(
            store = get<DataStore<Preferences>>(DataStores.Settings),
            chatDefaults = get<SettingsDefaults>().chat,
            defaultPlayerEngine = get<AppConfig>().defaultPlayerEngine,
        )
    }
    single<VodProgressStore> { DataStoreVodProgressStore(get<DataStore<Preferences>>(DataStores.VodProgress)) }
}
