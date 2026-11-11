package com.twitchplayer.tv.feature.player

import com.twitchplayer.tv.feature.player.playback.PlaybackStats
import com.twitchplayer.tv.feature.player.ui.formatBitrate
import com.twitchplayer.tv.feature.player.ui.formatLatency
import com.twitchplayer.tv.feature.player.ui.formatVideo
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackFormatTest {
    @Test fun numbers() {
        assertEquals("3.9 s", formatLatency(3_940))
        assertEquals("-", formatBitrate(0))
        assertEquals("850 kb/s", formatBitrate(850_000))
        assertEquals("6.0 Mb/s", formatBitrate(6_000_000))
        assertEquals("720p@60", formatVideo(PlaybackStats(width = 1280, height = 720, frameRate = 59.94f)))
        assertEquals("-", formatVideo(PlaybackStats()))
    }
}
