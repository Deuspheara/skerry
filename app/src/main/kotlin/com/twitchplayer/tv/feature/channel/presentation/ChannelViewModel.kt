package com.twitchplayer.tv.feature.channel.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twitchplayer.tv.core.data.model.ChannelDetails
import com.twitchplayer.tv.core.data.model.Replay
import com.twitchplayer.tv.core.data.repository.SessionRepository
import com.twitchplayer.tv.core.data.repository.TwitchRepository
import com.twitchplayer.tv.core.presentation.Loader
import com.twitchplayer.tv.feature.vod.data.VodProgress
import com.twitchplayer.tv.feature.vod.data.VodProgressStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Replays loaded after the first page: [cursor] is where the next page starts (null once everything is loaded). */
data class ReplayTail(val replays: List<Replay> = emptyList(), val cursor: String? = null, val started: Boolean = false, val loading: Boolean = false)

class ChannelViewModel(
    login: String,
    private val repository: TwitchRepository,
    session: SessionRepository,
    progressStore: VodProgressStore,
) : ViewModel() {
    private val loader = Loader<ChannelDetails>(viewModelScope, session) { repository.channel(login) }
    val state = loader.state

    /** Where the viewer stopped in each replay, for the progress bars. */
    val progress: StateFlow<Map<String, VodProgress>> = progressStore.progress.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val mutableTail = MutableStateFlow(ReplayTail())
    val tail: StateFlow<ReplayTail> = mutableTail.asStateFlow()

    /** Slug of the clip last opened: coming back from the clip screen puts focus on its card again. */
    var lastClip: String? = null

    /** Id of the replay last opened, for the same reason. */
    var lastReplay: String? = null

    /** Loads the next page of replays ([firstCursor] is the one from the channel details); a no-op while one is loading or at the end. */
    fun loadMoreReplays(channelId: String, firstCursor: String?) {
        val current = mutableTail.value
        val cursor = if (current.started) current.cursor else firstCursor
        if (current.loading || cursor == null) return
        mutableTail.value = current.copy(started = true, cursor = cursor, loading = true)
        viewModelScope.launch {
            try {
                val page = repository.replays(channelId, cursor)
                mutableTail.update { tail -> tail.copy(replays = (tail.replays + page.replays).distinctBy { it.id }, cursor = page.cursor, loading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Keeps the cursor: the next time focus reaches the end it tries again.
                mutableTail.update { it.copy(loading = false) }
            }
        }
    }

    fun retry() {
        mutableTail.value = ReplayTail()
        loader.reload()
    }
}
