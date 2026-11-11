package com.twitchplayer.tv.feature.player.emotes

import com.twitchplayer.tv.feature.player.data.emotes.EmoteChange
import com.twitchplayer.tv.feature.player.data.emotes.SevenTvProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/** The event-stream client comes from a platform-built factory; the set id learned by `channel()` gates `changes()`. */
class SevenTvEventStreamTest {
    private val user = """{"emote_set":{"id":"SET1","emotes":[]}}"""
    private val dispatch =
        """{"type":"emote_set.update","body":{"pushed":[{"key":"emotes","value":{"id":"E9","name":"Fresh","flags":0,"data":{"animated":false}}}]}}"""

    private fun http() = HttpClient(
        MockEngine { respond(user, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())) },
    ) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }

    private fun stream(onConnect: (String) -> Unit) = HttpClient(
        MockEngine { request ->
            onConnect(request.url.toString())
            respond("event: hello\ndata: {}\n\nevent: dispatch\ndata: $dispatch\n\n", HttpStatusCode.OK)
        },
    )

    @Test fun aChannelWhoseSetIsUnknownNeverOpensTheStream() = runTest {
        var created = 0
        val provider = SevenTvProvider(http()) {
            created++
            stream { }
        }
        provider.changes("42").collect { error("no change expected") }
        assertEquals(0, created)
    }

    @Test fun afterChannelLearnedTheSetTheStreamIsOpenedAndItsDispatchesAreEmitted() = runTest {
        var created = 0
        val urls = Channel<String>(Channel.UNLIMITED)
        val provider = SevenTvProvider(http()) {
            created++
            stream { urls.trySend(it) }
        }
        provider.channel("42")
        val received = Channel<EmoteChange>(Channel.UNLIMITED)
        val job = backgroundScope.launch { provider.changes("42").collect { received.trySend(it) } }
        // The mock engine answers on its own dispatcher: wait in real time, not on the test scheduler.
        val url = withContext(Dispatchers.Default) { withTimeout(10_000) { urls.receive() } }
        val change = withContext(Dispatchers.Default) { withTimeout(10_000) { received.receive() } }
        assertEquals("https://events.7tv.io/v3@emote_set.update%3Cobject_id=SET1%3E", url)
        assertEquals(listOf("Fresh"), change.added.map { it.name })
        assertEquals(1, created)
        job.cancel()
    }
}
