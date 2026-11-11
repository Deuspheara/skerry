package com.twitchplayer.tv.core.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class LiveStream(
    val channelId: String,
    val login: String,
    val name: String,
    val title: String,
    val categoryId: String,
    val categoryName: String,
    val viewers: Int,
    /** Thumbnail already sized for a 200 dp card (decoded at 2x). */
    val thumbnailUrl: String,
    val avatarUrl: String?,
    /** Stream start (epoch ms), 0 when unknown: the player shows the uptime. */
    val startedAtMs: Long = 0L,
    /** Tags the broadcaster set (free text, as Twitch returns them). */
    val tags: List<String> = emptyList(),
    /** Broadcast language code ("en", "fr"...), blank when unknown. */
    val language: String = "",
)

@Immutable
@Serializable
data class Category(
    val id: String,
    val name: String,
    /** Box art already sized 3:4 for the poster. */
    val artUrl: String,
    val group: CategoryGroup,
)

/** Coarse grouping for Browse filter chips; Twitch exposes no group field, so it is name-based. */
@Serializable
enum class CategoryGroup { Games, Irl, Music, Creative }

@Immutable
data class ChannelRef(
    val id: String,
    val login: String,
    val name: String,
    val avatarUrl: String?,
    val isLive: Boolean,
    val viewers: Int = 0,
)

@Immutable
data class Follows(val live: List<LiveStream>, val offline: List<ChannelRef>)

@Immutable
data class SearchResults(val channels: List<ChannelRef>, val categories: List<Category>)

@Immutable
data class Replay(
    val id: String,
    val title: String,
    val duration: String,
    val thumbnailUrl: String,
    val publishedAtMs: Long = 0L,
    val durationSeconds: Int = 0,
    val views: Int = 0,
)

/** One page of past broadcasts; [cursor] is null on the last page. */
@Immutable
data class ReplayPage(val replays: List<Replay>, val cursor: String? = null)

@Immutable
data class ScheduledStream(val title: String, val category: String, val startMs: Long, val endMs: Long = 0L)

@Immutable
data class ChannelClip(val slug: String, val title: String, val thumbnailUrl: String, val durationSeconds: Int, val views: Int, val createdAtMs: Long = 0L)

@Immutable
data class ChannelDetails(
    val channel: ChannelRef,
    val stream: LiveStream?,
    val isFollowed: Boolean,
    val replays: List<Replay>,
    /** Where the next page of replays starts; null when [replays] is everything. */
    val replaysCursor: String? = null,
    val similar: List<ChannelRef>,
    /** Upcoming scheduled streams, soonest first (cancelled ones left out). */
    val schedule: List<ScheduledStream> = emptyList(),
    /** Most viewed clips of the channel. */
    val clips: List<ChannelClip> = emptyList(),
)

@Immutable
data class Account(val userId: String, val login: String)

/** Chat visibility and placement: hidden, a column of ~24 % (compact) / ~28 % (large) of the screen, or over the video. */
enum class ChatMode { Hidden, Compact, Overlay }

enum class ChatTextSize(val sp: Float) { Small(14f), Medium(15f), Large(17f) }

/** Gap between two messages; each message adds 1 dp above and below on top of it (3, 5 and 8 dp apart). */
enum class ChatDensity(val gapDp: Int) { Compact(1), Standard(3), Relaxed(6) }

/** How many emotes may animate at once; beyond it they stay on their first frame. */
enum class AnimatedEmoteLimit(val count: Int) { Few(4), Some(8), Many(16) }

/** Chat look and behaviour, persisted. [backgroundOpacity] is a percentage of the panel colour (see [BACKGROUND_OPACITIES]). */
@Immutable
data class ChatSettings(
    val textSize: ChatTextSize = ChatTextSize.Medium,
    val density: ChatDensity = ChatDensity.Standard,
    val backgroundOpacity: Int = 80,
    val showBadges: Boolean = true,
    val showTimestamps: Boolean = false,
    val animatedEmotes: Boolean = true,
    val animatedLimit: AnimatedEmoteLimit = AnimatedEmoteLimit.Some,
) {
    companion object {
        val BACKGROUND_OPACITIES = listOf(40, 60, 80, 100)
    }
}

/**
 * Which player draws the video: [Native] (Media3 playing Twitch's HLS directly) or [WebView] (Twitch's embedded
 * player in a hosted page). A fresh install starts with the build's choice (`-PplayerEngine`, see app/build.gradle.kts).
 */
enum class PlayerEngine {
    Native,
    WebView,
    ;

    companion object {
        fun buildDefault(): PlayerEngine = if (com.twitchplayer.tv.BuildConfig.PLAYER_ENGINE_DEFAULT == "webview") WebView else Native
    }
}

@Immutable
data class AppSettings(
    val chatMode: ChatMode = ChatMode.Compact,
    val chat: ChatSettings = ChatSettings(),
    val animationsEnabled: Boolean = true,
    val recentSearches: List<String> = emptyList(),
    val followedCategories: Set<String> = emptySet(),
    val playerEngine: PlayerEngine = PlayerEngine.buildDefault(),
    /** Video quality picked in the native player (a variant id such as "720p60"); null = the default policy. */
    val preferredQuality: String? = null,
    /** Playback statistics (latency, bitrate, dropped frames...) over the native player. */
    val showStats: Boolean = false,
    /** Follow the channel being watched when it raids another one. */
    val followRaids: Boolean = false,
)
