package com.twitchplayer.tv.feature.channel.presentation

import com.twitchplayer.tv.core.data.model.ChannelDetails
import com.twitchplayer.tv.core.data.model.Replay
import com.twitchplayer.tv.core.data.repository.SessionRepository
import com.twitchplayer.tv.core.data.repository.TwitchRepository
import com.twitchplayer.tv.core.presentation.Load
import com.twitchplayer.tv.core.presentation.Loader
import com.twitchplayer.tv.feature.vod.data.VodProgress
import com.twitchplayer.tv.feature.vod.data.VodProgressStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Replays loaded after the first page: [cursor] is where the next page starts (null once everything is loaded). */
data class ReplayTail(val replays: List<Replay> = emptyList(), val cursor: String? = null, val started: Boolean = false, val loading: Boolean = false)

/**
 * What the channel screen shows: the channel ([details]), the replays loaded after the first page ([tail]) and where the
 * viewer stopped in each replay ([progress], for the progress bars).
 */
data class ChannelUiState(
    val details: Load<ChannelDetails> = Load.Loading,
    val tail: ReplayTail = ReplayTail(),
    val progress: Map<String, VodProgress> = emptyMap(),
)

sealed interface ChannelEvent {
    /** Loads the next page of replays ([firstCursor] is the one from the channel details); a no-op while one is loading or at the end. */
    data class LoadMoreReplays(val channelId: String, val firstCursor: String?) : ChannelEvent
    data object Retry : ChannelEvent
}

class ChannelPresenter(
    login: String,
    private val repository: TwitchRepository,
    session: SessionRepository,
    progressStore: VodProgressStore,
    private val scope: CoroutineScope,
    loadDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val loader = Loader<ChannelDetails>(scope, session, loadDispatcher) { repository.channel(login) }
    private val tail = MutableStateFlow(ReplayTail())

    val state: StateFlow<ChannelUiState> = combine(loader.state, tail, progressStore.progress) { details, tail, progress ->
        ChannelUiState(details, tail, progress)
        // Lazily, like `Loader.state` it wraps: the channel is loaded when the screen first collects and the state is never
        // stale when it collects again (a stop timeout would show the previous value for a frame before refreshing).
    }.stateIn(scope, SharingStarted.Lazily, ChannelUiState())

    fun onEvent(event: ChannelEvent) {
        when (event) {
            is ChannelEvent.LoadMoreReplays -> loadMoreReplays(event.channelId, event.firstCursor)

            ChannelEvent.Retry -> {
                tail.value = ReplayTail()
                loader.reload()
            }
        }
    }

    private fun loadMoreReplays(channelId: String, firstCursor: String?) {
        val current = tail.value
        val cursor = if (current.started) current.cursor else firstCursor
        if (current.loading || cursor == null) return
        tail.value = current.copy(started = true, cursor = cursor, loading = true)
        scope.launch {
            try {
                val page = repository.replays(channelId, cursor)
                tail.update { tail -> tail.copy(replays = (tail.replays + page.replays).distinctBy { it.id }, cursor = page.cursor, loading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Keeps the cursor: the next time focus reaches the end it tries again.
                tail.update { it.copy(loading = false) }
            }
        }
    }
}
