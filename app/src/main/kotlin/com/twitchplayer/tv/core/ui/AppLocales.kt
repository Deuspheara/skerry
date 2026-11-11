package com.twitchplayer.tv.core.ui

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * In-app language choice. Android TV settings rarely expose the per-app language screen, so the app offers its own.
 * Android 13+: the platform [LocaleManager] (persisted by the system, the activity is recreated for us).
 * Older versions: the tag is kept in shared preferences and applied in [wrap] (MainActivity.attachBaseContext).
 */
object AppLocales {
    /** Translated languages (BCP 47), English first; must match the `values-*` folders and `res/resources.properties`. */
    val supported: List<String> = listOf(
        "en", "ar", "bg", "ca", "cs", "da", "de", "el", "es", "fi", "fil", "fr", "he", "hi", "hr", "hu", "id", "it",
        "ja", "ko", "ms", "nb", "nl", "pl", "pt-BR", "pt-PT", "ro", "ru", "sk", "sv", "th", "tr", "uk", "vi",
        "zh-CN", "zh-TW",
    )

    private const val PREFS = "app_locale"
    private const val KEY = "tag"

    /** The chosen language tag, or null when the app follows the system language. */
    fun current(context: Context): String? =
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales.takeIf { !it.isEmpty }?.get(0)?.toLanguageTag()
                ?.let(::closestSupported)
        } else {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        }

    /** Switches the app language ([tag] null = system). Recreates [activity] so every screen picks it up. */
    fun set(activity: Activity, tag: String?) {
        if (Build.VERSION.SDK_INT >= 33) {
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        } else {
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply { if (tag == null) remove(KEY) else putString(KEY, tag) }.apply()
            activity.recreate()
        }
    }

    /** Pre-Android 13: a context whose resources use the chosen language (the base context as-is otherwise). */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return base
        val tag = base.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocales(LocaleList(locale))
        return base.createConfigurationContext(config)
    }

    /** A language's own name, as its speakers write it ("Deutsch", "日本語", "Português (Brasil)"). */
    fun nativeName(tag: String): String = when (tag) {
        "zh-CN" -> "中文（简体）"
        "zh-TW" -> "中文（繁體）"
        "pt-BR" -> "Português (Brasil)"
        "pt-PT" -> "Português (Portugal)"
        else -> Locale.forLanguageTag(tag).let { it.getDisplayLanguage(it) }.replaceFirstChar { it.titlecase(Locale.forLanguageTag(tag)) }
    }

    /** Maps a system tag like "de-AT" or "zh-Hant-TW" to the closest translated language. */
    private fun closestSupported(tag: String): String {
        if (tag in supported) return tag
        val locale = Locale.forLanguageTag(tag)
        if (locale.language == "zh") return if (locale.script == "Hant" || locale.country in setOf("TW", "HK", "MO")) "zh-TW" else "zh-CN"
        if (locale.language == "pt") return if (locale.country == "PT") "pt-PT" else "pt-BR"
        return supported.firstOrNull { Locale.forLanguageTag(it).language == locale.language } ?: tag
    }
}
