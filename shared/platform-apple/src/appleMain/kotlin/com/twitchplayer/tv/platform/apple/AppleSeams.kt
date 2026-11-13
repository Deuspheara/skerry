package com.twitchplayer.tv.platform.apple

import com.twitchplayer.tv.core.platform.DeviceProfile
import com.twitchplayer.tv.core.platform.Logger
import com.twitchplayer.tv.core.platform.NumberFormatter
import com.twitchplayer.tv.feature.search.presentation.VoiceInput
import platform.Foundation.NSLocale
import platform.Foundation.NSLog
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterDecimalStyle
import platform.Foundation.NSString
import platform.Foundation.create
import platform.Foundation.currentLocale
import platform.Foundation.preferredLanguages

/** Warnings go to the unified log (`NSLog`), visible in Console.app and in Xcode's debug area. */
class AppleLogger : Logger {
    override fun warn(tag: String, message: String) {
        // An Objective-C string for the `%@` (a Kotlin string passed to a C variadic is not one, and crashed in optimised builds: found measuring a release-optimised build).
        NSLog("%@", NSString.create(string = "[$tag] $message"))
    }
}

/** The first preferred language of the system ("fr-FR", "zh-Hans-CN"), or "en" when none is set. */
class AppleDeviceProfile : DeviceProfile {
    override val languageTag: String
        get() = (NSLocale.preferredLanguages.firstOrNull() as? String) ?: "en"
}

/** Locale-aware decimal text through `NSNumberFormatter`, built at each call so a language switch applies at once. */
class AppleNumberFormatter : NumberFormatter {
    override fun decimal(value: Float, maxFractionDigits: Int): String {
        val formatter = NSNumberFormatter().apply {
            locale = NSLocale.currentLocale
            numberStyle = NSNumberFormatterDecimalStyle
            usesGroupingSeparator = false
            minimumFractionDigits = 0u
            maximumFractionDigits = maxFractionDigits.toULong()
        }
        return formatter.stringFromNumber(NSNumber(float = value)) ?: value.toString()
    }
}

/**
 * Speech search is not built on Apple yet (a `SFSpeechRecognizer` bridge is a later step of the Apple plan): the search page
 * simply hides its microphone.
 */
class UnavailableVoiceInput : VoiceInput {
    override fun isAvailable(): Boolean = false

    override suspend fun listen(): String? = null
}
