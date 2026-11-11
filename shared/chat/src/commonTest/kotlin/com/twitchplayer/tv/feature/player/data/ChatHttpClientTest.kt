package com.twitchplayer.tv.feature.player.data

import com.twitchplayer.tv.feature.player.data.chat.EventSubChatClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.pluginOrNull
import io.ktor.client.plugins.websocket.WebSockets
import kotlin.test.Test
import kotlin.test.assertNotNull

class ChatHttpClientTest {
    private val engine = object : HttpClientEngineFactory<MockEngineConfig> {
        override fun create(block: MockEngineConfig.() -> Unit): HttpClientEngine = MockEngine(
            MockEngineConfig().apply {
                addHandler { respondOk() }
                block()
            },
        )
    }

    @Test fun theChatClientIsBuiltOnThePlatformEngineWithWebSocketsAndJson() {
        val client = EventSubChatClient.createHttpClient(engine)
        try {
            assertNotNull(client.pluginOrNull(WebSockets), "EventSub is a WebSocket")
            assertNotNull(client.pluginOrNull(ContentNegotiation), "subscriptions are posted as JSON")
        } finally {
            client.close()
        }
    }
}
