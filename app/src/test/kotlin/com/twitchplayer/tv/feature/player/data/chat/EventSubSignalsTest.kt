package com.twitchplayer.tv.feature.player.data.chat

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventSubSignalsTest {
    private fun decode(type: String, json: String) = EventSubChatClient.decodeSignal(type, Json.parseToJsonElement(json).jsonObject)

    @Test fun moderationEvents() {
        assertEquals(ChatEvent.Deleted("m1"), decode("channel.chat.message_delete", """{"message_id":"m1","target_user_login":"x"}"""))
        assertEquals(ChatEvent.UserCleared("troll"), decode("channel.chat.clear_user_messages", """{"target_user_login":"troll"}"""))
        assertEquals(ChatEvent.Cleared, decode("channel.chat.clear", "{}"))
        assertNull(decode("channel.chat.message_delete", "{}"))
    }

    @Test fun roomModesKeepTheirDurationsOnlyWhileOn() {
        val on = decode(
            "channel.chat_settings.update",
            """{"emote_mode":true,"follower_mode":true,"follower_mode_duration_minutes":10,"slow_mode":true,"slow_mode_wait_time_seconds":30,"subscriber_mode":true,"unique_chat_mode":false}""",
        ) as ChatEvent.Room
        assertEquals(RoomState(emoteOnly = true, followersOnlyMinutes = 10, slowSeconds = 30, subscribersOnly = true), on.state)
        val off = decode(
            "channel.chat_settings.update",
            """{"emote_mode":false,"follower_mode":false,"follower_mode_duration_minutes":10,"slow_mode":false,"slow_mode_wait_time_seconds":30,"subscriber_mode":false,"unique_chat_mode":false}""",
        ) as ChatEvent.Room
        assertEquals(RoomState(), off.state)
        assertTrue(!off.state.any)
        // Followers-only with no duration (null) means every follower.
        val anyFollower = decode("channel.chat_settings.update", """{"follower_mode":true,"follower_mode_duration_minutes":null}""") as ChatEvent.Room
        assertEquals(0, anyFollower.state.followersOnlyMinutes)
    }

    @Test fun channelEvents() {
        assertEquals(ChatEvent.Channel(ChannelNotice.Offline), decode("stream.offline", """{"broadcaster_user_id":"1"}"""))
        assertEquals(
            ChatEvent.Channel(ChannelNotice.Updated("New title", "Chess")),
            decode("channel.update", """{"title":"New title","category_name":"Chess","language":"en"}"""),
        )
        assertEquals(
            ChatEvent.Channel(ChannelNotice.Raid("A", "B", "b_login", 120)),
            decode("channel.raid", """{"from_broadcaster_user_name":"A","to_broadcaster_user_name":"B","to_broadcaster_user_login":"b_login","viewers":120}"""),
        )
        assertNull(decode("channel.hype_train.begin", "{}"))
    }

    @Test fun conditionsMatchTheTypes() {
        fun cond(type: String) = EventSubChatClient.conditionFor(type, "10", "20").toString()
        assertEquals("""{"broadcaster_user_id":"10"}""", cond("stream.offline"))
        assertEquals("""{"broadcaster_user_id":"10"}""", cond("channel.update"))
        assertEquals("""{"from_broadcaster_user_id":"10"}""", cond("channel.raid"))
        assertEquals("""{"broadcaster_user_id":"10","user_id":"20"}""", cond("channel.chat.clear"))
        // Never subscribe to what Twitch only gives the broadcaster (polls, predictions, hype train, ads).
        assertTrue(EventSubChatClient.OPTIONAL.none { (type, _) -> "poll" in type || "prediction" in type || "hype" in type || "ad_break" in type })
        // stream.offline + channel.update + channel.raid cost 1 each against the 10 of a connection.
        assertTrue(EventSubChatClient.OPTIONAL.count { (type, _) -> !type.startsWith("channel.chat") } <= 10)
    }
}
