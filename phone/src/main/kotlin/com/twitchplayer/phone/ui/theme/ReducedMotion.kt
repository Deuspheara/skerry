package com.twitchplayer.phone.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * True when the system animator scale is 0 ("Remove animations" in the accessibility settings). Read when the screen is composed
 * and again after every recreation; the in-app Animations switch is the other input (see [PhoneTheme]).
 */
fun systemAnimationsOff(context: Context): Boolean = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/**
 * True when motion must be skipped: the viewer turned animations off in the app, or the system animator scale is 0 (see
 * [PhoneTheme], which provides it). The one composition local of the app, named in the detekt allowlist: skeletons and transitions at any
 * depth read it, and a parameter on every composable in between would be worse.
 */
val LocalReducedMotion = staticCompositionLocalOf { false }
