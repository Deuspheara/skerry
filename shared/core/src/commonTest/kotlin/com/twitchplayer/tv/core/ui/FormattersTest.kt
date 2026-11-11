package com.twitchplayer.tv.core.ui

import com.twitchplayer.tv.core.data.repository.parseTwitchTime
import kotlin.test.Test
import kotlin.test.assertEquals

class FormattersTest {
    @Test fun uptime() {
        assertEquals("0:00", formatUptime(0))
        assertEquals("12:03", formatUptime(723_000))
        assertEquals("1:02:03", formatUptime(3_723_999))
        assertEquals("0:00", formatUptime(-5_000))
        assertEquals("10:00:00", formatUptime(36_000_000))
    }

    @Test fun vodDurations() {
        assertEquals("3:12:54", formatTwitchDuration("3h12m54s"))
        assertEquals("12:05", formatTwitchDuration("12m5s"))
        assertEquals("0:42", formatTwitchDuration("42s"))
        assertEquals("", formatTwitchDuration(""))
        assertEquals("soon", formatTwitchDuration("soon"))
    }

    @Test fun vodDurationSeconds() {
        assertEquals(11_574, parseTwitchDurationSeconds("3h12m54s"))
        assertEquals(7_200, parseTwitchDurationSeconds("2h"))
        assertEquals(0, parseTwitchDurationSeconds("soon"))
    }

    @Test fun helixTimestamps() {
        assertEquals(1_700_000_000_000L, parseTwitchTime("2023-11-14T22:13:20Z"))
        assertEquals(0L, parseTwitchTime(""))
        assertEquals(0L, parseTwitchTime("not a date"))
        assertEquals(1_798_761_599_000L, parseTwitchTime("2026-12-31T23:59:59Z"))
        assertEquals(0L, parseTwitchTime("1970-01-01T00:00:00Z"))
    }

    @Test fun onlyTheExactHelixShapeIsAccepted() {
        // Same strictness as the former SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'"): anything else is 0.
        assertEquals(0L, parseTwitchTime("2023-11-14T22:13:20+01:00"), "offset")
        assertEquals(0L, parseTwitchTime("2023-11-14T22:13:20.123Z"), "fraction")
        assertEquals(0L, parseTwitchTime("2023-11-14"), "date only")
        assertEquals(0L, parseTwitchTime(" 2023-11-14T22:13:20Z"), "leading space")
        assertEquals(0L, parseTwitchTime("2023-11-14T22:13:20Z "), "trailing space")
        assertEquals(0L, parseTwitchTime("2023-11-14t22:13:20z"), "lower case")
        assertEquals(0L, parseTwitchTime("2023-11-14 22:13:20Z"), "space instead of T")
        assertEquals(0L, parseTwitchTime("2023-13-14T22:13:20Z"), "month 13 has the right shape but is no date")
    }
}
