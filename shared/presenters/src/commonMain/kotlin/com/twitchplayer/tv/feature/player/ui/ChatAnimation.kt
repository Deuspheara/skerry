package com.twitchplayer.tv.feature.player.ui

import com.twitchplayer.tv.feature.player.presentation.ChatLine
import com.twitchplayer.tv.feature.player.presentation.ChatPiece

/** Animated emote layers in one line. */
fun ChatLine.animatedEmoteCount(): Int = pieces.sumOf { piece -> if (piece is ChatPiece.Emote) piece.layers.count { it.animated } else 0 }

/**
 * Which visible lines may animate their emotes: the budget goes to the newest lines first (what people are reading),
 * each taking as many of its animated emotes as are left. Everything else shows its still image.
 * [visibleNewestFirst] must be only the lines on screen: lines scrolled out of view never hold budget.
 */
fun animationAllowance(visibleNewestFirst: List<ChatLine>, limit: Int): Map<String, Int> {
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
