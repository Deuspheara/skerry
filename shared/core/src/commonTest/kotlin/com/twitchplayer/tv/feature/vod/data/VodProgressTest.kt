package com.twitchplayer.tv.feature.vod.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VodProgressTest {
    @Test fun progressIsOfferedOnlyAwayFromTheEnds() {
        assertFalse(VodProgress(5_000, 600_000).resumable)
        assertTrue(VodProgress(15_000, 600_000).resumable)
        assertTrue(VodProgress(300_000, 600_000).resumable)
        assertFalse(VodProgress(590_000, 600_000).resumable)
        // Length unknown: any position past the first seconds counts.
        assertTrue(VodProgress(120_000, 0).resumable)
        assertEquals(0.5f, VodProgress(300_000, 600_000).fraction, 0f)
        assertEquals(0f, VodProgress(300_000, 0).fraction, 0f)
        assertEquals(1f, VodProgress(900_000, 600_000).fraction, 0f)
    }
}
