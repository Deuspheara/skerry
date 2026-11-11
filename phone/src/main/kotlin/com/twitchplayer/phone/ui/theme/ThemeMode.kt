package com.twitchplayer.phone.ui.theme

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The colour scheme the viewer chose. A fresh install is [Dark]: the brand surfaces are dark first. */
enum class ThemeMode {
    Dark,
    Light,
    System,
    ;

    /** [systemDark] is only read for [System]. */
    fun isDark(systemDark: Boolean): Boolean = when (this) {
        Dark -> true
        Light -> false
        System -> systemDark
    }

    companion object {
        val Default = Dark

        /** An unknown or missing stored value falls back to the default (an older or damaged preference never breaks start-up). */
        fun fromStored(value: String?): ThemeMode = entries.firstOrNull { it.name == value } ?: Default
    }
}

/** Where the theme choice lives. A phone-only setting: it is not part of the shared `AppSettings`. */
interface ThemePreference {
    val mode: StateFlow<ThemeMode>
    fun set(mode: ThemeMode)
}

/** SharedPreferences keeps it synchronous, so the first frame already has the right scheme (no dark flash for a light choice). */
class SharedPreferencesThemePreference(context: Context) : ThemePreference {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(ThemeMode.fromStored(prefs.getString(KEY, null)))
    override val mode: StateFlow<ThemeMode> = state.asStateFlow()

    override fun set(mode: ThemeMode) {
        prefs.edit { putString(KEY, mode.name) }
        state.value = mode
    }

    companion object {
        const val PREFS = "phone_theme"
        const val KEY = "mode"
    }
}
