package com.twitchplayer.phone.feature.player

import com.twitchplayer.tv.feature.player.data.stream.VodAccessException
import com.twitchplayer.tv.feature.player.data.stream.VodFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ReplayErrorTest {
    @Test fun errorsMapToTheSameMessagesAsTheTvScreen() {
        assertEquals(ReplayError.Gone, VodAccessException(VodFailure.NOT_FOUND, "gql").toReplayError())
        assertEquals(ReplayError.SubscribersOnly, VodAccessException(VodFailure.SUBSCRIBER_ONLY, "usher", 403).toReplayError())
        assertEquals(ReplayError.Refused, VodAccessException(VodFailure.TOKEN, "gql", integrity = true).toReplayError())
        assertEquals(ReplayError.Refused, VodAccessException(VodFailure.UNAVAILABLE, "usher", 503).toReplayError())
        assertEquals(ReplayError.Network, IOException("x").toReplayError())
        assertEquals(ReplayError.Refused, IllegalStateException("x").toReplayError())
    }

    @Test fun onlyTransientErrorsOfferRetry() {
        assertFalse(ReplayError.Gone.retryable)
        assertFalse(ReplayError.SubscribersOnly.retryable)
        assertTrue(ReplayError.Network.retryable)
        assertTrue(ReplayError.Refused.retryable)
        assertTrue(ReplayError.Playback.retryable)
    }

    @Test fun shareTextNamesTheClipAndLinksIt() {
        assertEquals("Great play\nhttps://clips.twitch.tv/AbCd-1_x", clipShareText(" Great play ", "AbCd-1_x"))
        assertEquals("https://clips.twitch.tv/slug", clipShareText("", "slug"))
    }

    @Test fun neverBuildsALinkFromAnOddSlug() {
        assertNull(clipShareText("t", "../etc"))
        assertNull(clipShareText("t", "a b"))
        assertNull(clipShareText("t", "x?y=1"))
        assertNull(clipShareText("t", ""))
        assertNull(clipShareText("t", "a".repeat(101)))
    }
}
