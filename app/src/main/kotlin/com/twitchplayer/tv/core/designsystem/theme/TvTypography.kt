package com.twitchplayer.tv.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.twitchplayer.tv.R

private val families = HashMap<Int, FontFamily>()

/** Inter with its optical-size axis tied to the text size (14..32 px), as a browser would. */
fun interFamily(weight: Int, sizeSp: Float): FontFamily {
    val opsz = (sizeSp * 2f).coerceIn(14f, 32f).toInt()
    return families.getOrPut(weight * 100 + opsz) {
        FontFamily(
            Font(
                resId = R.font.inter_variable,
                weight = FontWeight(weight),
                variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.opticalSizing(opsz.sp)),
            ),
        )
    }
}

private fun style(size: TextUnit, weight: FontWeight, lineHeight: Float = 1.3f, tabular: Boolean = false) = TextStyle(
    fontFamily = interFamily(weight.weight, size.value),
    fontSize = size,
    fontWeight = weight,
    lineHeight = size * lineHeight,
    fontFeatureSettings = if (tabular) "tnum" else null,
)

/** Type scale: prototype px / 2 → sp (the TV renders at 2.0 density). */
@Immutable
object TvType {
    val Display = style(32.sp, FontWeight.SemiBold, 1.1f)
    val Headline = style(28.sp, FontWeight.SemiBold, 1.15f)
    val ProfileName = style(22.sp, FontWeight.SemiBold, 1.2f)
    val EmptyTitle = style(20.sp, FontWeight.SemiBold, 1.2f)
    val Query = style(20.sp, FontWeight.Medium, 1.0f)
    val Tile = style(18.sp, FontWeight.SemiBold, 1.2f)
    val Section = style(17.sp, FontWeight.SemiBold, 1.2f)
    val Key = style(22.sp, FontWeight.Medium, 1.0f)
    val Title = style(14.sp, FontWeight.SemiBold, 1.3f)
    val Label = style(14.sp, FontWeight.Medium, 1.0f)
    val Meta = style(14.sp, FontWeight.Normal, 1.4f)
    val MetaNumeric = style(14.sp, FontWeight.Medium, 1.4f, tabular = true)
    val Clock = style(14.sp, FontWeight.Medium, 1.0f, tabular = true)
    val LiveBadge = style(12.sp, FontWeight.Bold, 1.0f).copy(letterSpacing = 0.66.sp)
    /** Chat text at a user-chosen size; the line height is a fixed multiple so wrapped lines stay compact (18-22 sp). */
    fun chat(size: TextUnit, lineHeight: TextUnit) = TextStyle(
        fontFamily = interFamily(FontWeight.Normal.weight, size.value),
        fontSize = size,
        fontWeight = FontWeight.Normal,
        lineHeight = lineHeight,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )
    val PlayerName = style(16.sp, FontWeight.SemiBold, 1.25f)
    /** Text of an overlay pill or toast over the video / page: legible at 3 m. */
    val Pill = style(16.sp, FontWeight.Medium, 1.0f)
    /** Secondary body text of a header (stream title, tags). */
    val Body = style(16.sp, FontWeight.Normal, 1.35f)
    val ControlLabel = style(14.sp, FontWeight.Medium, 1.6f)
}
