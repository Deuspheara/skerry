package com.twitchplayer.phone.feature.player

import com.twitchplayer.phone.navigation.PlaybackTarget

/** What the replay page knows about the replay before it resolves it: shown under the picture and used to offer the channel's other replays. */
data class ReplayInfo(
    val id: String,
    val title: String,
    val durationSeconds: Int,
    val channelLogin: String = "",
    val channelName: String = "",
    val channelAvatarUrl: String? = null,
    val views: Int = 0,
    val publishedAtMs: Long = 0L,
)

fun PlaybackTarget.Replay.toInfo() = ReplayInfo(id, title, durationSeconds, channelLogin, channelName, channelAvatarUrl, views, publishedAtMs)
