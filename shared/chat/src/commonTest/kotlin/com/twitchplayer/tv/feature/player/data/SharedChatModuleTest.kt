package com.twitchplayer.tv.feature.player.data

import com.twitchplayer.tv.core.data.model.PlayerEngine
import com.twitchplayer.tv.core.data.repository.SessionRepository
import com.twitchplayer.tv.core.data.repository.SessionState
import com.twitchplayer.tv.core.network.api.TokenSource
import com.twitchplayer.tv.core.network.api.TwitchApi
import com.twitchplayer.tv.core.platform.AppConfig
import com.twitchplayer.tv.core.platform.Logger
import com.twitchplayer.tv.feature.player.data.emotes.EmoteRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respondOk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What `sharedCoreModule()` provides plus the platform list in [sharedChatModule]'s KDoc: the graph must resolve on exactly that. */
private val fakePlatform = module {
    single { AppConfig(clientId = "cid", defaultPlayerEngine = PlayerEngine.WebView) }
    single { HttpClient(MockEngine { respondOk() }) }
    single<TokenSource> {
        object : TokenSource {
            override fun current(): String? = "token"
            override suspend fun refresh(rejected: String?): String? = null
        }
    }
    single { TwitchApi(get(), "cid", get()) }
    single<SessionRepository> {
        object : SessionRepository {
            override val state: StateFlow<SessionState> = MutableStateFlow(SessionState.SignedOut)
            override fun start() = Unit
            override suspend fun signIn(onCode: (com.twitchplayer.tv.core.network.auth.DeviceCodeResponse) -> Unit) = false
            override suspend fun signOut() = Unit
            override fun current(): String? = "token"
            override suspend fun refresh(rejected: String?): String? = null
        }
    }
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
    single { Logger { _, _ -> } }
    single { EventStreamClientFactory { HttpClient(MockEngine { respondOk() }) } }
}

class SharedChatModuleTest {
    @Test fun everyChatBindingResolvesAgainstTheDocumentedPlatformBindings() {
        val koin = startKoin { modules(sharedChatModule(), fakePlatform) }.koin
        try {
            assertTrue(koin.get<ChatSource>() is EventSubChatSource)
            assertTrue(koin.get<ChatAssetsSource>() is ChatAssetsRepository)
            val repository = koin.get<EmoteRepository>()
            assertEquals(repository, koin.get<EmoteRepository>(), "singleton")
        } finally {
            stopKoin()
        }
    }
}
