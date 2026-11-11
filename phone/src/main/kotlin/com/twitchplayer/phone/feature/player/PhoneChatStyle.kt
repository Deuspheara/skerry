package com.twitchplayer.phone.feature.player

import androidx.compose.runtime.Immutable
import com.twitchplayer.tv.core.data.model.ChatSettings
import kotlin.math.roundToInt

/** Chat settings turned into the measurements the phone renderer needs. */
@Immutable
data class PhoneChatStyle(
    val textSp: Float,
    val lineHeightSp: Float,
    val emoteDp: Float,
    val badgeDp: Float,
    val gapDp: Float,
    val showBadges: Boolean,
    val showTimestamps: Boolean,
    val animatedEmotes: Boolean,
    val animatedLimit: Int,
    /** 0..1 opacity of the panel behind chat drawn over the video. */
    val backgroundAlpha: Float,
)

fun ChatSettings.toPhoneStyle() = PhoneChatStyle(
    textSp = textSize.sp,
    lineHeightSp = (textSize.sp * LINE_HEIGHT_FACTOR).roundToInt().toFloat(),
    emoteDp = (textSize.sp * EMOTE_FACTOR).roundToInt().toFloat(),
    badgeDp = (textSize.sp + 1f).coerceIn(MIN_BADGE_DP, MAX_BADGE_DP),
    gapDp = density.gapDp.toFloat(),
    showBadges = showBadges,
    showTimestamps = showTimestamps,
    animatedEmotes = animatedEmotes,
    animatedLimit = animatedLimit.count,
    backgroundAlpha = backgroundOpacity / PERCENT,
)

private const val LINE_HEIGHT_FACTOR = 1.3f
private const val EMOTE_FACTOR = 1.75f
private const val MIN_BADGE_DP = 15f
private const val MAX_BADGE_DP = 16f
private const val PERCENT = 100f
