package com.twitchplayer.tv.feature.feed.presentation

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * How a refreshed Discover list joins the one the viewer is swiping through. The pages up to and including the one they rest on are theirs: they
 * never move, never reorder and never disappear, so nothing teleports under their thumb (a refresh can rank the channel they are watching last,
 * because it counts as seen). What changes is what is still ahead: the refreshed streams that are not already among those pages, in the
 * refreshed order. A stream ahead that has ended (not in the refreshed list) is dropped.
 */
object FeedMerge {
    fun <T> merge(shown: ImmutableList<T>, refreshed: List<T>, currentIndex: Int, key: (T) -> Any): ImmutableList<T> {
        if (shown.isEmpty()) return refreshed.toImmutableList()
        val head = shown.take((currentIndex + 1).coerceIn(1, shown.size))
        val seen = head.mapTo(HashSet(), key)
        val tail = refreshed.filter { seen.add(key(it)) }
        val merged = head + tail
        // The same list as before keeps its identity: nothing downstream has anything to redo.
        return if (merged == shown) shown else merged.toImmutableList()
    }
}
