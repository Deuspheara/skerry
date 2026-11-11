package com.twitchplayer.tv.core.data.model

import com.twitchplayer.tv.BuildConfig

/** The engine a fresh install starts with: the build's `-PplayerEngine` choice. */
fun PlayerEngine.Companion.buildDefault(): PlayerEngine = if (BuildConfig.PLAYER_ENGINE_DEFAULT == "webview") PlayerEngine.WebView else PlayerEngine.Native
