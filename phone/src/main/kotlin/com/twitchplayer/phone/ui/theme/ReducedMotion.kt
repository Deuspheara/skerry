package com.twitchplayer.phone.ui.theme

import android.content.Context
import android.provider.Settings

/**
 * True when the system animator scale is 0 ("Remove animations" in the accessibility settings). Read when the screen is composed
 * and again after every recreation; the in-app Animations switch is the other input (see [PhoneTheme]).
 */
fun systemAnimationsOff(context: Context): Boolean = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
