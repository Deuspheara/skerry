package com.twitchplayer.tv.feature.player.data.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RecentMessagesTest {
    private val line = "@badge-info=subscriber/5;badges=subscriber/3,premium/1;color=#FF0000;display-name=Jo\\sSmith;emotes=25:3-7;id=abc;room-id=42;user-id=7 " +
        ":jo!jo@jo.tmi.twitch.tv PRIVMSG #chan :go Kappa now"

    @Test fun `parses tags badges and the emote range`() {
        val message = assertNotNull(RecentMessages.parsePrivmsg(line))
        assertEquals("abc", message.id)
        assertEquals("Jo Smith", message.username)
        assertEquals("jo", message.login)
        assertEquals("42", message.broadcasterId)
        assertEquals(listOf("subscriber", "premium"), message.badges.map { it.setId })
        assertEquals(listOf("text", "emote", "text"), message.content.fragments.map { it.type })
        assertEquals("Kappa", message.content.fragments[1].text)
        assertEquals("25", message.content.fragments[1].emote?.id)
    }

    @Test fun `ignores lines that are not chat messages`() {
        assertNull(RecentMessages.parsePrivmsg("@id=x :tmi.twitch.tv CLEARCHAT #chan :bad"))
        assertNull(RecentMessages.parsePrivmsg(":no tags PRIVMSG #chan :hi"))
    }

    @Test fun `emote ranges count code points`() {
        val fragments = RecentMessages.fragments("😀 Kappa", "25:2-6")
        assertEquals("Kappa", fragments.first { it.type == "emote" }.text)
    }
}
