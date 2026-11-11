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
    }
}
