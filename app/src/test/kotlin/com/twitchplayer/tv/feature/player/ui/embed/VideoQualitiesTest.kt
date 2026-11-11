package com.twitchplayer.tv.feature.player.ui.embed

import com.twitchplayer.tv.feature.player.playback.VideoQualities
import com.twitchplayer.tv.feature.player.playback.VideoQuality
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoQualitiesTest {
    @Test fun parsesTheJsonStringThatEvaluateJavascriptReturns() {
        val page = """{"qualities":[{"id":"chunked","name":"1080p60 (Source)"},{"id":"720p60","name":"720p60"},{"id":"auto","name":"Auto"}],"selected":"720p60"}"""
        val raw = JsonPrimitive(page).toString() // evaluateJavascript hands a string result back JSON-encoded
        val parsed = VideoQualities.parse(raw)
        assertEquals(listOf("chunked", "720p60", "auto"), parsed.options.map { it.id })
        assertEquals("1080p60 (Source)", parsed.options.first().label)
        assertEquals("720p60", parsed.selected)
    }

    @Test fun missingOrBrokenAnswersMeanNoQualities() {
        assertTrue(VideoQualities.parse(null).options.isEmpty())
        assertTrue(VideoQualities.parse("null").options.isEmpty())
        assertTrue(VideoQualities.parse("\"not json\"").options.isEmpty())
        assertTrue(VideoQualities.parse("").options.isEmpty())
    }
}
