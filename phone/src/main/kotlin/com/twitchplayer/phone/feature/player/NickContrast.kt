package com.twitchplayer.phone.feature.player

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/** WCAG contrast ratio between two opaque colours (1 = identical, 21 = black on white). */
fun contrastRatio(a: Color, b: Color): Float {
    val high = maxOf(a.luminance(), b.luminance())
    val low = minOf(a.luminance(), b.luminance())
    return (high + LUMINANCE_OFFSET) / (low + LUMINANCE_OFFSET)
}

/**
 * A viewer's nickname colour is already adjusted to read on a dark chat (the shared mapping used by the TV). On a light surface that
 * can leave a pastel almost invisible, so it is moved towards black, as little as needed, until it reaches [minRatio] against
 * [background]; on a dark surface the colour is moved towards white the same way (normally it already passes and is returned as is).
 */
fun readableOn(background: Color, color: Color, minRatio: Float = MIN_TEXT_CONTRAST): Color {
    if (contrastRatio(color, background) >= minRatio) return color
    val target = if (background.luminance() > MID_LUMINANCE) Color.Black else Color.White
    for (step in 1..STEPS) {
        val candidate = lerp(color, target, step / STEPS.toFloat())
        if (contrastRatio(candidate, background) >= minRatio) return candidate
    }
    return target
}

private const val LUMINANCE_OFFSET = 0.05f
private const val MID_LUMINANCE = 0.5f
private const val MIN_TEXT_CONTRAST = 4.5f
private const val STEPS = 20
