package com.twitchplayer.tv.core.platform

import com.twitchplayer.tv.core.data.model.ChatSettings

/** Per-device starting values for settings (e.g. fewer animated emotes on low-memory TVs). */
data class SettingsDefaults(val chat: ChatSettings = ChatSettings())
