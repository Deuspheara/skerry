package com.twitchplayer.tv.core.presentation

import com.twitchplayer.tv.core.data.repository.SessionRepository
import com.twitchplayer.tv.core.data.repository.SessionState
import com.twitchplayer.tv.core.platform.ForegroundSignal
import com.twitchplayer.tv.core.platform.WallClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Screen data lifecycle shared by every network-backed screen. */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data object SignedOut : Load<Nothing>
    data object Failed : Load<Nothing>

    /**
     * [loadedAtMs] is when [data] was fetched (epoch ms; 0 when unknown, as for a copy read from the disk). [refreshFailed]: the last attempt to
     * refresh failed and [data] is what was already shown, so the screen can say it is not up to date.
     */
    data class Ready<T>(val data: T, val loadedAtMs: Long = 0L, val refreshFailed: Boolean = false) : Load<T> {
        /** The same freshness information on other [data] (a screen that derives its own state from the loaded one). */
        fun <R> withData(other: R): Ready<R> = Ready(other, loadedAtMs, refreshFailed)

        /** Age of [data] at [nowMs]; null when unknown. */
        fun ageMs(nowMs: Long): Long? = if (loadedAtMs > 0L) (nowMs - loadedAtMs).coerceAtLeast(0L) else null
    }
}

/** Where a [Loader] keeps its last good result per account (see `SnapshotCache`). */
class LoadCache<T>(
    val read: suspend (userId: String) -> T?,
    val write: suspend (userId: String, data: T) -> Unit,
)

/**
 * Loads [block] whenever the session is signed in, exposes [Load] and re-runs on [reload].
 * Cancelled loads never leak into state; failures become [Load.Failed].
 * [block] runs on [dispatcher], not the main thread: building requests, decoding and mapping stay off the UI thread
 * (at startup the first requests would otherwise run before the first frame).
 *
 * Stale-while-revalidate: while a load runs, the screen keeps what it already shows for the same account, or else the
 * [cache]d copy from the last session, instead of a spinner; the fresh result replaces it (screens animate the change).
 * A failed load keeps the shown data and only becomes [Load.Failed] when there was nothing to show.
 *
 * With a [foreground] signal the data also refreshes when the app comes back and the data is at least [refreshAfterMs] old
 * (measured on [clock]); a load that is already running is left alone.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Loader<T>(
    scope: CoroutineScope,
    session: SessionRepository,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val cache: LoadCache<T>? = null,
    foreground: ForegroundSignal? = null,
    private val clock: WallClock = WallClock.System,
    private val refreshAfterMs: Long = FOREGROUND_REFRESH_MS,
    private val block: suspend () -> T,
) {
    private val tick = MutableStateFlow(0)

    /** When the last fresh result arrived (epoch ms, 0 = never) and whether a load is running: what the foreground refresh decides on. */
    private val loadedAtMs = MutableStateFlow(0L)
    private val loading = MutableStateFlow(false)
    private var lastUser: String? = null
    private var lastReady: Load.Ready<T>? = null

    val state: StateFlow<Load<T>> = combine(session.state, tick) { s, _ -> s }
        .flatMapLatest { s ->
            flow {
                when (s) {
                    SessionState.Loading -> emit(Load.Loading)

                    SessionState.SignedOut -> {
                        lastUser = null
                        lastReady = null
                        emit(Load.SignedOut)
                    }

                    is SessionState.SignedIn -> {
                        val user = s.account.userId
                        if (user != lastUser) {
                            lastUser = user
                            lastReady = null
                        }
                        val shown = lastReady ?: cache?.read(user)?.let { Load.Ready(it) }
                        emit(shown ?: Load.Loading)
                        loading.value = true
                        try {
                            // The stored copy is already on screen; the request waits for the tokens (see SessionRepository.awaitReady).
                            session.awaitReady()
                            val loadedAt = clock.nowMillis()
                            val fresh = Load.Ready(block(), loadedAtMs = loadedAt)
                            lastReady = fresh
                            loadedAtMs.value = loadedAt
                            emit(fresh)
                            cache?.write(user, fresh.data)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            // What was on screen stays, marked as not refreshed; only a screen with nothing to show becomes an error.
                            emit(shown?.copy(refreshFailed = true) ?: Load.Failed)
                        } finally {
                            loading.value = false
                        }
                    }
                }
            }
        }
        .flowOn(dispatcher)
        .stateIn(scope, SharingStarted.Lazily, Load.Loading)

    init {
        // Back in the foreground: refresh when the data is older than [refreshAfterMs] (or was never loaded), unless a load is already running
        // (an overlapping one would only cancel and restart it). The shown data stays until the new copy replaces it, so the scroll never resets.
        if (foreground != null) {
            scope.launch {
                foreground.events.collect {
                    val age = clock.nowMillis() - loadedAtMs.value
                    if (!loading.value && age >= refreshAfterMs) reload()
                }
            }
        }
    }

    fun reload() {
        tick.value++
    }

    companion object {
        /** Live lists (streams, follows) are refreshed on return to the app when they are at least this old. */
        const val FOREGROUND_REFRESH_MS = 60_000L
    }
}
