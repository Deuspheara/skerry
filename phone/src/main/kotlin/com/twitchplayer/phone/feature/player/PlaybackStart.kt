package com.twitchplayer.phone.feature.player

import com.twitchplayer.tv.feature.vod.data.VodProgress

/** How a replay starts: at a known position, or after asking the viewer whether to resume. */
sealed interface StartDecision {
    data class Start(val atMs: Long) : StartDecision
    data class Ask(val saved: VodProgress) : StartDecision
}

/**
 * A retry continues where playback failed; otherwise a saved position worth resuming is offered (not the first seconds, not the
 * credits: [VodProgress.resumable]); otherwise the replay starts at the beginning.
 */
fun decideStart(saved: VodProgress?, continueAtMs: Long?): StartDecision = when {
    continueAtMs != null -> StartDecision.Start(continueAtMs)
    saved?.resumable == true -> StartDecision.Ask(saved)
    else -> StartDecision.Start(0L)
}

/** What to do with the saved position of a replay at a given moment. */
enum class ProgressAction { Save, Clear, Skip }

/** A finished replay is forgotten, one never started is left alone, anything else is saved. */
fun progressAction(started: Boolean, ended: Boolean): ProgressAction = when {
    !started -> ProgressAction.Skip
    ended -> ProgressAction.Clear
    else -> ProgressAction.Save
}

/** Seek bar arithmetic: slider fraction (0..1) and positions in milliseconds. */
object SeekModel {
    fun fractionOf(positionMs: Long, durationMs: Long): Float = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    fun positionFor(fraction: Float, durationMs: Long): Long = (fraction.coerceIn(0f, 1f) * durationMs).toLong()
}
