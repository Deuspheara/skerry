package com.twitchplayer.tv.core.data

import com.twitchplayer.tv.core.data.model.LiveStream
import com.twitchplayer.tv.core.data.repository.newlyLive
import com.twitchplayer.tv.core.data.repository.upcoming
import com.twitchplayer.tv.core.network.api.ScheduleCategory
import com.twitchplayer.tv.core.network.api.ScheduleSegment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

private fun stream(id: String) = LiveStream(id, id, id.replaceFirstChar(Char::uppercase), "title $id", "cat", "cat", 100, "", null)

class LiveWatcherTest {
    @Test fun nothingIsNewAtTheFirstCheck() {
        assertTrue(newlyLive(null, listOf(stream("a"), stream("b"))).isEmpty())
    }

    @Test fun onlyChannelsAbsentFromThePreviousCheckAreNew() {
        assertEquals(listOf("c"), newlyLive(setOf("a", "b"), listOf(stream("a"), stream("c"))).map { it.login })
        assertTrue(newlyLive(setOf("a"), emptyList()).isEmpty())
    }

    @Test fun scheduleKeepsUpcomingNotCancelledSlotsSoonestFirst() {
        val now = Instant.parse("2026-11-07T12:00:00Z").toEpochMilliseconds()

        fun seg(start: String, end: String? = null, cancelled: Boolean = false, title: String = "t") = ScheduleSegment(
            "i",
            start,
            end,
            title,
            if (cancelled) "2026-12-01T00:00:00Z" else null,
            ScheduleCategory("1", "Chess"),
        )
        val list = listOf(
            seg("2026-11-09T18:00:00Z", title = "later"),
            seg("2026-11-06T18:00:00Z", end = "2026-11-06T20:00:00Z", title = "past"),
            seg("2026-11-08T18:00:00Z", cancelled = true, title = "cancelled"),
            seg("2026-11-07T11:00:00Z", end = "2026-11-07T14:00:00Z", title = "running"),
            seg("2026-11-08T09:00:00Z", title = "soon"),
        )
        assertEquals(listOf("running", "soon", "later"), list.upcoming(now).map { it.title })
        assertEquals("Chess", list.upcoming(now).first().category)
    }
}
