package com.twitchplayer.tv.feature.player.data

import com.twitchplayer.tv.core.platform.AppConfig
import com.twitchplayer.tv.feature.player.data.chat.RecentMessages
import com.twitchplayer.tv.feature.player.data.emotes.BttvProvider
import com.twitchplayer.tv.feature.player.data.emotes.EmoteRepository
import com.twitchplayer.tv.feature.player.data.emotes.FfzProvider
import com.twitchplayer.tv.feature.player.data.emotes.SevenTvProvider
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The platform-independent bindings of `:shared:chat`. Needs from the app, besides `sharedCoreModule()` (which provides the
 * `HttpClient`, `TwitchApi`, `TokenSource`, `SessionRepository` and [AppConfig]):
 *
 * - `HttpClientEngineFactory<*>` (the chat connection builds its own WebSocket client on it)
 * - [com.twitchplayer.tv.core.platform.Logger]
 * - [EventStreamClientFactory]
 *
 * Third-party emote providers are listed here in precedence order: the first one wins a name collision.
 */
fun sharedChatModule(): Module = module {
    single {
        val streamClients = get<EventStreamClientFactory>()
        EmoteRepository(listOf(SevenTvProvider(get(), { streamClients.create() }), BttvProvider(get()), FfzProvider(get())))
    }
    single { ChatAssetsRepository(get(), get(), get()) }
    single<ChatAssetsSource> { get<ChatAssetsRepository>() }
    single<LiteChatAssets> { get<ChatAssetsRepository>() }
    single<ChatSource> { EventSubChatSource(tokens = get(), clientId = get<AppConfig>().clientId, engine = get(), logger = get(), history = RecentMessages(get())) }
}
