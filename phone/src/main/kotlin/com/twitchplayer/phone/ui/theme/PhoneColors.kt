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

    /** The control layer over the picture: strong at the top and bottom bars (white text on any frame stays above 4.5:1), light in between. */
    val ControlsEdge = Color.Black.copy(alpha = 0.8f)
    val ControlsDim = Color.Black.copy(alpha = 0.3f)

    /** Shadow under chat text drawn straight over the video. */
    val Shadow = Color.Black
}
