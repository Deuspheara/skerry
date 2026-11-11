package com.twitchplayer.tv.feature.player.playback

import kotlin.time.Instant

/** What the playback statistics overlay shows. Every field is "unknown" (null / 0) until the player has data for it. */
data class PlaybackStats(
    /** Delay behind the live edge, from the playlist's program date-time tags. */
    val latencyMs: Long? = null,
    /** Media buffered ahead of the playhead. */
    val bufferMs: Long = 0,
    /** Bitrate of the rendition being played (bits per second), 0 when unknown. */
    val videoBitrate: Int = 0,
    /** The network's measured throughput (bits per second), 0 before the first estimate. */
    val bandwidth: Long = 0,
    val droppedFrames: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val frameRate: Float = 0f,
    val codec: String? = null,
    val speed: Float = 1f,
)

/** An advertisement break stitched into the stream; [remainingMs] counts down to the end of the break. */
data class AdBreak(val remainingMs: Long)

/**
 * Reads Twitch's ad markers from the raw tags of a media playlist. Twitch stitches ads into the live stream and marks them
 * with `#EXT-X-DATERANGE` lines (`CLASS="twitch-stitched-ad"` or an `ID` starting with `stitched-ad`) that carry the
 * break's `START-DATE` and `DURATION`. These tags are not documented by Twitch and may change: anything unexpected is
 * ignored, and the app only ever uses them to tell the viewer an ad is playing (it never alters or skips it).
 */
object HlsAds {
    private val attribute = Regex("""([A-Z0-9-]+)=("([^"]*)"|[^,]*)""")

    /** The break covering [playheadMs] (epoch ms of the picture now on screen), or null. */
    fun current(tags: List<String>, playheadMs: Long): AdBreak? {
        var best: AdBreak? = null
        for (tag in tags) {
            if (!tag.startsWith("#EXT-X-DATERANGE:")) continue
            val a = attributes(tag.substringAfter(':'))
            val ad = a["CLASS"]?.contains("stitched-ad") == true || a["ID"]?.startsWith("stitched-ad") == true
            if (!ad) continue
            val start = a["START-DATE"]?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() } ?: continue
            val duration = a["DURATION"]?.toDoubleOrNull() ?: a["PLANNED-DURATION"]?.toDoubleOrNull() ?: continue
            val remaining = start + (duration * 1000).toLong() - playheadMs
            if (playheadMs >= start && remaining > 0 && (best == null || remaining > best.remainingMs)) best = AdBreak(remaining)
        }
        return best
    }

    private fun attributes(list: String): Map<String, String> = attribute.findAll(list).associate { it.groupValues[1] to (it.groups[3]?.value ?: it.groupValues[2]) }
}
