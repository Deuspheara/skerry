package com.twitchplayer.tv.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.dp

/**
 * Layout tokens in dp. The validated prototype is 1920x1080 px, i.e. 960x540 dp at 320 dpi (px / 2).
 */
@Immutable
object TvDimens {
    // Overscan safe area
    val SafeHorizontal = 48.dp
    val SafeTop = 27.dp
    val SafeBottom = 27.dp

    // Spacing scale (base 4 dp)
    val Space4 = 4.dp
    val Space8 = 8.dp
    val Space12 = 12.dp
    val Space16 = 16.dp
    val Space24 = 24.dp
    val Space32 = 32.dp
    val Space48 = 48.dp

    // Layout
    val RailWidth = 48.dp
    val RailItem = 36.dp
    val RailIcon = 22.dp
    val RailExpandedWidth = 200.dp
    val RailShade = 960.dp
    /** Gap between the rail and the first content edge (prototype 144 px = rail 96 + 48). */
    val ContentStart = 24.dp
    val ContentEnd = 48.dp
    val TopBar = 48.dp
    val RowGap = 12.dp
    val FocusRing = 2.dp
    val FocusGap = 1.5.dp
    /** Extra ring width when animations are off: the ring is the only cue, so it gets bolder instead of the card growing. */
    val FocusRingReduced = 1.dp
    /** Default focus growth. Size-aware: the smaller the thing, the more it grows (a poster 120 dp wide reads at 1.08, a 200 dp card at 1.05). */
    const val FocusScale = 1.04f
    const val FocusScalePoster = 1.08f
    const val FocusScaleCard = 1.05f
    const val FocusScaleControl = 1.06f

    // Cards
    val LiveCardWidth = 200.dp
    val CategoryCardWidth = 120.dp
    val ZapCardWidth = 152.dp
    val ReplayCardWidth = 180.dp
    val AvatarCard = 32.dp

    // Player
    val ControlButton = 40.dp
    val ControlGap = 8.dp
    val ControlIcon = 22.dp
    val ChatCompact = 280.dp
    /** Chat drawn over the full-screen video, bottom right, fading out at the middle of the screen. */
    val ChatOverlay = 340.dp
    /**
     * The mini player keeps a 534x300 dp viewport (Twitch's embed minimum is 400x300) shown at [MiniScale]:
     * a 320x180 dp picture in the bottom-right corner, with a sharper auto quality than a truly tiny viewport.
     */
    val MiniWidth = 534.dp
    val MiniHeight = 300.dp
    const val MiniScale = 0.6f
    /** Chat text column as a share of the screen width (the panel adds [ChatInsetStart] and [ChatInsetEnd]). */
    const val ChatCompactFraction = 0.24f
    val ChatInsetStart = 8.dp
    /** Right edge of the chat column: half the safe margin, so the text is not pushed away from the screen edge. */
    val ChatInsetEnd = 24.dp
    /** The video always keeps at least this share of the width. */
    const val VideoMinFraction = 2f / 3f
}

@Immutable
object TvShapes {
    val Card = RoundedCornerShape(4.dp)
    val Control = RoundedCornerShape(2.dp)
    val Chip = RoundedCornerShape(2.dp)
    val Dialog = RoundedCornerShape(6.dp)
    val Rail = RoundedCornerShape(8.dp)
}
