package com.twitchplayer.tv.feature.player.data.stream

import com.twitchplayer.tv.core.platform.Logger
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Answers recorded from gql.twitch.tv and usher.ttvnw.net on 2026-11-08 (src/jvmTest/resources/vod). JVM-only: it reads the fixtures as class-path resources. */
class GqlVodResolverTest {
    private fun fixture(name: String) = checkNotNull(javaClass.classLoader!!.getResource("vod/$name")) { name }.readText()

    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val noSleep = RetryPolicy(attempts = 3, sleep = {})

    private fun resolver(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) = GqlVodResolver(HttpClient(MockEngine { handler(it) }), Logger { _, _ -> }, retry = noSleep, deviceId = "dev", random = Random(1))

    private suspend fun failure(r: GqlVodResolver, id: String = "2922475029") = assertFailsWith<VodAccessException> { r.resolve(id) }

    @Test fun resolvesTokenThenMasterPlaylistWithTheDocumentedUsherParameters() = runTest {
        val seen = mutableListOf<HttpRequestData>()
        var gqlBody = ""
        val vod = resolver { request ->
            seen += request
            if (request.method == HttpMethod.Post) {
                gqlBody = request.body.toByteArray().decodeToString()
                respond(fixture("gql_access_public.json"), HttpStatusCode.OK, json)
            } else {
                respond(fixture("usher_master_public.m3u8"), HttpStatusCode.OK)
            }
        }.resolve("2922475029")

        assertEquals("2922475029", vod.vodId)
        assertEquals(15_217_000L, vod.durationMs)
        assertFalse(vod.recording)
        // The recorded playlist: 6 renditions, source first.
        assertEquals(listOf("chunked", "720p60", "720p30", "480p30", "360p30", "160p30"), vod.variants.map { it.id })
        assertTrue(vod.variants.first().isSource)
        assertTrue(vod.variants.first { it.id == "720p60" }.url.endsWith("/720p60/index-dvr.m3u8"))

        val post = seen[0]
        assertEquals("https://gql.twitch.tv/gql", post.url.toString())
        assertEquals("kimne78kx3ncx6brgo4mv6wki5h1ko", post.headers["Client-Id"])
        assertTrue(gqlBody.contains("videoPlaybackAccessToken(id: \$id"))
        assertTrue(gqlBody.contains("playerBackend: \\\"mediaplayer\\\""))
        assertFalse(gqlBody.contains("persistedQuery"), "raw query, not a persisted-query hash")
        assertTrue(gqlBody.contains("\"id\":\"2922475029\""))

        val usher = seen[1].url
        assertEquals("usher.ttvnw.net", usher.host)
        assertEquals("/vod/2922475029.m3u8", usher.encodedPath)
        val p = usher.parameters
        assertTrue(p["sig"]!!.isNotEmpty())
        assertTrue(p["token"]!!.startsWith("{\"authorization\""), "the token is JSON: it must survive URL encoding")
        assertEquals("true", p["allow_source"])
        assertEquals("mediaplayer", p["player_backend"])
        assertEquals("true", p["playlist_include_framerate"])
        assertEquals("avc1", p["supported_codecs"])
        assertNotNull(p["p"]?.toIntOrNull())
        assertEquals(vod.masterUrl, usher.toString())
    }

    @Test fun defaultQualityIs720p60() = runTest {
        val vod = resolver { request ->
            if (request.method == HttpMethod.Post) respond(fixture("gql_access_public.json"), HttpStatusCode.OK, json) else respond(fixture("usher_master_public.m3u8"), HttpStatusCode.OK)
        }.resolve("2922475029")
        assertEquals("720p60", QualityPolicy.default(vod.variants)?.id)
    }

    @Test fun aRecordingReplayIsFlagged() = runTest {
        val vod = resolver { request ->
            if (request.method == HttpMethod.Post) respond(fixture("gql_access_recording.json"), HttpStatusCode.OK, json) else respond(fixture("usher_master_public.m3u8"), HttpStatusCode.OK)
        }.resolve("2921302210")
        assertTrue(vod.recording)
    }

    @Test fun aDeletedOrUnknownReplayIsNotFound() = runTest {
        var usherCalls = 0
        val r = resolver { request ->
            if (request.method == HttpMethod.Post) {
                respond(fixture("gql_access_missing.json"), HttpStatusCode.OK, json)
            } else {
                usherCalls++
                respond("", HttpStatusCode.OK)
            }
        }
        assertEquals(VodFailure.NOT_FOUND, failure(r, "1").failure)
        assertEquals(0, usherCalls, "no playlist is asked for once the token is missing")
    }

