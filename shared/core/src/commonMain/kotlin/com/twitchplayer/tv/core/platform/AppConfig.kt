package com.twitchplayer.tv.core.platform

import com.twitchplayer.tv.core.data.model.PlayerEngine

/** Build-time values each app provides (on Android: from `BuildConfig`). */
data class AppConfig(
    /** The public Twitch OAuth client id (no secret exists). */
    val clientId: String,
    /** HTTPS origin of the hosted embed page; blank when no hosted page is configured. */
    val playerHost: String = "",
    /** Hostname Twitch checks as `parent`; blank when no hosted page is configured. */
    val playerParent: String = "",
    /** False when the native player is compiled out (WebView only). */
    val nativePlayerEnabled: Boolean = true,
    /** The engine a fresh install starts with. */
    val defaultPlayerEngine: PlayerEngine = PlayerEngine.Fallback,
)
