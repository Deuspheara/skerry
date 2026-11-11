package com.twitchplayer.tv.core

import com.twitchplayer.tv.core.data.repository.parseTwitchTime
import com.twitchplayer.tv.core.ui.formatUptime
import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {
    @Test fun uptime() {
        assertEquals("0:00", formatUptime(0))
        assertEquals("12:03", formatUptime(723_000))
        assertEquals("1:02:03", formatUptime(3_723_999))
        assertEquals("0:00", formatUptime(-5_000))
    }

    @Test fun vodDurations() {
        assertEquals("3:12:54", com.twitchplayer.tv.core.ui.formatTwitchDuration("3h12m54s"))
        assertEquals("12:05", com.twitchplayer.tv.core.ui.formatTwitchDuration("12m5s"))
        assertEquals("0:42", com.twitchplayer.tv.core.ui.formatTwitchDuration("42s"))
        assertEquals("", com.twitchplayer.tv.core.ui.formatTwitchDuration(""))
    }

    @Test fun helixTimestamps() {
        assertEquals(1_700_000_000_000L, parseTwitchTime("2023-11-14T22:13:20Z"))
        assertEquals(0L, parseTwitchTime(""))
        assertEquals(0L, parseTwitchTime("not a date"))
    }
}
