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
import com.twitchplayer.tv.core.network.api.TokenSource
import com.twitchplayer.tv.core.network.api.TwitchApi
import com.twitchplayer.tv.core.network.api.twitchHttpClient
import com.twitchplayer.tv.core.network.auth.DeviceCodeAuth
import com.twitchplayer.tv.core.network.auth.TokenValidator
import com.twitchplayer.tv.core.platform.AppConfig
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
 * The platform-independent bindings of `:shared:core`. Each app must also provide: [AppConfig],
 * `HttpClientEngineFactory<*>`, `TokenStore`, `SnapshotStorage`, `ChatSettings` defaults via [SettingsDefaults], and the
 * two `DataStore<Preferences>` named by [DataStores].
 */
fun sharedCoreModule(): Module = module {
    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single { twitchHttpClient(get<HttpClientEngineFactory<*>>()) }
    single { DeviceCodeAuth(get(), get<AppConfig>().clientId) }
    single { TokenValidator(get(), get<AppConfig>().clientId) }
    single { SnapshotCache(get()) }
    single<SessionRepository> { TwitchSessionRepository(get(), get(), get(), get(), onSignedOut = get<SnapshotCache>()::clear) }
    single<TokenSource> { get<SessionRepository>() }
    single { TwitchApi(get(), get<AppConfig>().clientId, get()) }
    single<TwitchRepository> { TwitchRepositoryImpl(get(), get()) }
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
