package com.twitchplayer.phone.feature.discover

/** Rules of the Discover feed that do not depend on Compose, so a JVM test pins them. */
object FeedAudio {
    /**
     * Whether a preview player may hold audio focus. A muted preview must not: taking focus pauses the viewer's music or podcast just because
     * Discover opened. Focus is taken when the viewer turns the sound on, and given back when they mute again or the full player takes over.
     */
    fun takesFocus(muted: Boolean, playerOpen: Boolean): Boolean = !muted && !playerOpen
}

/**
 * Resolved addresses of clip files, kept [ttlMs] (ten minutes by default): the signed address of a clip stops working after a while, so one
 * resolved long ago is asked for again instead of being handed to a player that would fail on it. [now] is the wall clock (it keeps running in
 * deep sleep, unlike the monotonic one).
 */
class ClipUrlCache(private val ttlMs: Long = TTL_MS, private val now: () -> Long = System::currentTimeMillis) {
    private class Entry(val url: String, val atMs: Long)

    private val entries = HashMap<String, Entry>()

    /** The address of [slug] if one was stored less than the TTL ago (a clock set backwards counts as expired). */
    fun get(slug: String): String? {
        val entry = entries[slug] ?: return null
        val age = now() - entry.atMs
        return if (age in 0 until ttlMs) entry.url else null.also { entries.remove(slug) }
    }

    fun put(slug: String, url: String) {
        entries[slug] = Entry(url, now())
    }

    companion object {
        const val TTL_MS = 10 * 60_000L
    }
}

/** How many times and how long apart a failed clip resolution is retried before the page shows the thumbnail only. */
object ClipRetry {
    const val ATTEMPTS = 3
    const val DELAY_MS = 1_500L

    /** The wait before attempt [attempt] (1-based): 0 for the first, then growing. */
    fun delayBefore(attempt: Int): Long = if (attempt <= 1) 0L else DELAY_MS * (attempt - 1)
}
