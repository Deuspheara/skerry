package com.twitchplayer.phone.feature.player

import com.twitchplayer.tv.core.data.repository.SettingsRepository
import com.twitchplayer.tv.feature.player.data.stream.QualityPolicy
import com.twitchplayer.tv.feature.player.data.stream.StreamVariant
import com.twitchplayer.tv.feature.player.playback.VideoQualities
import com.twitchplayer.tv.feature.player.playback.VideoQuality
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** The qualities a replay offers and which address plays each (the live player keeps its own list in `NativeVideoPlayer`). */
object ReplayQuality {
    /** The quality a replay starts in: the viewer's saved choice when this replay has it, else the default policy; Auto when nothing fits. */
    fun initial(variants: List<StreamVariant>, preferred: String?): String = QualityPolicy.choose(variants, preferred)?.id ?: QualityPolicy.AUTO

    /** "Auto" first (the adaptive playlist), then every variant; [selected] is the one in use. */
    fun options(variants: List<StreamVariant>, autoLabel: String, selected: String): VideoQualities = VideoQualities(listOf(VideoQuality(QualityPolicy.AUTO, autoLabel)) + variants.map { VideoQuality(it.id, it.name) }, selected)

    /** The playlist to open for [id]: Auto and unknown ids play the adaptive master playlist. */
    fun urlFor(variants: List<StreamVariant>, masterUrl: String, id: String): String = if (id == QualityPolicy.AUTO) masterUrl else variants.firstOrNull { it.id == id }?.url ?: masterUrl
}

/** Picking a quality is a standing choice, shared with the live player and the TV: it is saved, and the next stream or replay starts there. */
class QualityChoice(private val settings: SettingsRepository, private val scope: CoroutineScope) {
    fun pick(id: String) {
        scope.launch { settings.setPreferredQuality(id) }
    }
}