    @Test fun aSubscriberOnlyReplayIsReportedFromUsher403() = runTest {
        var usherCalls = 0
        val r = resolver { request ->
            if (request.method == HttpMethod.Post) {
                respond(fixture("gql_access_subonly.json"), HttpStatusCode.OK, json)
            } else {
                usherCalls++
                respond(fixture("usher_restricted_403.json"), HttpStatusCode.Forbidden, json)
            }
        }
        val e = failure(r, "2922969951")
        assertEquals(VodFailure.SUBSCRIBER_ONLY, e.failure)
        assertEquals(403, e.status)
        assertEquals(1, usherCalls, "a restricted manifest is final: no v2 fallback, no retry")
    }

    @Test fun aTokenThatSaysForbiddenIsSubscriberOnly() = runTest {
        val forbidden = """{"data":{"videoPlaybackAccessToken":{"value":"{\"authorization\":{\"forbidden\":true,\"reason\":\"vod_manifest_restricted\"}}","signature":"abc"},"video":null}}"""
        val r = resolver { respond(forbidden, HttpStatusCode.OK, json) }
        assertEquals(VodFailure.SUBSCRIBER_ONLY, failure(r).failure)
    }

    @Test fun anIntegrityRefusalIsATokenFailureAndKeepsTheFlag() = runTest {
        val r = resolver { respond("""{"errors":[{"message":"failed integrity check"}]}""", HttpStatusCode.OK, json) }
        val e = failure(r)
        assertEquals(VodFailure.TOKEN, e.failure)
        assertTrue(e.integrity)
    }

    @Test fun anUnreadableTokenAnswerIsATokenFailure() = runTest {
        val r = resolver { respond("<html>oops</html>", HttpStatusCode.OK, json) }
        assertEquals(VodFailure.TOKEN, failure(r).failure)
        val r400 = resolver { respond("bad", HttpStatusCode.BadRequest, json) }
        assertEquals(VodFailure.TOKEN, failure(r400).failure)
    }

    @Test fun whenUsherRefusesTheLegacyPathTheV2PathIsTriedOnce() = runTest {
        val paths = mutableListOf<String>()
        val vod = resolver { request ->
            if (request.method == HttpMethod.Post) {
                respond(fixture("gql_access_public.json"), HttpStatusCode.OK, json)
            } else {
                paths += request.url.encodedPath
                if (request.url.encodedPath.startsWith("/vod/v2/")) {
                    assertNotNull(request.url.parameters["nauthsig"])
                    assertNotNull(request.url.parameters["nauth"])
                    respond(fixture("usher_master_public.m3u8"), HttpStatusCode.OK)
                } else {
                    respond("gone", HttpStatusCode.NotFound)
                }
            }
        }.resolve("2922475029")
        assertEquals(listOf("/vod/2922475029.m3u8", "/vod/v2/2922475029.m3u8"), paths)
        assertTrue(vod.masterUrl.contains("/vod/v2/"))
    }

    @Test fun bothPathsRefusingWith404IsNotFound() = runTest {
        val r = resolver { request -> if (request.method == HttpMethod.Post) respond(fixture("gql_access_public.json"), HttpStatusCode.OK, json) else respond("nope", HttpStatusCode.NotFound) }
        assertEquals(VodFailure.NOT_FOUND, failure(r).failure)
    }

    @Test fun serverErrorsRetryThenSurfaceAsUnavailable() = runTest {
        var calls = 0
        val r = resolver {
            calls++
            respond("busy", HttpStatusCode.ServiceUnavailable)
        }
        assertEquals(VodFailure.UNAVAILABLE, failure(r).failure)
        assertEquals(3, calls)
    }

    @Test fun networkErrorsRetryAndFinallyThrowAsIoException() = runTest {
        var calls = 0
        val r = resolver {
            calls++
            throw IOException("offline")
        }
        assertFailsWith<IOException> { r.resolve("2922475029") }
        assertEquals(3, calls)
    }

    @Test fun aPlaylistWithoutRenditionsIsUnavailable() = runTest {
        val r = resolver { request -> if (request.method == HttpMethod.Post) respond(fixture("gql_access_public.json"), HttpStatusCode.OK, json) else respond("#EXTM3U\n", HttpStatusCode.OK) }
        assertEquals(VodFailure.UNAVAILABLE, failure(r).failure)
    }

    @Test fun anInvalidIdNeverReachesTheNetwork() = runTest {
        val r = resolver { error("no request expected") }
        assertFailsWith<IllegalArgumentException> { r.resolve("../x") }
    }
}
