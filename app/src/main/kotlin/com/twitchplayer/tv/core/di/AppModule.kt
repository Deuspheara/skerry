package com.twitchplayer.tv.core.di

import com.twitchplayer.tv.BuildConfig
import com.twitchplayer.tv.core.data.repository.DataStoreSettingsRepository
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
import com.twitchplayer.tv.core.network.auth.TokenStore
import com.twitchplayer.tv.core.network.auth.TokenValidator
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val coreModule = module {
    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single { twitchHttpClient() }
    single { TokenStore(androidContext()) }
    single { DeviceCodeAuth(get(), BuildConfig.TWITCH_CLIENT_ID) }
    single { TokenValidator(get(), BuildConfig.TWITCH_CLIENT_ID) }
    single { SnapshotCache(androidContext()) }
    single<SessionRepository> { TwitchSessionRepository(get(), get(), get(), get(), onSignedOut = get<SnapshotCache>()::clear) }
    single<TokenSource> { get<SessionRepository>() }
    single { TwitchApi(get(), BuildConfig.TWITCH_CLIENT_ID, get()) }
    single<TwitchRepository> { TwitchRepositoryImpl(get(), get()) }
    single { com.twitchplayer.tv.core.data.repository.LiveWatcher(get(), get()) }
    single<SettingsRepository> { DataStoreSettingsRepository(androidContext()) }
    // Third-party emote providers in precedence order: the first one wins a name collision.
    single { EmoteRepository(listOf(SevenTvProvider(get()), BttvProvider(get()), FfzProvider(get()))) }
    single<ChatAssetsSource> { ChatAssetsRepository(get(), get()) }
    // Swap this binding for a client of your own server to resolve streams elsewhere.
    single<TwitchPlaybackResolver> { GqlPlaybackResolver(get()) }
    single<com.twitchplayer.tv.feature.player.data.stream.VodResolver> { com.twitchplayer.tv.feature.player.data.stream.GqlVodResolver(get()) }
    single<com.twitchplayer.tv.feature.vod.data.VodProgressStore> { com.twitchplayer.tv.feature.vod.data.DataStoreVodProgressStore(androidContext()) }
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
