package com.twitchplayer.tv.feature.home.presentation

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twitchplayer.tv.core.data.model.Category
import com.twitchplayer.tv.core.data.model.LiveStream
import com.twitchplayer.tv.core.data.repository.SessionRepository
import com.twitchplayer.tv.core.data.repository.TwitchRepository
import com.twitchplayer.tv.core.presentation.LoadCache
import com.twitchplayer.tv.core.presentation.Loader
import com.twitchplayer.tv.core.data.repository.SnapshotCache
import kotlinx.serialization.Serializable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

@Immutable
data class HomeContent(
    val following: ImmutableList<LiveStream>,
    val forYou: ImmutableList<LiveStream>,
    val categories: ImmutableList<Category>,
)

/** Home as stored on disk between sessions (plain lists: the immutable ones are a UI concern). */
@Serializable
private class HomeSnapshot(val following: List<LiveStream>, val forYou: List<LiveStream>, val categories: List<Category>)

private fun HomeSnapshot.toContent() =
    HomeContent(following.toImmutableList(), forYou.toImmutableList(), categories.toImmutableList())

/** Home shows the last session's copy at once (from [snapshots]) and replaces it when the fresh one arrives. */
class HomeViewModel(repository: TwitchRepository, session: SessionRepository, snapshots: SnapshotCache? = null) : ViewModel() {
    private val cache = snapshots?.let { store ->
        LoadCache<HomeContent>(
            read = { user -> store.read(SNAPSHOT, user, HomeSnapshot.serializer())?.toContent() },
            write = { user, c -> store.write(SNAPSHOT, user, HomeSnapshot.serializer(), HomeSnapshot(c.following, c.forYou, c.categories)) },
        )
    }
    private val loader = Loader(viewModelScope, session, cache = cache) {
        coroutineScope {
            val follows = async { repository.follows(force = true) }
            val forYou = async { repository.recommended() }
            val categories = async { repository.topCategories() }
            HomeContent(
                following = follows.await().live.toImmutableList(),
                forYou = forYou.await().toImmutableList(),
                categories = categories.await().take(8).toImmutableList(),
            )
        }
    }
    val state = loader.state
    fun retry() = loader.reload()

    private companion object { const val SNAPSHOT = "home" }
}
