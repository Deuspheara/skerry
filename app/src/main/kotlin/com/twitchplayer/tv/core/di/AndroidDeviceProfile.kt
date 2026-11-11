package com.twitchplayer.tv.core.di

import com.twitchplayer.tv.core.platform.DeviceProfile
import java.util.Locale

/** The interface language of the device (and of the in-app language picker, which sets the default locale). */
class AndroidDeviceProfile : DeviceProfile {
    override val languageTag: String get() = Locale.getDefault().toLanguageTag()
}
