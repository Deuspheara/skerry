package com.twitchplayer.phone.feature.discover

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedPolicyTest {
    @Test fun aMutedPreviewNeverTakesAudioFocus() {
        assertFalse("opening Discover must not pause the viewer's music", FeedAudio.takesFocus(muted = true, playerOpen = false))
        assertFalse(FeedAudio.takesFocus(muted = true, playerOpen = true))
    }

    @Test fun focusIsTakenOnlyWhenTheSoundIsOnAndNoFullPlayerPlays() {
        assertTrue(FeedAudio.takesFocus(muted = false, playerOpen = false))
        assertFalse("the full player has its own focus", FeedAudio.takesFocus(muted = false, playerOpen = true))
    }

    @Test fun aResolvedAddressIsKeptForTenMinutesThenForgotten() {
        var now = 1_000L
        val cache = ClipUrlCache(now = { now })
        cache.put("a", "https://clip/a.mp4")
        now += ClipUrlCache.TTL_MS - 1
        assertEquals("https://clip/a.mp4", cache.get("a"))
        now += 1
        assertNull(cache.get("a"))
        assertNull("an expired entry stays gone", cache.get("a"))
    }

    @Test fun anAddressStoredAgainRestartsItsClock() {
        var now = 0L
        val cache = ClipUrlCache(now = { now })
        cache.put("a", "old")
        now += ClipUrlCache.TTL_MS - 10
        cache.put("a", "new")
        now += ClipUrlCache.TTL_MS - 10
        assertEquals("new", cache.get("a"))
    }

    @Test fun aClockSetBackwardsMakesTheAddressExpired() {
        var now = 10_000L
        val cache = ClipUrlCache(now = { now })
        cache.put("a", "u")
        now = 5_000L
        assertNull(cache.get("a"))
    }

    @Test fun aFailedResolveIsRetriedWithAGrowingWait() {
        assertEquals(3, ClipRetry.ATTEMPTS)
        assertEquals(0L, ClipRetry.delayBefore(1))
        assertEquals(ClipRetry.DELAY_MS, ClipRetry.delayBefore(2))
        assertEquals(2 * ClipRetry.DELAY_MS, ClipRetry.delayBefore(3))
    }
}
