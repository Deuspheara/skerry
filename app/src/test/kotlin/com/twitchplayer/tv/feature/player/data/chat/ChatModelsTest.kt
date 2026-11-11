package com.twitchplayer.tv.feature.player.data.chat

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatModelsTest {
    @Test fun decodesTwitchEventSubMessageIdsBadgesAndFragments() {
        val json = Json { ignoreUnknownKeys = true }
        val message = json.decodeFromString<ChatMessage>(
            """
            {
              "broadcaster_user_id":"1971641",
              "chatter_user_id":"4145994",
              "chatter_user_login":"viewer32",
              "chatter_user_name":"Viewer32",
              "message_id":"cc106a89-1814-919d-454c-f4f2f970aae7",
              "message":{"text":"Hi Kappa","fragments":[
                {"type":"text","text":"Hi ","emote":null},
                {"type":"emote","text":"Kappa","emote":{"id":"25","emote_set_id":"0"}}
              ]},
              "color":"#00FF7F",
              "badges":[{"set_id":"moderator","id":"1","info":""}],
              "message_type":"text"
            }
            """.trimIndent(),
        )

        assertEquals("cc106a89-1814-919d-454c-f4f2f970aae7", message.id)
        assertEquals("moderator", message.badges.single().setId)
        assertEquals("Kappa", message.content.fragments.last().text)
        assertEquals("25", message.content.fragments.last().emote?.id)
    }
}
