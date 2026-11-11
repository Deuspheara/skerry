package com.twitchplayer.tv.core.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.twitchplayer.tv.core.MemoryTokenStore
import com.twitchplayer.tv.core.NoopPreferencesStore
import com.twitchplayer.tv.core.data.model.PlayerEngine
import com.twitchplayer.tv.core.data.repository.LiveWatcher
import com.twitchplayer.tv.core.data.repository.SessionRepository
import com.twitchplayer.tv.core.data.repository.SettingsRepository
import com.twitchplayer.tv.core.data.repository.SnapshotCache
import com.twitchplayer.tv.core.data.repository.TwitchRepository
import com.twitchplayer.tv.core.network.api.TokenSource
import com.twitchplayer.tv.core.network.api.TwitchApi
import com.twitchplayer.tv.core.network.auth.DeviceCodeAuth
import com.twitchplayer.tv.core.network.auth.TokenStore
import com.twitchplayer.tv.core.network.auth.TokenValidator
import com.twitchplayer.tv.core.platform.AppConfig
import com.twitchplayer.tv.core.platform.SettingsDefaults
import com.twitchplayer.tv.core.platform.SnapshotStorage
import com.twitchplayer.tv.feature.vod.data.VodProgressStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respondOk
import kotlinx.coroutines.CoroutineScope
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Exactly the platform bindings listed in [sharedCoreModule]'s KDoc; the graph must resolve against nothing else. */
private val fakePlatform = module {
    single { AppConfig(clientId = "cid", defaultPlayerEngine = PlayerEngine.WebView) }
    single<HttpClientEngineFactory<*>> {
        object : HttpClientEngineFactory<MockEngineConfig> {
            override fun create(block: MockEngineConfig.() -> Unit): HttpClientEngine = MockEngine(
                MockEngineConfig().apply {
                    addHandler { respondOk() }
                    block()
                },
            )
        }
    }
    single<TokenStore> { MemoryTokenStore() }
    single<SnapshotStorage> {
        object : SnapshotStorage {
            override suspend fun read(name: String): String? = null
            override suspend fun write(name: String, text: String) = Unit
            override suspend fun clear() = Unit
        }
    }
    single { SettingsDefaults() }
    single<DataStore<Preferences>>(DataStores.Settings) { NoopPreferencesStore }
    single<DataStore<Preferences>>(DataStores.VodProgress) { NoopPreferencesStore }
}

class SharedCoreModuleTest {
    @Test fun everySharedBindingResolvesAgainstThePlatformList() {
        val koin = startKoin { modules(sharedCoreModule(), fakePlatform) }.koin
        try {
            assertNotNull(koin.get<CoroutineScope>())
            assertNotNull(koin.get<HttpClient>())
            assertNotNull(koin.get<DeviceCodeAuth>())
            assertNotNull(koin.get<TokenValidator>())
            assertNotNull(koin.get<SnapshotCache>())
            val session = koin.get<SessionRepository>()
            assertEquals(session, koin.get<TokenSource>())
            assertNotNull(koin.get<TwitchApi>())
            assertNotNull(koin.get<TwitchRepository>())
            assertNotNull(koin.get<LiveWatcher>())
            assertNotNull(koin.get<SettingsRepository>())
            assertNotNull(koin.get<VodProgressStore>())
        } finally {
            stopKoin()
        }
    }
}
