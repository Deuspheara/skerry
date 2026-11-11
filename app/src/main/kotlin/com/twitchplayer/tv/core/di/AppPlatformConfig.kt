package com.twitchplayer.tv.core.di

import com.twitchplayer.tv.BuildConfig
import com.twitchplayer.tv.core.data.model.PlayerEngine
import com.twitchplayer.tv.core.data.model.buildDefault

/** The TV app's build-time values for `:platform-android` (it has no BuildConfig of its own). */
fun appPlatformConfig() = PlatformConfig(
    twitchClientId = BuildConfig.TWITCH_CLIENT_ID,
    defaultPlayerEngine = PlayerEngine.buildDefault(),
    playerHost = BuildConfig.TWITCH_PLAYER_HOST,
    playerParent = BuildConfig.TWITCH_PLAYER_PARENT,
)
