package com.twitchplayer.tv.core.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * One 24 dp line-icon family (stroke 2, round caps), authored from the validated prototype.
 * Vectors are built lazily once; tint through [androidx.tv.material3.Icon].
 */
object TvIcons {
    /** Directional glyphs: flipped in right-to-left languages (Arabic, Hebrew). */
    private val MIRRORED = setOf("ChevronLeft", "ChevronRight", "LogOut", "Backspace")

    private fun icon(name: String, filled: Boolean = false, vararg paths: String): ImageVector =
        ImageVector.Builder(
            name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
            autoMirror = name in MIRRORED,
        ).apply {
            paths.forEach { d ->
                addPath(
                    pathData = PathParser().parsePathString(d).toNodes(),
                    fill = if (filled) SolidColor(Color.Black) else null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    val Home: ImageVector by lazy(LazyThreadSafetyMode.NONE) { icon("Home", false, "M3 11l9-8 9 8v10a1 1 0 0 1-1 1h-5v-7H9v7H4a1 1 0 0 1-1-1z") }
    val Heart: ImageVector by lazy(LazyThreadSafetyMode.NONE) { icon("Heart", false, "M20.8 5.6a5.2 5.2 0 0 0-7.4 0L12 7l-1.4-1.4a5.2 5.2 0 0 0-7.4 7.4L12 21.8l8.8-8.8a5.2 5.2 0 0 0 0-7.4z") }
    val Grid: ImageVector by lazy(LazyThreadSafetyMode.NONE) { icon("Grid", false, "M3 3h7v7H3zM14 3h7v7h-7zM3 14h7v7H3zM14 14h7v7h-7z") }
    val Search: ImageVector by lazy(LazyThreadSafetyMode.NONE) { icon("Search", false, "M11 4a7 7 0 1 0 0 14 7 7 0 0 0 0-14zM21 21l-4.3-4.3") }
    val SearchOff: ImageVector by lazy(LazyThreadSafetyMode.NONE) { icon("SearchOff", false, "M11 4a7 7 0 1 0 0 14 7 7 0 0 0 0-14zM21 21l-4.3-4.3M3 3l18 18") }
    val User: ImageVector by lazy(LazyThreadSafetyMode.NONE) { icon("User", false, "M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM4 21a8 8 0 0 1 16 0") }
    val Users: ImageVector by lazy(LazyThreadSafetyMode.NONE) { icon("Users", false, "M9 11a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM3 20a6 6 0 0 1 12 0M17 11a3 3 0 1 0 0-6M21 20a6 6 0 0 0-4-5.6") }
    val Flame: ImageVector by lazy(LazyThreadSafetyMode.NONE) { icon("Flame", false, "M12 3c1 3.5 5 5 5 9.5a5 5 0 0 1-10 0c0-2 1-3.2 2-4.3.3 1.5 1 2.3 2 2.8C11 8.5 11.5 5.5 12 3z") }
    val Film: ImageVector by lazy(LazyThreadSafetyMode.NONE) { icon("Film", false, "M3 4h18v16H3zM7 4v16M17 4v16M3 9h4M3 15h4M17 9h4M17 15h4") }
    val Sparkles: ImageVector by lazy(LazyThreadSafetyMode.NONE) { icon("Sparkles", false, "M11 3l1.8 4.7L17.5 9.5l-4.7 1.8L11 16l-1.8-4.7L4.5 9.5l4.7-1.8zM19 15l.8 2.2L22 18l-2.2.8L19 21l-.8-2.2L16 18l2.2-.8z") }
    val Clock: ImageVector by lazy(LazyThreadSafetyMode.NONE) { icon("Clock", false, "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM12 7v5l3 2") }
    /** "Ad" in a frame: the stream is showing an advertisement. */
    val Ad: ImageVector by lazy(LazyThreadSafetyMode.NONE) { icon("Ad", false, "M3 5h18v14H3z", "M6.5 15l2-6 2 6M7.3 13h2.4", "M14 9v6h1.5a2 2 0 0 0 2-2v-2a2 2 0 0 0-2-2z") }
    val Smile: ImageVector by lazy(LazyThreadSafetyMode.NONE) { icon("Smile", false, "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18z", "M8.5 14a4 4 0 0 0 7 0", "M9 9.5v.5M15 9.5v.5") }
    /** A circled 1: every message must be unique. */
    val Unique: ImageVector by lazy(LazyThreadSafetyMode.NONE) { icon("Unique", false, "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18z", "M10.5 9.5L12.5 8v8") }
    val Shift: ImageVector by lazy(LazyThreadSafetyMode.NONE) { icon("Shift", false, "M12 4l8 9h-5v6H9v-6H4z") }
    val Replay: ImageVector by lazy(LazyThreadSafetyMode.NONE) { icon("Replay", false, "M4.5 12a7.5 7.5 0 1 0 2.3-5.4", "M4.5 4v4.5H9") }
    val Send: ImageVector by lazy(LazyThreadSafetyMode.NONE) { icon("Send", false, "M3 11l18-8-8 18-2-8z") }
    val Edit: ImageVector by lazy(LazyThreadSafetyMode.NONE) { icon("Edit", false, "M4 20h4L19 9l-4-4L4 16z", "M14 6l4 4") }
    val Mic: ImageVector by lazy(LazyThreadSafetyMode.NONE) { icon("Mic", false, "M12 3a3 3 0 0 0-3 3v6a3 3 0 0 0 6 0V6a3 3 0 0 0-3-3zM5 11a7 7 0 0 0 14 0M12 18v3") }
    val MicOff: ImageVector by lazy(LazyThreadSafetyMode.NONE) { icon("MicOff", false, "M12 3a3 3 0 0 0-3 3v6a3 3 0 0 0 6 0V6a3 3 0 0 0-3-3zM5 11a7 7 0 0 0 14 0M12 18v3M3 3l18 18") }
    val Backspace: ImageVector by lazy(LazyThreadSafetyMode.NONE) { icon("Backspace", false, "M21 5H9l-6 7 6 7h12a1 1 0 0 0 1-1V6a1 1 0 0 0-1-1zM17 9l-6 6M11 9l6 6") }
    val ChevronLeft: ImageVector by lazy(LazyThreadSafetyMode.NONE) { icon("ChevronLeft", false, "M15 5l-7 7 7 7") }
    val ChevronRight: ImageVector by lazy(LazyThreadSafetyMode.NONE) { icon("ChevronRight", false, "M9 5l7 7-7 7") }
    val ArrowUp: ImageVector by lazy(LazyThreadSafetyMode.NONE) { icon("ArrowUp", false, "M12 19V5M6 11l6-6 6 6") }
    val ArrowDown: ImageVector by lazy(LazyThreadSafetyMode.NONE) { icon("ArrowDown", false, "M12 5v14M6 13l6 6 6-6") }
    val Check: ImageVector by lazy(LazyThreadSafetyMode.NONE) { icon("Check", false, "M5 12l5 5 9-10") }
    val Minus: ImageVector by lazy(LazyThreadSafetyMode.NONE) { icon("Minus", false, "M6 12h12") }
    val Close: ImageVector by lazy(LazyThreadSafetyMode.NONE) { icon("Close", false, "M6 6l12 12M18 6L6 18") }
    val Space: ImageVector by lazy(LazyThreadSafetyMode.NONE) { icon("Space", false, "M4 10v5h16v-5") }
    val Eye: ImageVector by lazy(LazyThreadSafetyMode.NONE) { icon("Eye", false, "M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12zM12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6z") }
    val Play: ImageVector by lazy(LazyThreadSafetyMode.NONE) { icon("Play", false, "M7 4l13 8-13 8z") }
    val Pause: ImageVector by lazy(LazyThreadSafetyMode.NONE) { icon("Pause", false, "M8 5v14M16 5v14") }
    val Volume: ImageVector by lazy(LazyThreadSafetyMode.NONE) { icon("Volume", false, "M4 9v6h4l5 4V5L8 9zM16 9a4 4 0 0 1 0 6M19 6a8 8 0 0 1 0 12") }
    val Mute: ImageVector by lazy(LazyThreadSafetyMode.NONE) { icon("Mute", false, "M4 9v6h4l5 4V5L8 9zM17 9l5 6M22 9l-5 6") }
    val Maximize: ImageVector by lazy(LazyThreadSafetyMode.NONE) { icon("Maximize", false, "M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5") }
    val Minimize: ImageVector by lazy(LazyThreadSafetyMode.NONE) { icon("Minimize", false, "M9 4v5H4M15 4v5h5M9 20v-5H4M15 20v-5h5") }
    val Bell: ImageVector by lazy(LazyThreadSafetyMode.NONE) { icon("Bell", false, "M6 9a6 6 0 0 1 12 0c0 6 2 7 2 7H4s2-1 2-7zM10 20a2 2 0 0 0 4 0") }
    val Power: ImageVector by lazy(LazyThreadSafetyMode.NONE) { icon("Power", false, "M12 3v9M6.3 6.3a8 8 0 1 0 11.4 0") }
    val Globe: ImageVector by lazy(LazyThreadSafetyMode.NONE) { icon("Globe", false, "M12 2a10 10 0 1 0 0 20a10 10 0 1 0 0-20zM2 12h20M12 2a15.3 15.3 0 0 1 4 10a15.3 15.3 0 0 1-4 10a15.3 15.3 0 0 1-4-10a15.3 15.3 0 0 1 4-10z") }
    val LogOut: ImageVector by lazy(LazyThreadSafetyMode.NONE) { icon("LogOut", false, "M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9") }
    val Star: ImageVector by lazy(LazyThreadSafetyMode.NONE) { icon("Star", false, "M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1L3.2 9.5l6.1-.9z") }
    val Shield: ImageVector by lazy(LazyThreadSafetyMode.NONE) { icon("Shield", false, "M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6z") }
    val Gem: ImageVector by lazy(LazyThreadSafetyMode.NONE) { icon("Gem", false, "M6 4h12l4 6-10 11L2 10z") }
    val SortPopular: ImageVector by lazy(LazyThreadSafetyMode.NONE) { icon("SortPopular", false, "M4 6h16M4 12h11M4 18h6") }
    val SortAlpha: ImageVector by lazy(LazyThreadSafetyMode.NONE) { icon("SortAlpha", false, "M4 18l4-12 4 12M5.5 14h5M15 6h5l-5 12h5") }
    val Equalizer: ImageVector by lazy(LazyThreadSafetyMode.NONE) { icon("Equalizer", false, "M6 20V10M12 20V4M18 20v-7") }
    val OffAir: ImageVector by lazy(LazyThreadSafetyMode.NONE) { icon("OffAir", false, "M3 7h18v12H3zM8 3l4 4 4-4M3 3l18 18") }
    val ChatOptions: ImageVector by lazy(LazyThreadSafetyMode.NONE) { icon("ChatOptions", false, "M4 7h9M17 7h3M4 17h3M11 17h9M15 5v4M9 15v4") }
    val TextSize: ImageVector by lazy(LazyThreadSafetyMode.NONE) { icon("TextSize", false, "M4 7V4h16v3M12 4v16M9 20h6") }
    val ChatOff: ImageVector by lazy(LazyThreadSafetyMode.NONE) { icon("ChatOff", false, "M4 5h16v11H9l-5 4z", "M3 3l18 18") }
    val ChatCompact: ImageVector by lazy(LazyThreadSafetyMode.NONE) { icon("ChatCompact", false, "M2 6h11v9H7l-4 3z", "M16 4h6v16h-6z") }
    val ChatOverlay: ImageVector by lazy(LazyThreadSafetyMode.NONE) { icon("ChatOverlay", false, "M2 4h20v16H2z", "M12 11h7M10 15h9") }
    val Quality: ImageVector by lazy(LazyThreadSafetyMode.NONE) { icon("Quality", false, "M3 5h18v14H3z", "M7 10v4M7 12h3M10 10v4M14 10h3v4h-3z") }
    val Pip: ImageVector by lazy(LazyThreadSafetyMode.NONE) { icon("Pip", false, "M3 5h18v14H3z", "M12 12h7v5h-7z") }
    val BellOff: ImageVector by lazy(LazyThreadSafetyMode.NONE) { icon("BellOff", false, "M6 9a6 6 0 0 1 12 0c0 6 2 7 2 7H4s2-1 2-7zM10 20a2 2 0 0 0 4 0", "M3 3l18 18") }
    val HeartFilled: ImageVector by lazy(LazyThreadSafetyMode.NONE) { icon("HeartFilled", true, "M20.8 5.6a5.2 5.2 0 0 0-7.4 0L12 7l-1.4-1.4a5.2 5.2 0 0 0-7.4 7.4L12 21.8l8.8-8.8a5.2 5.2 0 0 0 0-7.4z") }
    val BellFilled: ImageVector by lazy(LazyThreadSafetyMode.NONE) { icon("BellFilled", true, "M6 9a6 6 0 0 1 12 0c0 6 2 7 2 7H4s2-1 2-7zM10 20a2 2 0 0 0 4 0") }
}
