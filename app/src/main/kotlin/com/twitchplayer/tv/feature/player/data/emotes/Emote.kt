package com.twitchplayer.tv.feature.player.data.emotes

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * One drawable chat word, whatever its provider. [url] is always a still image; [animatedUrl] is set only for
 * emotes that have an animated variant, so a renderer can pick the cheap still when it has no animation budget.
 * [aspect] is width / height, or 0 when the provider does not say.
 */
@Immutable
data class Emote(
    val name: String,
    val url: String,
    val animatedUrl: String? = null,
    /** Overlay emote (7TV "zero width"): drawn on top of the emote before it instead of taking room of its own. */
    val zeroWidth: Boolean = false,
    val aspect: Float = 0f,
) {
    val animated: Boolean get() = animatedUrl != null
}

/** Live edit of a channel's emote set: [removed] are names (applied first), then [added]. */
data class EmoteChange(val added: List<Emote> = emptyList(), val removed: Set<String> = emptySet())

/**
 * A third-party emote source (7TV, BTTV, FFZ). Adding a provider means implementing this and listing it in the
 * DI module in precedence order; nothing in the renderer changes. Functions throw on transport errors: the
 * repository isolates them per provider. An empty list means "this channel has none".
 */
interface EmoteProvider {
    suspend fun global(): List<Emote>
    suspend fun channel(channelId: String): List<Emote>
    /** Push updates for the channel's set, for providers that have them. Collected while chat is open. */
    fun changes(channelId: String): Flow<EmoteChange> = emptyFlow()
}
