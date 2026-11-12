package com.twitchplayer.tv.feature.player.data

import com.twitchplayer.tv.core.network.api.TokenSource
import com.twitchplayer.tv.core.platform.Logger
import com.twitchplayer.tv.feature.player.data.chat.ChatEvent
import com.twitchplayer.tv.feature.player.data.chat.EventSubChatClient
import io.ktor.client.engine.HttpClientEngineFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch

/** Live chat of one channel as a cold flow: collecting connects, cancelling disconnects. */
fun interface ChatSource {
    fun events(broadcasterId: String, userId: String): Flow<ChatEvent>
}

/** One EventSub connection per collection, over a client built on the platform's [engine]. */
class EventSubChatSource(
    private val tokens: TokenSource,
    private val clientId: String,
    private val engine: HttpClientEngineFactory<*>,
    private val logger: Logger,
) : ChatSource {
    override fun events(broadcasterId: String, userId: String): Flow<ChatEvent> = channelFlow {
        // The connection is signed with the stored token: it waits for the session to have read it (bounded, see TokenSource.awaitReady).
        tokens.awaitReady()
        val http = EventSubChatClient.createHttpClient(engine)
        val client = EventSubChatClient(http, clientId, { tokens.current().orEmpty() }, logger)
        launch { client.run(broadcasterId, userId) }
        try {
            client.events.collect { send(it) }
        } finally {
            client.stop()
            http.close()
        }
    }
}
