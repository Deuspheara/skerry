package com.twitchplayer.tv.core.data.repository

import com.twitchplayer.tv.core.data.model.LiveStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Tells when a followed channel goes live. Twitch's EventSub cannot do this for a viewer: `stream.online` costs one of the
 * connection's 10 points per channel, so a few followed channels would fill it. The follows list is read again every
 * [intervalMs] instead (one cheap call pair) and compared with the previous one.
 */
class LiveWatcher(
    private val repository: TwitchRepository,
    private val session: SessionRepository,
    private val intervalMs: Long = 90_000,
) {
    /** Each followed channel that was offline at the previous check and is live now. Nothing for the first check. */
    fun wentLive(): Flow<LiveStream> = flow {
        var known: Set<String>? = null
        while (true) {
            if (session.state.value is SessionState.SignedIn) {
                val live = try {
                    repository.follows(force = true).live
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
                if (live != null) {
                    newlyLive(known, live).forEach { emit(it) }
                    known = live.mapTo(HashSet()) { it.channelId }
                }
            } else known = null
            delay(intervalMs)
        }
    }
}

/** [live] channels that were not in [previous]; none when there was no previous check (everything would look new). */
internal fun newlyLive(previous: Set<String>?, live: List<LiveStream>): List<LiveStream> =
    if (previous == null) emptyList() else live.filter { it.channelId !in previous }
