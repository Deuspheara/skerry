package com.twitchplayer.tv.feature.player.emotes

import com.twitchplayer.tv.core.network.api.twitchHttpClient
import com.twitchplayer.tv.feature.player.data.emotes.BttvProvider
import com.twitchplayer.tv.feature.player.data.emotes.Emote
import com.twitchplayer.tv.feature.player.data.emotes.EmoteChange
import com.twitchplayer.tv.feature.player.data.emotes.FfzProvider
import com.twitchplayer.tv.feature.player.data.emotes.SevenTvProvider
import com.twitchplayer.tv.feature.player.data.emotes.parseSevenTvChange
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Parsing against payloads shaped like the live APIs (trimmed from real responses of 7tv.io, api.betterttv.net, api.frankerfacez.com). */
class EmoteProvidersTest {
    private fun client(vararg routes: Pair<String, Pair<HttpStatusCode, String>>): HttpClient {
        val table = routes.toMap()
        return HttpClient(MockEngine { request ->
            val (status, body) = table.entries.firstOrNull { request.url.toString().endsWith(it.key) }?.value ?: (HttpStatusCode.NotFound to "{}")
            respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
    }

    // ---- 7TV ------------------------------------------------------------------------------------------
    private val sevenTvUser = """
        {"id":"01FE","platform":"TWITCH","emote_set":{"id":"SET1","name":"Channel","emotes":[
          {"id":"E1","name":"HeartPop","flags":0,"timestamp":1,"data":{"id":"E1","name":"HeartPop","flags":0,"animated":false,
            "host":{"url":"//cdn.7tv.app/emote/E1","files":[{"name":"1x.webp","width":32,"height":32,"format":"WEBP"},{"name":"2x.webp","width":64,"height":64,"format":"WEBP"}]}}},
          {"id":"E2","name":"RainTime","flags":1,"data":{"id":"E2","name":"rain","flags":256,"animated":true,
            "host":{"url":"//cdn.7tv.app/emote/E2","files":[{"name":"2x.webp","static_name":"2x_static.webp","width":128,"height":64,"format":"WEBP"}]}}}]}}
    """.trimIndent()

    @Test fun sevenTvChannelEmotesCarryStillAnimatedZeroWidthAndAspect() = runTest {
        val emotes = SevenTvProvider(client("/v3/users/twitch/42" to (HttpStatusCode.OK to sevenTvUser))).channel("42").associateBy { it.name }
        assertEquals(Emote("HeartPop", "https://cdn.7tv.app/emote/E1/2x.webp", null, false, 1f), emotes["HeartPop"])
        val rain = emotes.getValue("RainTime")
        assertEquals("https://cdn.7tv.app/emote/E2/2x_static.webp", rain.url)
        assertEquals("https://cdn.7tv.app/emote/E2/2x.webp", rain.animatedUrl)
        assertTrue("zero width from the set flag and the emote flag", rain.zeroWidth)
        assertEquals(2f, rain.aspect, 0f)
    }

    @Test fun sevenTvZeroWidthIsReadFromEitherFlag() = runTest {
        val onlyEmoteFlag = """{"emote_set":{"id":"S","emotes":[{"id":"Z","name":"Zed","flags":0,"data":{"flags":256,"animated":false}}]}}"""
        val zed = SevenTvProvider(client("/v3/users/twitch/1" to (HttpStatusCode.OK to onlyEmoteFlag))).channel("1").single()
        assertTrue(zed.zeroWidth)
        assertEquals("a push that omits the host falls back to the canonical CDN layout", "https://cdn.7tv.app/emote/Z/2x.webp", zed.url)
    }

    @Test fun sevenTvChannelWithoutAnAccountIsEmptyAndServerErrorsThrow() = runTest {
        assertEquals(emptyList<Emote>(), SevenTvProvider(client()).channel("404"))
        try {
            SevenTvProvider(client("/v3/users/twitch/500" to (HttpStatusCode.InternalServerError to "{}"))).channel("500")
            fail("a 5xx must surface so the repository keeps the previous layer")
        } catch (_: Exception) { /* expected */ }
    }

    @Test fun sevenTvDispatchBecomesAddRemoveAndRename() {
        val change = parseSevenTvChange(
            """{"type":"emote_set.update","body":{"id":"SET1","pushed":[{"key":"emotes","index":3,"value":{"id":"N","name":"NewOne","flags":0,"data":{"animated":true,"host":{"url":"//cdn.7tv.app/emote/N"}}}}],
               "pulled":[{"key":"emotes","index":1,"old_value":{"id":"O","name":"OldOne"}}],
               "updated":[{"key":"emotes","index":2,"old_value":{"id":"R","name":"Before"},"value":{"id":"R","name":"After","flags":0}}]}}""",
        )!!
        assertEquals(setOf("OldOne", "Before"), change.removed)
        assertEquals(setOf("NewOne", "After"), change.added.map { it.name }.toSet())
        assertTrue(change.added.first { it.name == "NewOne" }.animated)
    }

    @Test fun sevenTvDispatchIgnoresOtherTypesAndFields() {
        assertNull(parseSevenTvChange("""{"type":"emote_set.update","body":{"id":"S","updated":[{"key":"name","value":"Renamed set"}]}}"""))
        assertNull(parseSevenTvChange("""{"type":"user.update","body":{}}"""))
        assertNull(parseSevenTvChange("not json"))
        assertEquals(EmoteChange(), EmoteChange())
    }

    // ---- BTTV -----------------------------------------------------------------------------------------
    @Test fun bttvMergesChannelAndSharedEmotesWithStaticAndAnimatedVariants() = runTest {
        val body = """{"id":"x","bots":[],"channelEmotes":[{"id":"A1","code":"Plain","imageType":"png","animated":false,"userId":"u"}],
            "sharedEmotes":[{"id":"A2","code":"Jam","imageType":"gif","animated":true,"width":40,"height":20,"user":{"id":"u","name":"n"}}]}"""
        val emotes = BttvProvider(client("/3/cached/users/twitch/7" to (HttpStatusCode.OK to body))).channel("7").associateBy { it.name }
        assertEquals("https://cdn.betterttv.net/emote/A1/2x.webp", emotes.getValue("Plain").url)
        assertFalse(emotes.getValue("Plain").animated)
        assertEquals("https://cdn.betterttv.net/emote/A2/static/2x.webp", emotes.getValue("Jam").url)
        assertEquals("https://cdn.betterttv.net/emote/A2/2x.webp", emotes.getValue("Jam").animatedUrl)
        assertEquals(2f, emotes.getValue("Jam").aspect, 0f)
    }

    @Test fun bttvGlobalListAndModifierFlag() = runTest {
        val body = """[{"id":"G1","code":"Cold","imageType":"png","animated":false,"modifier":true},{"id":"G2","code":"Plain","imageType":"png","animated":false,"modifier":false}]"""
        val emotes = BttvProvider(client("/3/cached/emotes/global" to (HttpStatusCode.OK to body))).global().associateBy { it.name }
        assertTrue(emotes.getValue("Cold").zeroWidth)
        assertFalse(emotes.getValue("Plain").zeroWidth)
    }

    // ---- FFZ ------------------------------------------------------------------------------------------
    @Test fun ffzRoomUsesTheRoomSetStillsAnimatedVariantAndSkipsModifiers() = runTest {
        val room = """{"room":{"set":85287},"sets":{"85287":{"emoticons":[
            {"id":1,"name":"WallE","width":25,"height":32,"modifier":false,"urls":{"1":"https://cdn.frankerfacez.com/emote/1/1","2":"https://cdn.frankerfacez.com/emote/1/2","4":"https://cdn.frankerfacez.com/emote/1/4"}},
            {"id":2,"name":"Moving","width":32,"height":32,"modifier":false,"urls":{"2":"https://cdn.frankerfacez.com/emote/2/2"},"animated":{"2":"https://cdn.frankerfacez.com/emote/2/animated/2"}},
            {"id":3,"name":"ffzW","width":28,"height":28,"modifier":true,"modifier_flags":9,"urls":{"2":"https://cdn.frankerfacez.com/emote/3/2"}}]}}}"""
        val emotes = FfzProvider(client("/v1/room/id/9" to (HttpStatusCode.OK to room))).channel("9").associateBy { it.name }
        assertEquals(setOf("WallE", "Moving"), emotes.keys)
        assertEquals("https://cdn.frankerfacez.com/emote/1/2", emotes.getValue("WallE").url)
        assertEquals(25f / 32f, emotes.getValue("WallE").aspect, 0.001f)
        assertEquals("https://cdn.frankerfacez.com/emote/2/animated/2", emotes.getValue("Moving").animatedUrl)
    }

    @Test fun ffzGlobalReadsEveryDefaultSet() = runTest {
        val body = """{"default_sets":[3,5],"sets":{"3":{"emoticons":[{"id":1,"name":"A","urls":{"2":"u/a"}}]},"5":{"emoticons":[{"id":2,"name":"B","urls":{"1":"u/b"}}]},"9":{"emoticons":[{"id":3,"name":"C","urls":{"1":"u/c"}}]}}}"""
        assertEquals(setOf("A", "B"), FfzProvider(client("/v1/set/global" to (HttpStatusCode.OK to body))).global().map { it.name }.toSet())
    }

    @Test fun theSharedTwitchClientCanBeUsedAsIs() = runTest {
        // The providers reuse the app's Twitch HTTP client (same JSON settings); constructing it must stay side-effect free.
        twitchHttpClient().close()
    }
}
