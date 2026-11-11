package com.twitchplayer.tv.feature.vod.ui

import androidx.annotation.StringRes
import com.twitchplayer.tv.R
import com.twitchplayer.tv.feature.player.data.stream.VodAccessException
import com.twitchplayer.tv.feature.player.data.stream.VodFailure
import java.io.IOException

/** Why the replay screen shows an error; [retryable] decides whether it offers Retry. */
enum class VodError(@StringRes val title: Int, @StringRes val line: Int, val retryable: Boolean) {
    Gone(R.string.vod_gone, R.string.vod_gone_line, false),
    SubscribersOnly(R.string.vod_subs, R.string.vod_subs_line, false),
    Refused(R.string.vod_failed, R.string.vod_failed_line, true),
    Network(R.string.vod_failed, R.string.vod_failed_net_line, true),
    Playback(R.string.vod_failed, R.string.vod_failed_line, true),
}

internal fun Throwable.toVodError(): VodError = when {
    this is VodAccessException -> when (failure) {
        VodFailure.NOT_FOUND -> VodError.Gone
        VodFailure.SUBSCRIBER_ONLY -> VodError.SubscribersOnly
        VodFailure.TOKEN, VodFailure.UNAVAILABLE -> VodError.Refused
    }

    this is IOException -> VodError.Network

    else -> VodError.Refused
}

internal const val SEEK_STEP_MS = 10_000L
internal const val JUMP_MS = 60_000L

/**
 * Size of one seek for a held Left / Right key, by auto-repeat count: 10 s for the first presses, then 20, 30 ... up to 60 s,
 * and for a long hold 2 then 5 minutes (a replay lasts hours; 60 s per repeat would take minutes to cross it).
 */
internal fun vodSeekStep(repeatCount: Int): Long = when {
    repeatCount >= 40 -> 300_000L
    repeatCount >= 24 -> 120_000L
    else -> SEEK_STEP_MS * (1 + repeatCount / 4)
}

internal val PLAYBACK_SPEEDS = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

/** The speed after [current] in [PLAYBACK_SPEEDS], wrapping to the slowest. */
internal fun nextSpeed(current: Float): Float = PLAYBACK_SPEEDS.firstOrNull { it > current + 0.001f } ?: PLAYBACK_SPEEDS.first()

/** "1×", "1.25×": speed as the control shows it. */
internal fun speedLabel(speed: Float): String = java.text.DecimalFormat("0.##", java.text.DecimalFormatSymbols(java.util.Locale.getDefault())).format(speed) + "×"

/** Where a seek of [deltaMs] from [from] lands inside a replay of [durationMs] (unknown length: no upper bound beyond 0). */
internal fun seekTarget(from: Long, deltaMs: Long, durationMs: Long): Long = (from + deltaMs).coerceIn(0L, if (durationMs > 0) durationMs else Long.MAX_VALUE)
