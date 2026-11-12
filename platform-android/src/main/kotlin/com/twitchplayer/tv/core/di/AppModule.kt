package com.twitchplayer.tv.core.di

import android.app.ActivityManager
import android.util.Log
import com.twitchplayer.tv.core.data.model.AnimatedEmoteLimit
import com.twitchplayer.tv.core.data.model.ChatSettings
import com.twitchplayer.tv.core.data.model.PlayerEngine
import com.twitchplayer.tv.core.data.repository.AndroidSnapshotStorage
import com.twitchplayer.tv.core.network.auth.AndroidKeystoreTokenStore
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
import com.twitchplayer.tv.feature.player.ui.embed.PlayerConfigProvider
import com.twitchplayer.tv.feature.player.ui.embed.TwitchPlayerConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * What only the app knows (it comes from the app's `BuildConfig`): a library has no BuildConfig values. The TV app builds it
 * in `AppPlatformConfig.kt`; the phone app provides its own.
 */
data class PlatformConfig(
    /** Public Twitch OAuth client id (no secret exists). */
    val twitchClientId: String,
    /** The player engine a fresh install starts with (the Settings tile overrides it). */
    val defaultPlayerEngine: PlayerEngine,
    /** HTTPS origin and exact hostname of the hosted Twitch embed page; blank disables the WebView embed. */
    val playerHost: String,
    val playerParent: String,
)

/** Android bindings. The platform-independent ones are in `sharedCoreModule()`. */
fun coreModule(config: PlatformConfig) = module {
    // Platform pieces the shared module needs (see sharedCoreModule()).
    single<DeviceProfile> { AndroidDeviceProfile() }
    single<NetworkStatus> { AndroidNetworkStatus(androidContext()) }
    single<NumberFormatter> { AndroidNumberFormatter() }
    single { AppConfig(clientId = config.twitchClientId, defaultPlayerEngine = config.defaultPlayerEngine) }
    single<HttpClientEngineFactory<*>> { OkHttp }
    // Warn level survives R8's stripping of Log.v/d/i (see proguard-rules.pro).
    single<Logger> { Logger { tag, message -> Log.w(tag, message) } }
    single<TokenStore> { AndroidKeystoreTokenStore(androidContext()) }
    single<SnapshotStorage> { AndroidSnapshotStorage(File(androidContext().cacheDir, StorageNames.SNAPSHOT_DIRECTORY)) }
    single(DataStores.Settings) { androidContext().settingsStore }
    single(DataStores.VodProgress) { androidContext().vodProgressStore }
    single {
        val lowRam = androidContext().getSystemService(ActivityManager::class.java).isLowRamDevice
        SettingsDefaults(if (lowRam) ChatSettings(animatedLimit = AnimatedEmoteLimit.Few) else ChatSettings())
    }
    // 7TV's event stream is quiet between heartbeats: no read timeout (the client is closed by its collector).
    single { EventStreamClientFactory { HttpClient(OkHttp) { engine { config { readTimeout(0, TimeUnit.SECONDS) } } } } }
    single {
        PlayerConfigProvider(
            if (config.playerHost.isBlank() || config.playerParent.isBlank()) {
                null
            } else {
                runCatching { TwitchPlayerConfig(config.playerHost, config.playerParent) }.getOrNull()
            },
        )
    }
}
