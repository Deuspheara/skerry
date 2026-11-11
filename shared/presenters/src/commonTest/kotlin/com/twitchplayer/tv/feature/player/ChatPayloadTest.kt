package com.twitchplayer.tv.feature.player

import com.twitchplayer.tv.feature.player.data.ChatAssets
import com.twitchplayer.tv.feature.player.data.chat.ChatMessage
import com.twitchplayer.tv.feature.player.data.chat.ChatNotification
import com.twitchplayer.tv.feature.player.presentation.ChatLineKind
import com.twitchplayer.tv.feature.player.presentation.ChatPiece
import com.twitchplayer.tv.feature.player.presentation.toLine
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The transport DTOs against the shape of Twitch's documented `channel.chat.message` / `channel.chat.notification` events (nulls included). */
class ChatPayloadTest {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test fun aDocumentedChatMessageEventDecodesAndRenders() {
        val event = """
            {"broadcaster_user_id":"1971641","broadcaster_user_login":"streamer","broadcaster_user_name":"streamer",
             "chatter_user_id":"4145994","chatter_user_login":"viewer32","chatter_user_name":"viewer32",
             "message_id":"cc106a89-1814-919d-454c-f4f2f970aae7",
             "message":{"text":"Hi chat Kappa @streamer cheer100","fragments":[
               {"type":"text","text":"Hi chat ","cheermote":null,"emote":null,"mention":null},
               {"type":"emote","text":"Kappa","cheermote":null,"emote":{"id":"25","emote_set_id":"0","owner_id":"0","format":["static"]},"mention":null},
               {"type":"text","text":" ","cheermote":null,"emote":null,"mention":null},
               {"type":"mention","text":"@streamer","cheermote":null,"emote":null,"mention":{"user_id":"1971641","user_name":"streamer","user_login":"streamer"}},
               {"type":"text","text":" ","cheermote":null,"emote":null,"mention":null},
               {"type":"cheermote","text":"cheer100","cheermote":{"prefix":"cheer","bits":100,"tier":1},"emote":null,"mention":null}]},
             "color":"#00FF7F","badges":[{"set_id":"moderator","id":"1","info":""},{"set_id":"subscriber","id":"12","info":"16"}],
             "message_type":"text","cheer":{"bits":100},"reply":null,"channel_points_custom_reward_id":null}
        """.trimIndent()
        val message = json.decodeFromString<ChatMessage>(event)
        val line = message.toLine(ChatAssets(mapOf("moderator:1" to "mod.png"), emptyMap()), "streamer", 0)
        assertEquals(listOf("mod.png"), line.badgeUrls)
        assertEquals(100, line.bits)
        assertEquals(ChatLineKind.Mention, line.kind)
        assertTrue(line.pieces.any { it is ChatPiece.Emote })
        assertEquals(ChatPiece.Strong("cheer100"), line.pieces.last())
    }

    @Test fun anEmptyColorAndMissingOptionalFieldsAreFine() {
        val message = json.decodeFromString<ChatMessage>(
            """{"message_id":"1","broadcaster_user_id":"b","chatter_user_id":"u","chatter_user_login":"a","chatter_user_name":"A","color":"","message":{"text":"hi","fragments":[{"type":"text","text":"hi"}]}}""",
        )
        assertEquals(null, message.cheer)
        assertTrue(message.toLine(ChatAssets.Empty, null, 0).color != 0L)
    }

    @Test fun aNotificationCarriesTwitchsOwnSentence() {
        val n = json.decodeFromString<ChatNotification>(
            """{"notice_type":"resub","system_message":"viewer32 subscribed for 3 months.","message":{"text":"thanks!","fragments":[]},"chatter_user_name":"viewer32"}""",
        )
        assertEquals("viewer32 subscribed for 3 months.", n.systemMessage)
        assertEquals("thanks!", n.content?.text)
    }
}
