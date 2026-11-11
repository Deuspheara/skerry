package com.twitchplayer.tv.feature.player.data.stream

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import kotlin.random.Random

class GqlPlaybackResolverTest {
    private val token = """{"data":{"streamPlaybackAccessToken":{"value":"{\"channel\":\"zerator\"}","signature":"deadbeef"}}}"""
    private val playlist = """
        #EXTM3U
        #EXT-X-MEDIA:TYPE=VIDEO,GROUP-ID="720p60",NAME="720p60",AUTOSELECT=YES,DEFAULT=YES
        #EXT-X-STREAM-INF:BANDWIDTH=3400000,RESOLUTION=1280x720,CODECS="avc1.4D401F,mp4a.40.2",VIDEO="720p60",FRAME-RATE=60.000
        https://weaver.example/720p60.m3u8
    """.trimIndent()
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val noSleep = RetryPolicy(attempts = 3, sleep = {})

    private fun resolver(handler: suspend MockRequestHandleScope.(io.ktor.client.request.HttpRequestData) -> io.ktor.client.request.HttpResponseData) = GqlPlaybackResolver(HttpClient(MockEngine { handler(it) }), retry = noSleep, deviceId = "dev", random = Random(1), log = {})

    @Test fun resolvesTokenThenMasterPlaylistWithTheDocumentedUsherParameters() = runTest {
        val seen = mutableListOf<io.ktor.client.request.HttpRequestData>()
        var gqlBody = ""
        val r = resolver { request ->
            seen += request
            if (request.method == HttpMethod.Post) {
                gqlBody = String(request.body.toByteArray())
                respond(token, HttpStatusCode.OK, json)
            } else {
                respond(playlist, HttpStatusCode.OK)
            }
        }
        val stream = r.resolve("Zerator")
        assertEquals("zerator", stream.channel)
        assertEquals(listOf("720p60"), stream.variants.map { it.id })

        val post = seen[0]
        assertEquals("https://gql.twitch.tv/gql", post.url.toString())
        assertEquals("kimne78kx3ncx6brgo4mv6wki5h1ko", post.headers["Client-Id"])
        assertTrue(gqlBody.contains("streamPlaybackAccessToken(channelName: \$login"))
        assertFalse("raw query, not a persisted-query hash", gqlBody.contains("persistedQuery"))
        assertTrue(gqlBody.contains("\"login\":\"zerator\""))

        val usher = seen[1].url
        assertEquals("usher.ttvnw.net", usher.host)
        assertEquals("/api/channel/hls/zerator.m3u8", usher.encodedPath)
        val p = usher.parameters
        assertEquals("deadbeef", p["sig"])
        assertEquals("{\"channel\":\"zerator\"}", p["token"])
        assertEquals("true", p["allow_source"])
        assertEquals("mediaplayer", p["player_backend"])
        assertEquals("true", p["playlist_include_framerate"])
        assertEquals("avc1", p["supported_codecs"])
        assertNotNull(p["p"]?.toIntOrNull())
        assertEquals(stream.masterUrl, usher.toString())
    }

    @Test fun anOfflineChannelIsReportedAsOfflineAndNotRetried() = runTest {
        var usherCalls = 0
        val r = resolver { request ->
            if (request.method == HttpMethod.Post) {
                respond(token, HttpStatusCode.OK, json)
            } else {
                usherCalls++
                respond("""[{"error":"Not Found","status":404,"message":"The requested channel is offline"}]""", HttpStatusCode.NotFound)
            }
        }
        assertThrows(ChannelOfflineException::class.java) { kotlinx.coroutines.runBlocking { r.resolve("quiet") } }
        assertEquals(1, usherCalls)
    }

    @Test fun anIntegrityCheckRefusalKeepsTheExactStatusAndBody() = runTest {
        val body = """{"errors":[{"message":"failed integrity check"}],"data":null}"""
        var calls = 0
        val r = resolver {
            calls++
            respond(body, HttpStatusCode.OK, json)
        }
        val e = assertThrows(StreamAccessException::class.java) { kotlinx.coroutines.runBlocking { r.resolve("zerator") } }
        assertTrue(e.integrity)
        assertEquals(200, e.status)
        assertEquals(body, e.body)
        assertEquals("gql", e.stage)
        assertEquals("a refusal is not retried", 1, calls)
    }

    @Test fun serverErrorsAreRetriedThenSucceed() = runTest {
        var tokenCalls = 0
        val r = resolver { request ->
            if (request.method == HttpMethod.Post) {
                tokenCalls++
                if (tokenCalls < 3) respond("busy", HttpStatusCode.ServiceUnavailable) else respond(token, HttpStatusCode.OK, json)
            } else {
                respond(playlist, HttpStatusCode.OK)
            }
        }
        assertEquals("720p60", r.resolve("zerator").variants.single().id)
        assertEquals(3, tokenCalls)
    }

    @Test fun aNetworkThatStaysDownFailsWithTheIoErrorAfterAllAttempts() = runTest {
        var calls = 0
        val r = resolver {
            calls++
            throw IOException("no route")
        }
        assertThrows(IOException::class.java) { kotlinx.coroutines.runBlocking { r.resolve("zerator") } }
        assertEquals(3, calls)
    }

    @Test fun clientErrorsFromTokenAreNotRetried() = runTest {
        var calls = 0
        val r = resolver {
            calls++
            respond("""{"error":"Unauthorized"}""", HttpStatusCode.Unauthorized)
        }
        val e = assertThrows(StreamAccessException::class.java) { kotlinx.coroutines.runBlocking { r.resolve("zerator") } }
        assertEquals(401, e.status)
        assertFalse(e.integrity)
        assertEquals(1, calls)
    }

    @Test fun rejectsAnInvalidLogin() = runTest {
        val r = resolver { respond("", HttpStatusCode.OK) }
        assertThrows(IllegalArgumentException::class.java) { kotlinx.coroutines.runBlocking { r.resolve("a/b?x") } }
    }
}
