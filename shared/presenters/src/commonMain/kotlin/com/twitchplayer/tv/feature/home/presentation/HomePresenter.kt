package com.twitchplayer.tv.feature.home.presentation

import com.twitchplayer.tv.core.data.model.Category
import com.twitchplayer.tv.core.data.model.LiveStream
import com.twitchplayer.tv.core.data.repository.SessionRepository
import com.twitchplayer.tv.core.data.repository.SnapshotCache
import com.twitchplayer.tv.core.data.repository.TwitchRepository
import com.twitchplayer.tv.core.presentation.Load
import com.twitchplayer.tv.core.presentation.LoadCache
import com.twitchplayer.tv.core.presentation.Loader
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

data class HomeContent(
    val following: ImmutableList<LiveStream>,
    val forYou: ImmutableList<LiveStream>,
    val categories: ImmutableList<Category>,
)

/** Home as stored on disk between sessions (plain lists: the immutable ones are a UI concern). */
@Serializable
private class HomeSnapshot(val following: List<LiveStream>, val forYou: List<LiveStream>, val categories: List<Category>)

private fun HomeSnapshot.toContent() = HomeContent(following.toImmutableList(), forYou.toImmutableList(), categories.toImmutableList())

sealed interface HomeEvent {
    data object Retry : HomeEvent
}

/** Home shows the last session's copy at once (from [snapshots]) and replaces it when the fresh one arrives. */
class HomePresenter(
    repository: TwitchRepository,
    session: SessionRepository,
    snapshots: SnapshotCache?,
    scope: CoroutineScope,
    loadDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val cache = snapshots?.let { store ->
        LoadCache<HomeContent>(
            read = { user -> store.read(SNAPSHOT, user, HomeSnapshot.serializer())?.toContent() },
            write = { user, c -> store.write(SNAPSHOT, user, HomeSnapshot.serializer(), HomeSnapshot(c.following, c.forYou, c.categories)) },
        )
    }
    private val loader = Loader(scope, session, loadDispatcher, cache) {
        coroutineScope {
            val follows = async { repository.follows(force = true) }
            val forYou = async { repository.recommended() }
            val categories = async { repository.topCategories() }
            HomeContent(
                following = follows.await().live.toImmutableList(),
                forYou = forYou.await().toImmutableList(),
                categories = categories.await().take(HOME_CATEGORIES).toImmutableList(),
            )
        }
    }

    val state: StateFlow<Load<HomeContent>> = loader.state

    fun onEvent(event: HomeEvent) = when (event) {
        HomeEvent.Retry -> loader.reload()
    }

    private companion object {
        const val SNAPSHOT = "home"
        const val HOME_CATEGORIES = 8
    }
}
