package com.twitchplayer.tv.platform.apple

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.twitchplayer.tv.core.data.model.PlayerEngine
import com.twitchplayer.tv.core.di.DataStores
import com.twitchplayer.tv.core.di.sharedCoreModule
import com.twitchplayer.tv.core.di.sharedPresentersModule
import com.twitchplayer.tv.core.network.auth.TokenStore
import com.twitchplayer.tv.core.platform.AppConfig
import com.twitchplayer.tv.core.platform.DeviceProfile
import com.twitchplayer.tv.core.platform.Logger
import com.twitchplayer.tv.core.platform.NetworkStatus
import com.twitchplayer.tv.core.platform.NumberFormatter
import com.twitchplayer.tv.core.platform.SettingsDefaults
import com.twitchplayer.tv.core.platform.SnapshotStorage
import com.twitchplayer.tv.core.platform.StorageNames
import com.twitchplayer.tv.feature.player.data.EventStreamClientFactory
import com.twitchplayer.tv.feature.player.data.sharedChatModule
import com.twitchplayer.tv.feature.player.data.stream.sharedStreamModule
import com.twitchplayer.tv.feature.search.presentation.VoiceInput
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.darwin.Darwin
import org.koin.core.module.Module
import org.koin.dsl.module

/** A request that never times out while 7TV's event stream is quiet (it heartbeats about every 46 s). */
private const val EVENT_STREAM_TIMEOUT_SECONDS = 24.0 * 60 * 60

/** The Apple bindings. The platform-independent ones come from the `shared*Module()` functions. */
fun appleModule(config: AppleConfig): Module = module {
    single { AppConfig(clientId = config.twitchClientId, defaultPlayerEngine = PlayerEngine.Fallback) }
    single<HttpClientEngineFactory<*>> { Darwin }
    single<Logger> { AppleLogger() }
    single<DeviceProfile> { AppleDeviceProfile() }
    single<NumberFormatter> { AppleNumberFormatter() }
    single<NetworkStatus> { AppleNetworkStatus() }
    single<TokenStore> { KeychainTokenStore(config.keychainService) }
    single<SnapshotStorage> { AppleSnapshotStorage() }
    single<DataStore<Preferences>>(DataStores.Settings) { AppleDataStores.create(StorageNames.SETTINGS_DATASTORE) }
    single<DataStore<Preferences>>(DataStores.VodProgress) { AppleDataStores.create(StorageNames.VOD_PROGRESS_DATASTORE) }
    single { SettingsDefaults() }
    single<VoiceInput> { UnavailableVoiceInput() }
    single {
        EventStreamClientFactory {
            HttpClient(Darwin) { engine { configureSession { timeoutIntervalForRequest = EVENT_STREAM_TIMEOUT_SECONDS } } }
        }
    }
}

/** The production Koin graph of the Apple apps, in load order (the same shared modules as Android's `productionModules`). */
fun appleModules(config: AppleConfig): List<Module> = listOf(
    sharedCoreModule(),
    sharedStreamModule(),
    sharedChatModule(),
    sharedPresentersModule(),
    appleModule(config),
)
