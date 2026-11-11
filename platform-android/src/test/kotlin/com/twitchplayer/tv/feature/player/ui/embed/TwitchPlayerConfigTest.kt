package com.twitchplayer.tv.feature.player.ui.embed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TwitchPlayerConfigTest {
    @Test
    fun buildsPlayerPageWithExplicitParentDomain() {
        val config = TwitchPlayerConfig("https://player.example.org", "player.example.org")
        assertEquals(
            "https://player.example.org/?channel=some_channel&parent=player.example.org",
            config.pageUrl("some_channel"),
        )
    }

    @Test
    fun rejectsUnencryptedOrMismatchedHost() {
        assertThrows(IllegalArgumentException::class.java) {
            TwitchPlayerConfig("http://player.example.org", "player.example.org")
        }
        assertThrows(IllegalArgumentException::class.java) {
            TwitchPlayerConfig("https://player.example.org", "other.example.org")
        }
    }

    @Test
    fun rejectsUnsafeChannelInput() {
        val config = TwitchPlayerConfig("https://player.example.org", "player.example.org")
        assertThrows(IllegalArgumentException::class.java) { config.pageUrl("bad/path") }
    }

    @Test
    fun autoplayFlagIsOptIn() {
        val config = TwitchPlayerConfig("https://zavori.app", "zavori.app")
        org.junit.Assert.assertFalse("autoplay" in config.pageUrl("abc"))
        org.junit.Assert.assertTrue(config.pageUrl("abc", autoplay = true).endsWith("&autoplay=1"))
    }
}
