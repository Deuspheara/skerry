package com.twitchplayer.tv.feature.follows.presentation

import com.twitchplayer.tv.core.data.model.ChannelRef
import com.twitchplayer.tv.core.data.model.LiveStream
import com.twitchplayer.tv.core.data.repository.SessionRepository
import com.twitchplayer.tv.core.data.repository.TwitchRepository
import com.twitchplayer.tv.core.presentation.Load
import com.twitchplayer.tv.core.presentation.Loader
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow

data class FollowsContent(val live: ImmutableList<LiveStream>, val offline: ImmutableList<ChannelRef>)

sealed interface FollowsEvent {
    data object Retry : FollowsEvent
}

class FollowsPresenter(
    repository: TwitchRepository,
    session: SessionRepository,
    scope: CoroutineScope,
    loadDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val loader = Loader(scope, session, loadDispatcher) {
        repository.follows(force = true).let { FollowsContent(it.live.toImmutableList(), it.offline.toImmutableList()) }
    }

    val state: StateFlow<Load<FollowsContent>> = loader.state

    fun onEvent(event: FollowsEvent) = when (event) {
        FollowsEvent.Retry -> loader.reload()
    }
}
