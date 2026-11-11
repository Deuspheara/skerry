package com.twitchplayer.tv.core.platform

/**
 * What presenters need to know about the device, provided by the platform (Android: `Locale.getDefault()`).
 * Read once when a presenter is created, like the `Locale.getDefault()` call it replaces: a language switch in the app
 * recreates the screens.
 */
interface DeviceProfile {
    /** The interface language as a BCP 47 tag ("fr-FR", "zh-HK", "fil"). */
    val languageTag: String
}
