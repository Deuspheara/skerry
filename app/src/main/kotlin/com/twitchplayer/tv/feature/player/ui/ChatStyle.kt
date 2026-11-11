package com.twitchplayer.tv.feature.player.ui

import androidx.compose.runtime.Immutable
import com.twitchplayer.tv.core.data.model.ChatSettings
import com.twitchplayer.tv.feature.player.presentation.ChatLine
import com.twitchplayer.tv.feature.player.presentation.ChatPiece
import kotlin.math.roundToInt

/** Chat settings turned into the measurements the renderer needs (TV sizes: text 14-17 sp, lines 18-22 sp, emotes 24-30 dp, badges 15-16 dp). */
@Immutable
data class ChatStyle(
    val textSp: Float,
    val lineHeightSp: Float,
    val emoteDp: Float,
    val badgeDp: Float,
    val gapDp: Float,
    val showBadges: Boolean,
    val showTimestamps: Boolean,
    val animatedEmotes: Boolean,
    val animatedLimit: Int,
    val backgroundAlpha: Float,
    /** Chat drawn straight over the video: a soft shadow keeps the text readable without a panel. */
    val textShadow: Boolean = false,
)

fun ChatSettings.toStyle() = ChatStyle(
    textSp = textSize.sp,
    lineHeightSp = (textSize.sp * 1.3f).roundToInt().toFloat(),
    emoteDp = (textSize.sp * 1.75f).roundToInt().toFloat(),
    badgeDp = (textSize.sp + 1f).coerceIn(15f, 16f),
    gapDp = density.gapDp.toFloat(),
    showBadges = showBadges,
    showTimestamps = showTimestamps,
    animatedEmotes = animatedEmotes,
    animatedLimit = animatedLimit.count,
    backgroundAlpha = backgroundOpacity / 100f,
)

internal fun ChatLine.animatedEmoteCount(): Int = pieces.sumOf { piece -> if (piece is ChatPiece.Emote) piece.layers.count { it.animated } else 0 }

/**
 * Which visible lines may animate their emotes: the budget goes to the newest lines first (what people are reading),
 * each taking as many of its animated emotes as are left. Everything else shows its still image.
 * [visibleNewestFirst] must be only the lines on screen: lines scrolled out of view never hold budget.
 */
internal fun animationAllowance(visibleNewestFirst: List<ChatLine>, limit: Int): Map<String, Int> {
    var left = limit
    val out = HashMap<String, Int>()
    for (line in visibleNewestFirst) {
        if (left <= 0) break
        val take = minOf(left, line.animatedEmoteCount())
        if (take > 0) {
            out[line.id] = take
            left -= take
        }
    }
    return out
}
