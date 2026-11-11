package com.twitchplayer.tv.feature.player.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * What the player screen asks of whatever draws the video. Two implementations: [com.twitchplayer.tv.feature.player.ui.embed.WebViewVideoPlayer]
 * (Twitch's embed in a hosted page) and [NativeVideoPlayer] (Media3 over Twitch's HLS). The screen never knows which one it drives.
 */
interface VideoPlayer {
    fun play()
    fun pause()
    fun toggleMute()

    /** Qualities on offer for the current stream and the one in use; [VideoQualities.None] while unknown. */
    fun loadQualities(onResult: (VideoQualities) -> Unit)

    /** [id] is one of [VideoQuality.id] from [loadQualities]. Kept for later streams. */
    fun setQuality(id: String)

    /** Lets D-pad focus enter the video (the embed's own prompts); a no-op for the native player, which has none. */
    fun setInteractive(enabled: Boolean)

    /** Live statistics while [setStatsEnabled] is on; always null for a player that cannot measure (the embed). */
    val stats: StateFlow<PlaybackStats?> get() = NoStats

    /** The ad break being played, when the player can tell (native player only). */
    val adBreak: StateFlow<AdBreak?> get() = NoAd

    fun setStatsEnabled(enabled: Boolean) = Unit
}

private val NoStats = MutableStateFlow<PlaybackStats?>(null)
private val NoAd = MutableStateFlow<AdBreak?>(null)

/** One selectable video quality; [id] is what [VideoPlayer.setQuality] expects ("chunked" is the source quality). */
data class VideoQuality(val id: String, val label: String)

/** Qualities offered and the id of the one in use (null while unknown). */
data class VideoQualities(val options: List<VideoQuality>, val selected: String?) {
    companion object {
        val None = VideoQualities(emptyList(), null)

        /** Parses the embed page's JSON answer, which `evaluateJavascript` hands back as a JSON-encoded string or `null`. */
        fun parse(raw: String?): VideoQualities {
            if (raw.isNullOrBlank() || raw == "null") return None
            return runCatching {
                // A string result arrives quoted and escaped: unwrap it once, then read the object inside.
                var element = Json.parseToJsonElement(raw)
                if (element is JsonPrimitive && element.isString) element = Json.parseToJsonElement(element.content)
                val root = element.jsonObject
                val options = root.getValue("qualities").jsonArray.mapNotNull { item ->
                    val fields = item.jsonObject
                    val id = fields["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    if (id.isEmpty()) null else VideoQuality(id, fields["name"]?.jsonPrimitive?.contentOrNull?.ifEmpty { null } ?: id)
                }
                VideoQualities(options, root["selected"]?.jsonPrimitive?.contentOrNull?.ifEmpty { null })
            }.getOrDefault(None)
        }
    }
}
