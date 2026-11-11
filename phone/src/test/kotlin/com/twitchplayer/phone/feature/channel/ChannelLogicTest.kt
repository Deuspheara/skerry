package com.twitchplayer.phone.feature.channel

import com.twitchplayer.tv.core.data.model.ChannelDetails
import com.twitchplayer.tv.core.data.model.ChannelRef
import com.twitchplayer.tv.core.data.model.Replay
import com.twitchplayer.tv.feature.channel.presentation.ReplayTail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelLogicTest {
    private fun replay(id: String) = Replay(id = id, title = "t$id", duration = "1h", thumbnailUrl = "")

    private fun details(replays: List<Replay>, cursor: String? = null) = ChannelDetails(
        channel = ChannelRef(id = "1", login = "camille", name = "Camille", avatarUrl = null, isLive = false),
        stream = null,
        isFollowed = false,
        replays = replays,
        replaysCursor = cursor,
        similar = emptyList(),
    )

    @Test fun replaysAreTheFirstPageThenTheLoadedOnesWithoutRepeats() {
        val details = details(listOf(replay("1"), replay("2")))
        val tail = ReplayTail(replays = listOf(replay("2"), replay("3")), cursor = "c", started = true)

        assertEquals(listOf("1", "2", "3"), allReplays(details, tail).map { it.id })
    }

    @Test fun beforeAnyPageWasRequestedTheFirstCursorDecides() {
        assertTrue(hasMoreReplays(details(emptyList(), cursor = "first"), ReplayTail()))
        assertFalse(hasMoreReplays(details(emptyList(), cursor = null), ReplayTail()))
    }

    @Test fun onceStartedTheLastPagesCursorDecides() {
        val details = details(emptyList(), cursor = "first")

        assertTrue(hasMoreReplays(details, ReplayTail(cursor = "next", started = true)))
        assertFalse(hasMoreReplays(details, ReplayTail(cursor = null, started = true)))
    }

    @Test fun durationsUseTheSharedClockFormat() {
        assertEquals("0:45", durationLabel(45))
        assertEquals("2:05", durationLabel(125))
        assertEquals("1:02:03", durationLabel(3723))
        assertEquals("0:00", durationLabel(-5))
    }

    @Test fun liveForNeedsAKnownStartInThePast() {
        assertEquals("1:02:03", liveForLabel(startedAtMs = 1_000L, nowMs = 1_000L + 3_723_000L))
        assertNull(liveForLabel(startedAtMs = 0L, nowMs = 5_000L))
        assertNull(liveForLabel(startedAtMs = 9_000L, nowMs = 5_000L))
    }
}
