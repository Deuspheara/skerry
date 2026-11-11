package com.twitchplayer.tv.core.di

import android.app.ActivityManager
import android.util.Log
import com.twitchplayer.tv.BuildConfig
import com.twitchplayer.tv.core.data.model.AnimatedEmoteLimit
import com.twitchplayer.tv.core.data.model.ChatSettings
import com.twitchplayer.tv.core.data.model.PlayerEngine
import com.twitchplayer.tv.core.data.model.buildDefault
import com.twitchplayer.tv.core.data.repository.AndroidSnapshotStorage
import com.twitchplayer.tv.core.network.auth.AndroidKeystoreTokenStore
import com.twitchplayer.tv.core.network.auth.TokenStore
import com.twitchplayer.tv.core.platform.AppConfig
import com.twitchplayer.tv.core.platform.Logger
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

/** Android bindings. The platform-independent ones are in `sharedCoreModule()`. */
val coreModule = module {
    // Platform pieces the shared module needs (see sharedCoreModule()).
    single { AppConfig(clientId = BuildConfig.TWITCH_CLIENT_ID, defaultPlayerEngine = PlayerEngine.buildDefault()) }
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
            if (BuildConfig.TWITCH_PLAYER_HOST.isBlank() || BuildConfig.TWITCH_PLAYER_PARENT.isBlank()) {
                null
            } else {
                runCatching { TwitchPlayerConfig(BuildConfig.TWITCH_PLAYER_HOST, BuildConfig.TWITCH_PLAYER_PARENT) }.getOrNull()
            },
        )
    }
}
