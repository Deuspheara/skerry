package com.twitchplayer.tv.feature.feed.presentation

import com.twitchplayer.tv.core.data.model.FeedAutoplay
import com.twitchplayer.tv.core.data.model.LiveStream

/**
 * What the Discover feed shows and in which order. Twitch's own recommendation ranking is not public, so the feed is built from what the
 * API gives a viewer: the followed channels that are live come first (the viewer asked for them), then the busiest streams in their language.
 */
object FeedRanking {
    const val LIMIT = 40

    /** At most this many followed streams lead the feed, so a viewer who follows many channels still sees something new within a few swipes. */
    const val MAX_FOLLOWED = 8

    /**
     * [followed] (live, most viewers first) then [top], each channel once. [language] is the viewer's Twitch language code: a stream in
     * another language is left out of [top], one with an unknown language stays. [seen] are channels the viewer already swiped past this
     * session; they go to the end instead of coming back first.
     */
    fun rank(followed: List<LiveStream>, top: List<LiveStream>, language: String?, seen: Set<String> = emptySet(), limit: Int = LIMIT): List<LiveStream> {
        val lead = followed.sortedByDescending { it.viewers }.take(MAX_FOLLOWED)
        val rest = top.filter { matchesLanguage(it, language) }
        val ordered = (lead + rest).distinctBy { it.channelId }
        val (fresh, old) = ordered.partition { it.channelId !in seen }
        return (fresh + old).take(limit)
    }

    private fun matchesLanguage(stream: LiveStream, language: String?): Boolean = language.isNullOrBlank() || stream.language.isBlank() || stream.language.equals(language, ignoreCase = true)
}

/** Whether a preview may start by itself under the viewer's [FeedAutoplay] choice and the connection. */
object FeedAutoplayPolicy {
    fun allowed(setting: FeedAutoplay, metered: Boolean): Boolean = when (setting) {
        FeedAutoplay.Always -> true
        FeedAutoplay.WifiOnly -> !metered
        FeedAutoplay.Off -> false
    }
}
