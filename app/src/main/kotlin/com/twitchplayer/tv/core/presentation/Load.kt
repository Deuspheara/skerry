package com.twitchplayer.tv.core.presentation

import androidx.compose.runtime.Immutable
import com.twitchplayer.tv.core.data.repository.SessionRepository
import com.twitchplayer.tv.core.data.repository.SessionState
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

/** Screen data lifecycle shared by every network-backed screen. */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data object SignedOut : Load<Nothing>
    data object Failed : Load<Nothing>
    @Immutable
    data class Ready<T>(val data: T) : Load<T>
}

/** Where a [Loader] keeps its last good result per account (see [com.twitchplayer.tv.core.data.repository.SnapshotCache]). */
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
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Loader<T>(
    scope: CoroutineScope,
    session: SessionRepository,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val cache: LoadCache<T>? = null,
    private val block: suspend () -> T,
) {
    private val tick = MutableStateFlow(0)
    private var lastUser: String? = null
    private var lastReady: Load.Ready<T>? = null

    val state: StateFlow<Load<T>> = combine(session.state, tick) { s, _ -> s }
        .flatMapLatest { s ->
            flow {
                when (s) {
                    SessionState.Loading -> emit(Load.Loading)
                    SessionState.SignedOut -> { lastUser = null; lastReady = null; emit(Load.SignedOut) }
                    is SessionState.SignedIn -> {
                        val user = s.account.userId
                        if (user != lastUser) { lastUser = user; lastReady = null }
                        val shown = lastReady ?: cache?.read(user)?.let { Load.Ready(it) }
                        emit(shown ?: Load.Loading)
                        try {
                            val fresh = Load.Ready(block())
                            lastReady = fresh
                            emit(fresh)
                            cache?.write(user, fresh.data)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            if (shown == null) emit(Load.Failed)
                        }
                    }
                }
            }
        }
        .flowOn(dispatcher)
        .stateIn(scope, SharingStarted.Lazily, Load.Loading)

    fun reload() { tick.value++ }
}
