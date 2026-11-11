package com.twitchplayer.tv.feature.player.data.chat

import com.twitchplayer.tv.core.platform.Logger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Drives [EventSubChatClient.run] against a scripted transport, in virtual time. */
@OptIn(ExperimentalCoroutinesApi::class)
class EventSubSessionTest {
    private class FakeSocket : EventSubSocket {
        val frames = Channel<String?>(Channel.UNLIMITED)
        var closed = false

        override suspend fun receive(): String? = frames.receive()

        override suspend fun close() {
            closed = true
            frames.close()
        }

        fun send(frame: String?) {
            frames.trySend(frame)
        }
    }

    private class Subscription(val sessionId: String, val type: String, val version: String, val condition: JsonObject)

    private class FakeTransport(private val clock: () -> Long) : EventSubTransport {
        /** Scripted outcomes of successive `connect` calls; an exhausted script keeps failing. */
        val script = ArrayDeque<Result<FakeSocket>>()
        val connects = mutableListOf<Pair<String, Long>>()
        val subscriptions = mutableListOf<Subscription>()
        var refuse: (String) -> Boolean = { false }

        override suspend fun connect(url: String): EventSubSocket {
            connects += url to clock()
            return (script.removeFirstOrNull() ?: Result.failure(IllegalStateException("no scripted socket"))).getOrThrow()
        }

        override suspend fun subscribe(sessionId: String, type: String, version: String, condition: JsonObject) {
            if (refuse(type)) error("EventSub subscription $type failed (403)")
            subscriptions += Subscription(sessionId, type, version, condition)
        }
    }

    private class Fixture(scope: TestScope) {
        val transport = FakeTransport { scope.testScheduler.currentTime }
        val warnings = mutableListOf<String>()
        val client = EventSubChatClient(transport, Logger { _, message -> warnings += message }, maxMessages = 3)
        val seen = mutableListOf<ChatEvent>()

        init {
            scope.backgroundScope.launch { client.events.collect { seen += it } }
            scope.backgroundScope.launch { client.run("100", "200") }
        }
    }

    private fun welcome(id: String = "S1") = envelope("session_welcome", session = """{"id":"$id"}""")

