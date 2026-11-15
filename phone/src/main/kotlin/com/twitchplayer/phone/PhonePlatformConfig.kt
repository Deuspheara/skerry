package com.twitchplayer.phone

import com.twitchplayer.tv.core.data.model.PlayerEngine
import com.twitchplayer.tv.core.di.PlatformConfig

/**
 * The phone's platform values. Playback is native (Media3) only: no hosted embed page, so the player host and parent stay
 * blank and the WebView engine is never offered.
 */
fun phonePlatformConfig() = PlatformConfig(
    twitchClientId = BuildConfig.TWITCH_CLIENT_ID,
    defaultPlayerEngine = PlayerEngine.Native,
    playerHost = "",
    playerParent = "",
    playbackClientId = BuildConfig.PLAYBACK_CLIENT_ID,
)
