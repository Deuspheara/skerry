package com.twitchplayer.tv.core.platform

import com.twitchplayer.tv.core.data.model.PlayerEngine

/**
 * Build-time values shared code reads (on Android: from `BuildConfig`). Player host/parent and the native-player flag
 * are only read by the Android UI and stay in `BuildConfig` there.
 */
data class AppConfig(
    /** The public Twitch OAuth client id (no secret exists). */
    val clientId: String,
    /** The engine a fresh install starts with. */
    val defaultPlayerEngine: PlayerEngine = PlayerEngine.Fallback,
    /** Client id sent with the unofficial playback requests; blank (the default) switches native playback off. See LEGAL.md. */
    val playbackClientId: String = "",
)