    private fun envelope(
        type: String,
        messageId: String = "m-$type",
        subscriptionType: String? = null,
        session: String? = null,
        event: String? = null,
    ): String {
        val meta = buildString {
            append("""{"message_type":"$type","message_id":"$messageId"""")
            if (subscriptionType != null) append(""","subscription_type":"$subscriptionType"""")
            append(""","message_timestamp":"2026-11-11T10:00:00Z"}""")
        }
        val payload = buildList {
            if (session != null) add(""""session":$session""")
            if (event != null) add(""""event":$event""")
        }.joinToString(",", "{", "}")
        return """{"metadata":$meta,"payload":$payload}"""
    }

    private fun chatMessage(id: String) = envelope(
        "notification",
        messageId = id,
        subscriptionType = "channel.chat.message",
        event = """{"broadcaster_user_id":"100","chatter_user_id":"7","chatter_user_login":"viewer","chatter_user_name":"Viewer",""" +
            """"message_id":"$id","message":{"text":"hi","fragments":[{"type":"text","text":"hi","emote":null}]},"badges":[],"message_type":"text"}""",
    )

    @Test fun welcomeSubscribesToChatThenAllBonusEventsAndReportsConnected() = runTest {
        val f = Fixture(this)
        val socket = FakeSocket().also { f.transport.script += Result.success(it) }
        runCurrent()
        // The state turns Connected as soon as the socket opens (before any welcome): the original behaviour.
        assertEquals(ChatConnectionState.Connected, f.client.connectionState.value)

        socket.send(welcome("S1"))
        runCurrent()

        assertEquals(ChatConnectionState.Connected, f.client.connectionState.value)
        assertEquals("channel.chat.message", f.transport.subscriptions.first().type)
        // The chat subscription goes first; the bonus ones follow together (their relative order is not part of the contract).
        assertEquals(EventSubChatClient.OPTIONAL.map { it.first }.toSet(), f.transport.subscriptions.drop(1).map { it.type }.toSet())
        assertTrue(f.transport.subscriptions.all { it.sessionId == "S1" })
        val chat = f.transport.subscriptions.first().condition
        assertEquals("100", chat["broadcaster_user_id"]?.jsonPrimitive?.content)
        assertEquals("200", chat["user_id"]?.jsonPrimitive?.content)
        val raid = f.transport.subscriptions.first { it.type == "channel.raid" }
        assertEquals("100", raid.condition["from_broadcaster_user_id"]?.jsonPrimitive?.content)
        assertEquals("2", f.transport.subscriptions.first { it.type == "channel.update" }.version)
    }

    @Test fun notificationsBecomeEventsAndDuplicateIdsAreDropped() = runTest {
        val f = Fixture(this)
        val socket = FakeSocket().also { f.transport.script += Result.success(it) }
        runCurrent()
        socket.send(welcome())
        socket.send(chatMessage("a"))
        socket.send(chatMessage("a"))
        socket.send(chatMessage("b"))
        runCurrent()

        val messages = f.seen.filterIsInstance<ChatEvent.Message>()
        assertEquals(listOf("a", "b"), messages.map { it.message.id })
        assertEquals("2026-11-11T10:00:00Z", messages.first().message.timestamp)
    }

    @Test fun theDedupWindowForgetsTheOldestIdsBeyondMaxMessages() = runTest {
        val f = Fixture(this)
        val socket = FakeSocket().also { f.transport.script += Result.success(it) }
        runCurrent()
        socket.send(welcome())
        listOf("a", "b", "c", "d", "a").forEach { socket.send(chatMessage(it)) }
        runCurrent()

        // maxMessages = 3: "a" was evicted when "d" arrived, so its replay is a fresh message again.
        assertEquals(listOf("a", "b", "c", "d", "a"), f.seen.filterIsInstance<ChatEvent.Message>().map { it.message.id })
    }

    @Test fun keepalivesAndNonTextFramesAreIgnoredAndAnUndecodableEventDoesNotEndTheSession() = runTest {
        val f = Fixture(this)
        val socket = FakeSocket().also { f.transport.script += Result.success(it) }
        runCurrent()
        socket.send(welcome())
        socket.send(null)
        socket.send(envelope("session_keepalive"))
        socket.send(envelope("notification", messageId = "bad", subscriptionType = "channel.chat.message", event = """{"unexpected":true}"""))
        socket.send(chatMessage("ok"))
        runCurrent()

        assertEquals(ChatConnectionState.Connected, f.client.connectionState.value)
        assertEquals(1, f.transport.connects.size)
        assertEquals(listOf("ok"), f.seen.filterIsInstance<ChatEvent.Message>().map { it.message.id })
    }

    @Test fun sessionReconnectMigratesToTheNewSocketAfterItsWelcomeAndClosesTheOldOne() = runTest {
        val f = Fixture(this)
        val old = FakeSocket().also { f.transport.script += Result.success(it) }
        val migrated = FakeSocket().also { f.transport.script += Result.success(it) }
        runCurrent()
        old.send(welcome("S1"))
        old.send(envelope("session_reconnect", session = """{"id":"S1","reconnect_url":"wss://reconnect.example/ws"}"""))
        migrated.send(welcome("S2"))
        runCurrent()

        assertEquals(listOf("wss://eventsub.wss.twitch.tv/ws", "wss://reconnect.example/ws"), f.transport.connects.map { it.first })
        assertTrue(old.closed)
        assertEquals(false, migrated.closed)
        migrated.send(chatMessage("after"))
        runCurrent()
        assertEquals(listOf("after"), f.seen.filterIsInstance<ChatEvent.Message>().map { it.message.id })
        assertEquals(ChatConnectionState.Connected, f.client.connectionState.value)
    }

    @Test fun aReplacementThatDoesNotWelcomeIsClosedAndTheSessionReconnectsAfterOneSecond() = runTest {
        val f = Fixture(this)
        val old = FakeSocket().also { f.transport.script += Result.success(it) }
        val bad = FakeSocket().also { f.transport.script += Result.success(it) }
        val fresh = FakeSocket().also { f.transport.script += Result.success(it) }
        runCurrent()
        old.send(welcome("S1"))
        old.send(envelope("session_reconnect", session = """{"id":"S1","reconnect_url":"wss://reconnect.example/ws"}"""))
        bad.send(envelope("session_keepalive"))
        runCurrent()

        assertTrue(bad.closed)
        assertTrue(old.closed, "the failed session is closed by the loop's finally")
        val error = f.client.connectionState.value
        assertIs<ChatConnectionState.Error>(error)
        assertEquals("Replacement EventSub session did not welcome", error.message)

        advanceTimeBy(999)
        runCurrent()
        assertEquals(2, f.transport.connects.size)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(3, f.transport.connects.size)
        assertEquals(1_000L, f.transport.connects.last().second - f.transport.connects[1].second)
        fresh.send(welcome("S3"))
        runCurrent()
        assertEquals(ChatConnectionState.Connected, f.client.connectionState.value)
    }

    @Test fun aReconnectFrameWithoutUrlFailsTheSession() = runTest {
        val f = Fixture(this)
        val socket = FakeSocket().also { f.transport.script += Result.success(it) }
        runCurrent()
        socket.send(welcome())
        socket.send(envelope("session_reconnect", session = """{"id":"S1"}"""))
        runCurrent()

        assertEquals(ChatConnectionState.Error("Reconnect frame omitted reconnect URL"), f.client.connectionState.value)
        assertTrue(socket.closed)
    }

    @Test fun revokingTheChatSubscriptionEndsTheSessionButABonusRevocationDoesNot() = runTest {
        val f = Fixture(this)
        val socket = FakeSocket().also { f.transport.script += Result.success(it) }
        runCurrent()
        socket.send(welcome())
        socket.send(envelope("revocation", subscriptionType = "channel.raid"))
        runCurrent()
        assertEquals(ChatConnectionState.Connected, f.client.connectionState.value)
        assertEquals(false, socket.closed)

        socket.send(envelope("revocation", subscriptionType = "channel.chat.message"))
        runCurrent()
        assertEquals(ChatConnectionState.Error("Twitch revoked the chat subscription"), f.client.connectionState.value)
        assertTrue(socket.closed)
    }

    @Test fun aRefusedChatSubscriptionFailsTheSessionWhileRefusedBonusSubscriptionsOnlyWarn() = runTest {
        val bonus = Fixture(this)
        bonus.transport.refuse = { it != "channel.chat.message" }
        val socket = FakeSocket().also { bonus.transport.script += Result.success(it) }
        runCurrent()
        socket.send(welcome())
        runCurrent()
        assertEquals(ChatConnectionState.Connected, bonus.client.connectionState.value)
        assertEquals(EventSubChatClient.OPTIONAL.size, bonus.warnings.size)
        assertTrue(bonus.warnings.all { it.startsWith("bonus subscription refused: EventSub subscription ") })
    }

    @Test fun aRefusedChatSubscriptionIsAnErrorAndTheLoopRetries() = runTest {
        val f = Fixture(this)
        f.transport.refuse = { it == "channel.chat.message" }
        val socket = FakeSocket().also { f.transport.script += Result.success(it) }
        runCurrent()
        socket.send(welcome())
        runCurrent()

        assertEquals(ChatConnectionState.Error("EventSub subscription channel.chat.message failed (403)"), f.client.connectionState.value)
        assertTrue(f.transport.subscriptions.isEmpty())
        assertTrue(socket.closed)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(2, f.transport.connects.size)
    }

    @Test fun aWelcomeWithoutSessionIdIsAnError() = runTest {
        val f = Fixture(this)
        val socket = FakeSocket().also { f.transport.script += Result.success(it) }
        runCurrent()
        socket.send(envelope("session_welcome", session = "{}"))
        runCurrent()

        assertEquals(ChatConnectionState.Error("EventSub welcome omitted session ID"), f.client.connectionState.value)
    }

    @Test fun aClosedSocketReconnectsAndTheBackoffDoublesUpToThirtySeconds() = runTest {
        val f = Fixture(this)
        // The script is empty after the first socket: every later connect fails.
        val socket = FakeSocket().also { f.transport.script += Result.success(it) }
        runCurrent()
        socket.send(welcome())
        runCurrent()
        socket.frames.close()
        runCurrent()
        advanceTimeBy(200_000)
        runCurrent()

        val times = f.transport.connects.map { it.second }
        val gaps = times.zipWithNext { a, b -> (b - a) / 1000 }
        // 1, 2, 4, 8, 16, then capped at 30 s.
        assertEquals(listOf(1L, 2L, 4L, 8L, 16L, 30L, 30L), gaps.take(7))
    }

    @Test fun stopEndsTheLoopWithoutReconnectingAndReportsDisconnected() = runTest {
        val f = Fixture(this)
        val socket = FakeSocket().also { f.transport.script += Result.success(it) }
        runCurrent()
        socket.send(welcome())
        runCurrent()

        f.client.stop()
        socket.send(envelope("session_keepalive"))
        runCurrent()
        // The loop notices `stopped` after the next frame, leaves the session and skips the delay.
        assertEquals(ChatConnectionState.Disconnected, f.client.connectionState.value)
        assertEquals(1, f.transport.connects.size)
        assertTrue(socket.closed)
    }

    @Test fun runRejectsBlankIdentifiers() = runTest {
        val f = EventSubChatClient(FakeTransport { 0 }, Logger { _, _ -> })
        assertFailsWith<IllegalArgumentException> { f.run(" ", "2") }
        assertFailsWith<IllegalArgumentException> { f.run("1", "") }
    }
}
