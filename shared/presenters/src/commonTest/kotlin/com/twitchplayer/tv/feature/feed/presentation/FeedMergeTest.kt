package com.twitchplayer.tv.feature.feed.presentation

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class FeedMergeTest {
    private fun merge(shown: List<String>, refreshed: List<String>, current: Int) = FeedMerge.merge(shown.toImmutableList(), refreshed, current) { it }

    @Test fun theFirstListIsTheRefreshedOne() {
        assertEquals(listOf("a", "b"), FeedMerge.merge(persistentListOf<String>(), listOf("a", "b"), 0) { it })
    }

    @Test fun thePagesUpToTheCurrentOneNeverMove() {
        assertEquals(listOf("a", "b", "c", "y", "x"), merge(listOf("a", "b", "c", "d"), listOf("y", "b", "x", "a"), current = 2))
        assertEquals(listOf("a", "b", "c"), merge(listOf("a", "b", "c", "d"), listOf("d", "c", "b", "a"), current = 2).take(3))
    }

    @Test fun aSeenChannelRankedLastByTheRefreshIsNotMovedUnderTheViewer() {
        // The viewer rests on "a"; the refresh puts "a" last (it counts as seen): the page stays first and is not repeated at the end.
        val merged = merge(listOf("a", "b", "c"), listOf("b", "c", "a"), current = 0)
        assertEquals(listOf("a", "b", "c"), merged)
    }

    @Test fun whatIsAheadFollowsTheRefreshedOrderWithoutRepeats() {
        assertEquals(listOf("a", "n1", "n2", "b"), merge(listOf("a", "b", "c"), listOf("n1", "n2", "a", "b"), current = 0))
    }

    @Test fun aStreamAheadThatEndedIsDropped() {
        assertEquals(listOf("a", "c"), merge(listOf("a", "b", "c"), listOf("a", "c"), current = 0))
    }

    @Test fun aCurrentPageThatWentOfflineStaysUntilTheViewerLeaves() {
        assertEquals(listOf("a", "b", "c"), merge(listOf("a", "b"), listOf("b", "c"), current = 0))
    }

    @Test fun anUnchangedListKeepsItsIdentity() {
        val shown = persistentListOf("a", "b", "c")
        assertSame(shown, FeedMerge.merge(shown, listOf("a", "b", "c"), 1) { it })
    }

    @Test fun aCurrentIndexPastTheEndKeepsEverythingShown() {
        assertEquals(listOf("a", "b", "n"), merge(listOf("a", "b"), listOf("n", "a"), current = 9))
    }
}
