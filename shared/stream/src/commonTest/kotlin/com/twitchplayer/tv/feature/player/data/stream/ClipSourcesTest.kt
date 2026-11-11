package com.twitchplayer.tv.feature.player.data.stream

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClipSourcesTest {
    // Shape of a real answer from gql.twitch.tv (checked 2026-11-07), shortened.
    private val answer = """{"data":{"clip":{"playbackAccessToken":{"value":"{\"clip_uri\":\"x\"}","signature":"abc123"},
        "videoQualities":[{"quality":"480","sourceURL":"https://cdn.example/480/index.mp4","frameRate":30.0},
        {"quality":"1080","sourceURL":"https://cdn.example/1080/index.mp4","frameRate":30}]}},"extensions":{"requestID":"r"}}"""

    @Test fun filesAreSignedAndBestFirst() {
        val sources = parseClipSources(answer)
        assertEquals(listOf(1080, 480), sources.map { it.height })
        val url = sources.first().url
        assertTrue(url.startsWith("https://cdn.example/1080/index.mp4?sig=abc123&token="), url)
        // The token is JSON: it must be URL-encoded in the query.
        assertTrue(url.endsWith("%7B%22clip_uri%22%3A%22x%22%7D"), url)
    }

    @Test fun missingClipOrTokenGivesNothing() {
        assertTrue(parseClipSources("""{"data":{"clip":null}}""").isEmpty())
        assertTrue(parseClipSources("""{"data":{"clip":{"playbackAccessToken":null,"videoQualities":[{"quality":"720","sourceURL":"u"}]}}}""").isEmpty())
        assertTrue(parseClipSources("not json").isEmpty())
    }

    @Test fun anExistingQueryStringGetsAnAmpersand() {
        assertEquals("u?a=1&sig=s&token=t", signedClipUrl("u?a=1", "t", "s"))
    }
}
