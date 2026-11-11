package com.twitchplayer.phone.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Brand colours shared with the TV design system (docs/DESIGN_SYSTEM.md); LIVE red is reserved for the live state. */
@Immutable
object PhoneColors {
    val Live = Color(0xFFEB0400)
    val OnLive = Color.White
    val Scrim = Color.Black.copy(alpha = 0.72f)
    val OnScrim = Color.White
}
