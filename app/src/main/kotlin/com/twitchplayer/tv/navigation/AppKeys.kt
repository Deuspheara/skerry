package com.twitchplayer.tv.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Typed destinations (Navigation 3). Top-level keys are rail destinations. */
@Serializable data object HomeKey : NavKey
@Serializable data object FollowsKey : NavKey
@Serializable data object BrowseKey : NavKey
@Serializable data object SearchKey : NavKey
@Serializable data object ProfileKey : NavKey
@Serializable data class CategoryKey(val id: String, val name: String, val artUrl: String) : NavKey
@Serializable data class ChannelKey(val login: String) : NavKey
@Serializable data class PlayerKey(val login: String) : NavKey
@Serializable data class ClipKey(val slug: String, val title: String) : NavKey
@Serializable data class VodKey(val id: String, val title: String, val durationSeconds: Int) : NavKey

/** Rail order and the key each item opens. */
internal val TopLevelKeys: List<NavKey> = listOf(HomeKey, FollowsKey, BrowseKey, SearchKey, ProfileKey)

/** The rail item highlighted for [key]: Category belongs to Browse. Null hides the rail. */
internal fun railIndexOf(key: NavKey?): Int? = when (key) {
    HomeKey -> 0
    FollowsKey -> 1
    BrowseKey, is CategoryKey -> 2
    SearchKey -> 3
    ProfileKey -> 4
    else -> null
}
