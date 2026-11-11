package com.twitchplayer.tv.feature.player.data

import com.twitchplayer.tv.BuildConfig
import com.twitchplayer.tv.core.network.api.TokenSource
import com.twitchplayer.tv.feature.player.data.chat.ChatEvent
import com.twitchplayer.tv.feature.player.data.chat.EventSubChatClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch

/** Live chat of one channel as a cold flow: collecting connects, cancelling disconnects. */
fun interface ChatSource {
    fun events(broadcasterId: String, userId: String): Flow<ChatEvent>
}

class EventSubChatSource(private val tokens: TokenSource) : ChatSource {
    override fun events(broadcasterId: String, userId: String): Flow<ChatEvent> = channelFlow {
        val http = EventSubChatClient.createHttpClient()
        val client = EventSubChatClient(http, BuildConfig.TWITCH_CLIENT_ID, { tokens.current().orEmpty() })
        launch { client.run(broadcasterId, userId) }
        try {
            client.events.collect { send(it) }
        } finally {
            client.stop()
            http.close()
        }
    }
}
