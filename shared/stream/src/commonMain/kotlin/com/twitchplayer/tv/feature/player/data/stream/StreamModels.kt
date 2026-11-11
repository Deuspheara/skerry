package com.twitchplayer.tv.feature.player.data.stream

/** One rendition of a live stream, read from the master playlist. [id] is Twitch's group id ("chunked" = source, "720p60", "audio_only"). */
data class StreamVariant(
    val id: String,
    /** Twitch's own label, e.g. "1080p60 (source)". */
    val name: String,
    val bandwidth: Int,
    val width: Int,
    val height: Int,
    val frameRate: Float,
    val codecs: String,
    /** Media playlist of this rendition: playing it pins the quality (no adaptive switching). */
    val url: String,
) {
    val audioOnly: Boolean get() = id == AUDIO_ONLY || height == 0
    val isSource: Boolean get() = id == SOURCE

    companion object {
        const val AUDIO_ONLY = "audio_only"
        const val SOURCE = "chunked"
    }
}

/**
 * What the player needs to start a channel. [masterUrl] is the adaptive playlist (usher, token embedded); [variants] are
 * its renditions, best first, audio only last.
 */
data class ResolvedStream(
    val channel: String,
    val masterUrl: String,
    val variants: List<StreamVariant>,
)

/** The channel is not live: usher has no stream for it (HTTP 404 `transcode_does_not_exist`). Not worth retrying. */
class ChannelOfflineException(val channel: String) : Exception("$channel is offline")

/**
 * Twitch refused to hand out a playback token or playlist. [status] and [body] are exactly what the server answered,
 * [integrity] is true for the "failed integrity check" answer (Twitch wants a client-integrity token this app does not have).
 */
class StreamAccessException(
    val stage: String,
    val status: Int,
    val body: String,
    val integrity: Boolean = false,
    val retryable: Boolean = false,
) : Exception("$stage failed: HTTP $status${if (integrity) " (integrity check)" else ""}: ${body.take(300)}")
