package com.twitchplayer.phone.feature.browse

import java.util.Locale

/**
 * The language filter's label: the language's own name in the viewer's display language ("English", "français"), from a Twitch
 * language code ("en", "fr", "zh-hk"). A code that Java does not know is shown as it is, upper-cased, rather than as an empty label.
 */
fun languageLabel(twitchCode: String, displayIn: Locale = Locale.getDefault()): String {
    val locale = Locale.forLanguageTag(twitchCode)
    val name = locale.getDisplayLanguage(displayIn)
    return if (name.isBlank() || name.equals(locale.language, ignoreCase = true)) twitchCode.uppercase() else name.replaceFirstChar { it.titlecase(displayIn) }
}
