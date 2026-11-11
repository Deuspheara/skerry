package com.twitchplayer.tv.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

/** Semantic color tokens (docs/DESIGN_SYSTEM.md). LIVE red is reserved for the live state. */
@Immutable
object TvColors {
    val Purple = Color(0xFF9146FF)
    val PurplePressed = Color(0xFF772CE8)
    /** Primary fill while focused: a touch toward the focus tone, so the button itself changes, not only its ring. */
    val PurpleFocused = Color(0xFF9B58FF)
    /** Veil over a pressed secondary button or chip. */
    val PressTone = Color.Black.copy(alpha = 0.22f)
    val Focus = Color(0xFFBF94FF)
    val Live = Color(0xFFEB0400)

    val Body = Color(0xFF0E0E10)
    val Base = Color(0xFF18181B)
    val Alt = Color(0xFF1F1F23)
    val Alt2 = Color(0xFF26262C)
    val Divider = Color(0xFF303032)
    val Tile = Color(0xFF202024)
    val TileFocused = Color(0xFF2A2A30)
    val KeyFill = Color(0xFF1F1F23)
    val KeyLine = Color(0xFF2E2E35)
    val ChipLine = Color(0xFF3A3A40)
    val Hair = Color.White.copy(alpha = 0.12f)

    val Text = Color(0xFFEFEFF1)
    /** Secondary text, >= #A8A8B3 on every dark surface (WCAG AA). */
    val TextSecondary = Color(0xFFC8C8D0)
    val IconHigh = Color(0xFFE5E5EA)
    val Grey = Color(0xFF6F6F7A)

    val Scrim = Body.copy(alpha = 0.86f)
    val Black60 = Color.Black.copy(alpha = 0.60f)
    val Black70 = Color.Black.copy(alpha = 0.70f)
    val Black80 = Color.Black.copy(alpha = 0.80f)
    val Black85 = Color.Black.copy(alpha = 0.85f)

    /** Chat: highlighted message (channel points) and a mention of the viewer; both also get a left rule, never colour alone. */
    val ChatHighlight = Purple.copy(alpha = 0.16f)
    val ChatMention = Purple.copy(alpha = 0.30f)
    val ChatBits = Color(0xFFD9C7FF)

    /** Purple tint used for nicknames and the mic outline. */
    val Tint = Purple.copy(alpha = 0.45f).compositeOver(Text)
    val FocusedKey = Purple.copy(alpha = 0.20f).compositeOver(KeyFill)
}
