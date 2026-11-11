package com.twitchplayer.phone.feature.settings

import android.app.Activity
import com.twitchplayer.tv.core.ui.AppLocales

/** The in-app language, behind an interface so the choice logic is tested without an Activity. */
interface LanguageSwitcher {
    /** The chosen language tag, or null when the app follows the system. */
    fun current(): String?

    /** Applies [tag] (null = system); the platform recreates the screen. */
    fun set(tag: String?)
}

/** [AppLocales] for an Activity: Android 13's per-app language, or the stored tag applied by `MainActivity.attachBaseContext` before. */
class AppLocalesSwitcher(private val activity: Activity) : LanguageSwitcher {
    override fun current(): String? = AppLocales.current(activity)

    override fun set(tag: String?) = AppLocales.set(activity, tag)
}

/** The system choice (null) first, then every translated language in the app's order. */
fun languageTags(supported: List<String>): List<String?> = listOf<String?>(null) + supported

/** Applies the choice; picking the language already in use does nothing (it would only recreate the screen). */
fun chooseLanguage(switcher: LanguageSwitcher, tag: String?): Boolean {
    if (tag == switcher.current()) return false
    switcher.set(tag)
    return true
}
