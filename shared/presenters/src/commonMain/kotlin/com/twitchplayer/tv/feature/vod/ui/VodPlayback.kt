package com.twitchplayer.tv.feature.vod.ui

import com.twitchplayer.tv.core.platform.NumberFormatter

/** One tap of Left / Right while seeking a replay. */
const val SEEK_STEP_MS = 10_000L

/** One jump (the skip buttons) in a replay. */
const val JUMP_MS = 60_000L

private const val LONG_HOLD_REPEATS = 40
private const val MEDIUM_HOLD_REPEATS = 24
private const val REPEATS_PER_STEP = 4
private const val LONG_HOLD_STEP_MS = 300_000L
private const val MEDIUM_HOLD_STEP_MS = 120_000L
private const val SPEED_EPSILON = 0.001f

/**
 * Size of one seek for a held Left / Right key, by auto-repeat count: 10 s for the first presses, then 20, 30 ... up to 60 s,
 * and for a long hold 2 then 5 minutes (a replay lasts hours; 60 s per repeat would take minutes to cross it).
 */
fun vodSeekStep(repeatCount: Int): Long = when {
    repeatCount >= LONG_HOLD_REPEATS -> LONG_HOLD_STEP_MS
    repeatCount >= MEDIUM_HOLD_REPEATS -> MEDIUM_HOLD_STEP_MS
    else -> SEEK_STEP_MS * (1 + repeatCount / REPEATS_PER_STEP)
}

val PLAYBACK_SPEEDS: List<Float> = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

/** The speed after [current] in [PLAYBACK_SPEEDS], wrapping to the slowest. */
fun nextSpeed(current: Float): Float = PLAYBACK_SPEEDS.firstOrNull { it > current + SPEED_EPSILON } ?: PLAYBACK_SPEEDS.first()

/** "1×", "1.25×": speed as the control shows it, with the device locale's decimal separator. */
fun speedLabel(speed: Float, formatter: NumberFormatter): String = formatter.decimal(speed, maxFractionDigits = 2) + "×"

/** Where a seek of [deltaMs] from [from] lands inside a replay of [durationMs] (unknown length: no upper bound beyond 0). */
fun seekTarget(from: Long, deltaMs: Long, durationMs: Long): Long = (from + deltaMs).coerceIn(0L, if (durationMs > 0) durationMs else Long.MAX_VALUE)
