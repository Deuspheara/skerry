package com.twitchplayer.tv.feature.player.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class HlsAdsTest {
    private val start = Instant.parse("2026-11-07T10:00:00Z").toEpochMilliseconds()
    private val ad = """#EXT-X-DATERANGE:ID="stitched-ad-1",CLASS="twitch-stitched-ad",START-DATE="2026-11-07T10:00:00.000Z",DURATION=30.000,X-TV-TWITCH-AD-ROLL-TYPE="PREROLL""""

    @Test fun insideTheBreakItCountsDown() {
        assertEquals(AdBreak(20_000), HlsAds.current(listOf("#EXTM3U", ad), start + 10_000))
        assertEquals(AdBreak(30_000), HlsAds.current(listOf(ad), start))
    }

    @Test fun beforeAndAfterThereIsNothing() {
        assertNull(HlsAds.current(listOf(ad), start - 1))
        assertNull(HlsAds.current(listOf(ad), start + 30_000))
        assertNull(HlsAds.current(emptyList(), start))
    }

    @Test fun otherDateRangesAndBrokenTagsAreIgnored() {
        val other = """#EXT-X-DATERANGE:ID="marker",CLASS="twitch-stream-source",START-DATE="2026-11-07T10:00:00Z",DURATION=60"""
        val broken = """#EXT-X-DATERANGE:ID="stitched-ad-2",START-DATE="not a date",DURATION=abc"""
        assertNull(HlsAds.current(listOf(other, broken, "#EXT-X-PROGRAM-DATE-TIME:2026-11-07T10:00:05Z"), start + 5_000))
    }

    @Test fun theIdAloneMarksAnAd() {
        val byId = """#EXT-X-DATERANGE:ID="stitched-ad-3",START-DATE="2026-11-07T10:00:00Z",PLANNED-DURATION=15"""
        assertEquals(AdBreak(10_000), HlsAds.current(listOf(byId), start + 5_000))
    }

    @Test fun startDatesAreParsedAsInstantsWhateverTheirShape() {
        // The old code used java.time.Instant, which needs API 26 (minSdk is 24): kotlin.time.Instant gives the same millis for
        // the shapes Twitch sends, with or without fractional seconds, and also reads an explicit UTC offset.
        fun adAt(date: String) = """#EXT-X-DATERANGE:ID="stitched-ad-9",START-DATE="$date",DURATION=10"""
        val playhead = start + 1_000
        assertEquals(AdBreak(9_000), HlsAds.current(listOf(adAt("2026-11-07T10:00:00Z")), playhead))
        assertEquals(AdBreak(9_000), HlsAds.current(listOf(adAt("2026-11-07T10:00:00.000Z")), playhead))
        assertEquals(AdBreak(9_500), HlsAds.current(listOf(adAt("2026-11-07T10:00:00.500Z")), playhead))
        assertEquals(AdBreak(9_000), HlsAds.current(listOf(adAt("2026-11-07T11:00:00+01:00")), playhead))
        assertNull(HlsAds.current(listOf(adAt("2026-11-07")), playhead))
        assertNull(HlsAds.current(listOf(adAt("")), playhead))
    }
}
