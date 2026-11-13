package com.twitchplayer.phone.navigation

import androidx.compose.runtime.Immutable
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Typed destinations (Navigation 3). The tab roots are the bottom bar / rail destinations; Search and Settings are pages opened from the
 * header (the search pill and the account button), not tabs.
 */
@Serializable data object HomeKey : NavKey

@Serializable data object DiscoverKey : NavKey

@Serializable data object FollowingKey : NavKey

@Serializable data object BrowseKey : NavKey

@Serializable data object SearchKey : NavKey

@Serializable data object SettingsKey : NavKey

@Serializable data class CategoryKey(val id: String, val name: String, val artUrl: String = "") : NavKey

@Serializable data class ChannelKey(val login: String) : NavKey

/**
 * The tabs, in bar order; each keeps its own back stack. The bar always shows all four, in the same places whoever is signed in
 * (an icon-only bar must not move its icons); a tab that needs a viewer (Following) shows its own sign-in message instead of hiding.
 */
enum class PhoneTab(val root: NavKey) {
    Home(HomeKey),
    Discover(DiscoverKey),
    Following(FollowingKey),
    Browse(BrowseKey),
    ;

    companion object {
        /** The tab a destination belongs to: category and channel pages belong to the tab they were opened from. */
        fun of(key: NavKey): PhoneTab? = entries.firstOrNull { it.root == key }

        /** The tabs the bar shows: all of them, always. */
        val bar: List<PhoneTab> get() = entries
    }
}

/** What a tap on a stream, replay or clip plays: the player session's target, not a page of the navigation. */
@Immutable
sealed interface PlaybackTarget {
    data class Live(val login: String) : PlaybackTarget
    data class Replay(
        val id: String,
        val title: String,
        val durationSeconds: Int,
        val channelLogin: String = "",
        val channelName: String = "",
        val channelAvatarUrl: String? = null,
        val views: Int = 0,
        val publishedAtMs: Long = 0L,
    ) : PlaybackTarget
    data class Clip(val slug: String, val title: String) : PlaybackTarget
}
