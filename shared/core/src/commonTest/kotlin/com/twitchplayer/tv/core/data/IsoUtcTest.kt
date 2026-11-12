package com.twitchplayer.tv.core.data

import com.twitchplayer.tv.core.data.repository.isoUtc
import kotlin.test.Test
import kotlin.test.assertEquals

class IsoUtcTest {
    @Test fun theEpochIsMidnightOnNewYear1970() {
        assertEquals("1970-01-01T00:00:00Z", isoUtc(0L))
    }

    @Test fun aKnownInstant() {
        assertEquals("2023-11-14T22:13:20Z", isoUtc(1_700_000_000_000L))
    }

    @Test fun aLeapDay() {
        assertEquals("2000-02-29T00:00:00Z", isoUtc(951_782_400_000L))
        assertEquals("2024-02-29T12:34:56Z", isoUtc(1_709_210_096_000L))
    }

    @Test fun beforeTheEpoch() {
        assertEquals("1969-12-31T23:59:59Z", isoUtc(-1_000L))
    }

    @Test fun theEndOfAYearAndTheStartOfTheNext() {
        assertEquals("2025-12-31T23:59:59Z", isoUtc(1_767_225_599_000L))
        assertEquals("2026-01-01T00:00:00Z", isoUtc(1_767_225_600_000L))
    }

    @Test fun subSecondsAreDropped() {
        assertEquals("1970-01-01T00:00:01Z", isoUtc(1_999L))
    }
}
