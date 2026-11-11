package com.twitchplayer.tv.core.di

import com.twitchplayer.tv.core.platform.DeviceProfile
import java.util.Locale

/** The interface language of the device (and of the in-app language picker, which sets the default locale). */
class AndroidDeviceProfile(private val locale: () -> Locale = Locale::getDefault) : DeviceProfile {
    override val languageTag: String get() = locale().deviceLanguageTag()
}

/**
 * BCP 47 tag of this locale, or "" for the root locale: `toLanguageTag()` would say "und", while the code this seam replaced
 * read `locale.language` (empty for the root locale) and sent no language filter.
 */
internal fun Locale.deviceLanguageTag(): String = if (language.isEmpty()) "" else toLanguageTag()
