package com.twitchplayer.tv.feature.player

import com.twitchplayer.tv.feature.player.data.ChatAssets
import com.twitchplayer.tv.feature.player.data.ChatAssetsSource
import com.twitchplayer.tv.feature.player.data.ChatSource
import com.twitchplayer.tv.feature.player.data.chat.ChatEvent
import com.twitchplayer.tv.feature.player.data.chat.ChatFragment
import com.twitchplayer.tv.feature.player.data.chat.ChatMessage
import com.twitchplayer.tv.feature.player.data.chat.ChatMessageContent
import com.twitchplayer.tv.feature.player.data.emotes.Emote
import com.twitchplayer.tv.feature.player.presentation.ChatFeed
import com.twitchplayer.tv.feature.player.presentation.ChatPiece
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ChatFeedTest {
    private fun message(id: String, text: String = "hi $id") = ChatEvent.Message(ChatMessage(id, "b", "u", "Nick", "nick", ChatMessageContent(text, listOf(ChatFragment("text", text)))))

    private val events = MutableSharedFlow<ChatEvent>(extraBufferCapacity = 1024)
    private val source = ChatSource { _, _ -> events as Flow<ChatEvent> }

    private fun testScopeFeed(assets: ChatAssetsSource, max: Int = 200, dispatcher: kotlinx.coroutines.test.TestDispatcher) = ChatFeed(source, assets, batchMs = 250, maxLines = max, mapper = dispatcher, clock = { 0L })

    @Test fun eventsAreGroupedIntoOneUpdatePerWindowAndTheBufferIsBounded() = runTest {
        val feed = testScopeFeed({ flowOf(ChatAssets.Empty) }, max = 50, dispatcher = StandardTestDispatcher(testScheduler))
        val updates = mutableListOf<Int>()
        backgroundScope.launch { feed.lines.collect { updates += it.size } }
        backgroundScope.launch { feed.run("c", "me", "me") }
        runCurrent()
        advanceTimeBy(10)
        runCurrent()
        repeat(120) { events.emit(message("m$it")) }
        runCurrent()
        assertEquals(listOf(0), updates, "nothing before the window ends")
        advanceTimeBy(251)
        runCurrent()
        assertEquals(listOf(0, 50), updates, "one update for the whole burst, bounded to the newest 50")
        assertEquals("m119", feed.lines.value.last().id)
        assertEquals("m70", feed.lines.value.first().id)
    }

    @Test fun aRepeatedIdNeverReachesTheList() = runTest {
        val feed = testScopeFeed({ flowOf(ChatAssets.Empty) }, dispatcher = StandardTestDispatcher(testScheduler))
        backgroundScope.launch { feed.run("c", "me", "me") }
        runCurrent()
        advanceTimeBy(10)
        runCurrent()
        events.emit(message("same"))
        events.emit(message("same"))
        events.emit(message("other"))
        advanceTimeBy(251)
        runCurrent()
        events.emit(message("same"))
        advanceTimeBy(251)
        runCurrent()
        assertEquals(listOf("same", "other"), feed.lines.value.map { it.id })
    }

    @Test fun emotesLoadedLateApplyToTheMessagesThatFollow() = runTest {
        val assets = MutableStateFlow(ChatAssets.Empty)
        val feed = testScopeFeed({ assets }, dispatcher = StandardTestDispatcher(testScheduler))
        backgroundScope.launch { feed.run("c", "me", "me") }
        runCurrent()
        advanceTimeBy(10)
        runCurrent()
        events.emit(message("a", "go PogU"))
        advanceTimeBy(251)
        runCurrent()
        assets.value = ChatAssets(emptyMap(), mapOf("PogU" to Emote("PogU", "u")))
        runCurrent()
        events.emit(message("b", "go PogU"))
        advanceTimeBy(251)
        runCurrent()
        assertTrue(feed.lines.value[0].pieces.none { it is ChatPiece.Emote })
        assertTrue(feed.lines.value[1].pieces.any { it is ChatPiece.Emote })
    }

    @Test fun aHangingAssetSourceDoesNotBlockChatForever() = runTest {
        val feed = testScopeFeed({ flow { kotlinx.coroutines.awaitCancellation() } }, dispatcher = StandardTestDispatcher(testScheduler))
        backgroundScope.launch { feed.run("c", "me", "me") }
        runCurrent()
        advanceTimeBy(ChatFeed.ASSETS_WAIT_MS + 1)
        runCurrent()
        events.emit(message("a"))
        advanceTimeBy(300)
        runCurrent()
        assertEquals(listOf("a"), feed.lines.value.map { it.id }, "chat started once the wait was over, without assets")
    }

    @Test fun clearEmptiesTheBuffer() = runTest {
        val feed = testScopeFeed({ flowOf(ChatAssets.Empty) }, dispatcher = StandardTestDispatcher(testScheduler))
        backgroundScope.launch { feed.run("c", "me", "me") }
        runCurrent()
        advanceTimeBy(10)
        runCurrent()
        events.emit(message("a"))
        advanceTimeBy(251)
        runCurrent()
        feed.clear()
        assertTrue(feed.lines.value.isEmpty())
    }

    @Test fun moderationRemovesLinesAndTheRoomAndChannelEventsAreForwarded() = runTest {
        val feed = testScopeFeed({ flowOf(ChatAssets.Empty) }, dispatcher = StandardTestDispatcher(testScheduler))
        val notices = mutableListOf<com.twitchplayer.tv.feature.player.data.chat.ChannelNotice>()
        backgroundScope.launch { feed.notices.collect { notices += it } }
        backgroundScope.launch { feed.run("c", "me", "me") }
        runCurrent()
        advanceTimeBy(10)
        runCurrent()
        events.emit(message("a"))
        events.emit(message("b"))
        events.emit(message("c"))
        advanceTimeBy(251)
        runCurrent()
        events.emit(ChatEvent.Deleted("b"))
        advanceTimeBy(251)
        runCurrent()
        assertEquals(listOf("a", "c"), feed.lines.value.map { it.id })
        // A timeout removes every line of that viewer (the test messages are all from "nick").
        events.emit(ChatEvent.UserCleared("NICK"))
        advanceTimeBy(251)
        runCurrent()
        assertTrue(feed.lines.value.isEmpty())
        // Same batch: the line arrives, then the clear: nothing is left; a line after the clear stays.
        events.emit(message("d"))
        events.emit(ChatEvent.Cleared)
        events.emit(message("e"))
        advanceTimeBy(251)
        runCurrent()
        assertEquals(listOf("e"), feed.lines.value.map { it.id })
        val room = com.twitchplayer.tv.feature.player.data.chat.RoomState(slowSeconds = 30, subscribersOnly = true)
        events.emit(ChatEvent.Room(room))
        events.emit(ChatEvent.Channel(com.twitchplayer.tv.feature.player.data.chat.ChannelNotice.Offline))
        advanceTimeBy(251)
        runCurrent()
        assertEquals(room, feed.room.value)
        assertEquals(listOf<com.twitchplayer.tv.feature.player.data.chat.ChannelNotice>(com.twitchplayer.tv.feature.player.data.chat.ChannelNotice.Offline), notices)
    }
}
