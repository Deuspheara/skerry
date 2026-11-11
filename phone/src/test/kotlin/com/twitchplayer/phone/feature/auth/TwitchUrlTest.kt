package com.twitchplayer.phone.feature.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TwitchUrlTest {
    @Test fun acceptsHttpsOnTwitchAndItsSubdomains() {
        assertTrue(isTwitchUrl("https://www.twitch.tv/activate"))
        assertTrue(isTwitchUrl("https://twitch.tv/activate?public=true&device-code=ABCDEFGH"))
        assertTrue(isTwitchUrl("https://id.twitch.tv/oauth2/device"))
        assertTrue(isTwitchUrl("HTTPS://WWW.TWITCH.TV/activate"))
        assertTrue(isTwitchUrl("https://www.twitch.tv:443/activate"))
        assertTrue(isTwitchUrl("  https://www.twitch.tv/activate  "))
    }

    @Test fun refusesAnythingButHttps() {
        assertFalse(isTwitchUrl("http://www.twitch.tv/activate"))
        assertFalse(isTwitchUrl("javascript:alert(1)"))
        assertFalse(isTwitchUrl("intent://www.twitch.tv/#Intent;scheme=https;end"))
        assertFalse(isTwitchUrl("file:///sdcard/twitch.tv"))
        assertFalse(isTwitchUrl("//www.twitch.tv/activate"))
    }

    @Test fun refusesLookAlikeHosts() {
        assertFalse(isTwitchUrl("https://twitch.tv.example.com/activate"))
        assertFalse(isTwitchUrl("https://evil-twitch.tv/activate"))
        assertFalse(isTwitchUrl("https://nottwitch.tv/activate"))
        assertFalse(isTwitchUrl("https://example.com/twitch.tv"))
        assertFalse(isTwitchUrl("https://twitch.tv@example.com/activate"))
        assertFalse(isTwitchUrl("https://user:pass@www.twitch.tv/activate"))
    }

    @Test fun refusesOtherPortsAndGarbage() {
        assertFalse(isTwitchUrl("https://www.twitch.tv:8443/activate"))
        assertFalse(isTwitchUrl(""))
        assertFalse(isTwitchUrl("not a url"))
        assertFalse(isTwitchUrl("https://"))
    }

    @Test fun aChannelUrlIsTheLowerCasedLoginOnWwwTwitch() {
        assertEquals("https://www.twitch.tv/camille", channelUrl("Camille"))
        assertTrue(isTwitchUrl(channelUrl("camille_99")))
    }
}
