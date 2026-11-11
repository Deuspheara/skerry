package com.twitchplayer.tv.core.data

import com.twitchplayer.tv.core.data.repository.freshThumbnail
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class FreshThumbnailTest {
    private val url = "https://static-cdn.jtvnw.net/previews-ttv/live_user_x-400x225.jpg"

    @Test fun sameUrlWithinFiveMinutesNewOneAfter() {
        val t0 = 1_000_000L * 300_000L
        assertEquals(freshThumbnail(url, t0), freshThumbnail(url, t0 + 299_999))
        assertNotEquals(freshThumbnail(url, t0), freshThumbnail(url, t0 + 300_000))
        assertEquals("$url?t=1000000", freshThumbnail(url, t0))
    }

    @Test fun keepsAnExistingQueryAndEmptyUrls() {
        assertEquals("$url?a=1&t=0", freshThumbnail("$url?a=1", 0))
        assertEquals("", freshThumbnail("", 0))
    }
}
