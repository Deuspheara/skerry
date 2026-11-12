package com.twitchplayer.tv.feature.follows.presentation

import com.twitchplayer.tv.core.data.model.ChannelRef
import com.twitchplayer.tv.core.data.model.LiveStream
import com.twitchplayer.tv.core.data.repository.SessionRepository
import com.twitchplayer.tv.core.data.repository.SnapshotCache
import com.twitchplayer.tv.core.data.repository.TwitchRepository
import com.twitchplayer.tv.core.platform.ForegroundSignal
import com.twitchplayer.tv.core.presentation.Load
import com.twitchplayer.tv.core.presentation.LoadCache
import com.twitchplayer.tv.core.presentation.Loader
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

data class FollowsContent(val live: ImmutableList<LiveStream>, val offline: ImmutableList<ChannelRef>)

/** The follows as stored on disk between sessions (plain lists: the immutable ones are a UI concern). */
@Serializable
private class FollowsSnapshot(val live: List<LiveStream>, val offline: List<ChannelRef>)

private fun FollowsSnapshot.toContent() = FollowsContent(live.toImmutableList(), offline.toImmutableList())

sealed interface FollowsEvent {
    data object Retry : FollowsEvent
}

class FollowsPresenter(
    repository: TwitchRepository,
    session: SessionRepository,
    scope: CoroutineScope,
    loadDispatcher: CoroutineDispatcher = Dispatchers.Default,
    snapshots: SnapshotCache? = null,
    foreground: ForegroundSignal? = null,
) {
    private val cache = snapshots?.let { store ->
        LoadCache<FollowsContent>(
            read = { user -> store.read(SNAPSHOT, user, FollowsSnapshot.serializer())?.toContent() },
            write = { user, c -> store.write(SNAPSHOT, user, FollowsSnapshot.serializer(), FollowsSnapshot(c.live, c.offline)) },
        )
    }
    private val loader = Loader(scope, session, loadDispatcher, cache, foreground) {
        repository.follows(force = true).let { FollowsContent(it.live.toImmutableList(), it.offline.toImmutableList()) }
    }

    val state: StateFlow<Load<FollowsContent>> = loader.state

    fun onEvent(event: FollowsEvent) = when (event) {
        FollowsEvent.Retry -> loader.reload()
    }

    private companion object {
        const val SNAPSHOT = "follows"
    }
}
