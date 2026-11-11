package com.twitchplayer.tv.core.di

import android.app.ActivityManager
import com.twitchplayer.tv.BuildConfig
import com.twitchplayer.tv.core.data.model.AnimatedEmoteLimit
import com.twitchplayer.tv.core.data.model.ChatSettings
import com.twitchplayer.tv.core.data.model.PlayerEngine
import com.twitchplayer.tv.core.data.model.buildDefault
import com.twitchplayer.tv.core.data.repository.AndroidSnapshotStorage
import com.twitchplayer.tv.core.network.auth.AndroidKeystoreTokenStore
import com.twitchplayer.tv.core.network.auth.TokenStore
import com.twitchplayer.tv.core.platform.AppConfig
import com.twitchplayer.tv.core.platform.SettingsDefaults
import com.twitchplayer.tv.core.platform.SnapshotStorage
import com.twitchplayer.tv.core.platform.StorageNames
import com.twitchplayer.tv.feature.player.data.ChatAssetsRepository
import com.twitchplayer.tv.feature.player.data.ChatAssetsSource
import com.twitchplayer.tv.feature.player.data.emotes.BttvProvider
import com.twitchplayer.tv.feature.player.data.emotes.EmoteRepository
import com.twitchplayer.tv.feature.player.data.emotes.FfzProvider
import com.twitchplayer.tv.feature.player.data.emotes.SevenTvProvider
import com.twitchplayer.tv.feature.player.data.stream.GqlPlaybackResolver
import com.twitchplayer.tv.feature.player.data.stream.TwitchPlaybackResolver
import com.twitchplayer.tv.feature.player.ui.embed.PlayerConfigProvider
import com.twitchplayer.tv.feature.player.ui.embed.TwitchPlayerConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import java.io.File

/** Android bindings. The platform-independent ones are in `sharedCoreModule()`. */
val coreModule = module {
    // Platform pieces the shared module needs (see sharedCoreModule()).
    single { AppConfig(clientId = BuildConfig.TWITCH_CLIENT_ID, defaultPlayerEngine = PlayerEngine.buildDefault()) }
    single<HttpClientEngineFactory<*>> { OkHttp }
    single<TokenStore> { AndroidKeystoreTokenStore(androidContext()) }
    single<SnapshotStorage> { AndroidSnapshotStorage(File(androidContext().cacheDir, StorageNames.SNAPSHOT_DIRECTORY)) }
    single(DataStores.Settings) { androidContext().settingsStore }
    single(DataStores.VodProgress) { androidContext().vodProgressStore }
    single {
        val lowRam = androidContext().getSystemService(ActivityManager::class.java).isLowRamDevice
        SettingsDefaults(if (lowRam) ChatSettings(animatedLimit = AnimatedEmoteLimit.Few) else ChatSettings())
    }
    // Third-party emote providers in precedence order: the first one wins a name collision.
    single { EmoteRepository(listOf(SevenTvProvider(get()), BttvProvider(get()), FfzProvider(get()))) }
    single<ChatAssetsSource> { ChatAssetsRepository(get(), get()) }
    // Swap this binding for a client of your own server to resolve streams elsewhere.
    single<TwitchPlaybackResolver> { GqlPlaybackResolver(get()) }
    single<com.twitchplayer.tv.feature.player.data.stream.VodResolver> { com.twitchplayer.tv.feature.player.data.stream.GqlVodResolver(get()) }
    single<com.twitchplayer.tv.feature.player.data.stream.ClipResolver> { com.twitchplayer.tv.feature.player.data.stream.GqlClipResolver(get()) }
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
