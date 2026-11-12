package com.twitchplayer.phone.navigation

import androidx.compose.runtime.Immutable
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Typed destinations (Navigation 3). The four tab roots are the bottom bar / rail destinations. */
@Serializable data object HomeKey : NavKey

@Serializable data object BrowseKey : NavKey

@Serializable data object SearchKey : NavKey

@Serializable data object SettingsKey : NavKey

@Serializable data class CategoryKey(val id: String, val name: String) : NavKey

@Serializable data class ChannelKey(val login: String) : NavKey

/** The four tabs, in bar order; each keeps its own back stack. */
enum class PhoneTab(val root: NavKey) {
    Home(HomeKey),
    Browse(BrowseKey),
    Search(SearchKey),
    Settings(SettingsKey),
    ;

    companion object {
        /** The tab a destination belongs to: category and channel pages belong to the tab they were opened from. */
        fun of(key: NavKey): PhoneTab? = entries.firstOrNull { it.root == key }
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
