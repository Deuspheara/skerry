package com.twitchplayer.tv.feature.follows.presentation

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twitchplayer.tv.core.data.model.ChannelRef
import com.twitchplayer.tv.core.data.model.LiveStream
import com.twitchplayer.tv.core.data.repository.SessionRepository
import com.twitchplayer.tv.core.data.repository.TwitchRepository
import com.twitchplayer.tv.core.presentation.Loader
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

@Immutable
data class FollowsContent(val live: ImmutableList<LiveStream>, val offline: ImmutableList<ChannelRef>)

class FollowsViewModel(repository: TwitchRepository, session: SessionRepository) : ViewModel() {
    private val loader = Loader(viewModelScope, session) {
        repository.follows(force = true).let { FollowsContent(it.live.toImmutableList(), it.offline.toImmutableList()) }
    }
    val state = loader.state
    fun retry() = loader.reload()
}
