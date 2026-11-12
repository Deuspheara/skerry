package com.twitchplayer.phone.navigation

import android.content.Context
import androidx.core.content.edit

/**
 * Whether a viewer was signed in the last time the session was known. Only used to draw the bar while the session is still being read,
 * so it does not change shape a moment after the app opens; the session itself decides everything once it is read.
 */
class SignedInHint(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var signedIn: Boolean
        get() = prefs.getBoolean(KEY, false)
        set(value) = prefs.edit { putBoolean(KEY, value) }

    private companion object {
        const val PREFS = "phone_session_hint"
        const val KEY = "signed_in"
    }
}
