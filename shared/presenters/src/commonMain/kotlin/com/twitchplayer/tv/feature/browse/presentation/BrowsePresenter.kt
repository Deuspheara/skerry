package com.twitchplayer.tv.feature.browse.presentation

import com.twitchplayer.tv.core.data.model.Category
import com.twitchplayer.tv.core.data.model.CategoryGroup
import com.twitchplayer.tv.core.data.repository.SessionRepository
import com.twitchplayer.tv.core.data.repository.SnapshotCache
import com.twitchplayer.tv.core.data.repository.TwitchRepository
import com.twitchplayer.tv.core.platform.ForegroundSignal
import com.twitchplayer.tv.core.presentation.Load
import com.twitchplayer.tv.core.presentation.LoadCache
import com.twitchplayer.tv.core.presentation.Loader
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.serialization.builtins.ListSerializer

enum class BrowseFilter(val group: CategoryGroup?) { All(null), Games(CategoryGroup.Games), Irl(CategoryGroup.Irl), Music(CategoryGroup.Music), Creative(CategoryGroup.Creative) }

enum class BrowseSort { Popularity, Alphabetical }

data class BrowseUiState(
    val filter: BrowseFilter = BrowseFilter.All,
    val sort: BrowseSort = BrowseSort.Popularity,
    val categories: ImmutableList<Category> = persistentListOf(),
)

/** Filter then sort. Twitch returns categories by popularity, so that order is the source order. */
internal fun applyBrowse(source: List<Category>, filter: BrowseFilter, sort: BrowseSort): List<Category> {
    val filtered = filter.group?.let { g -> source.filter { it.group == g } } ?: source
    return if (sort == BrowseSort.Alphabetical) filtered.sortedBy { it.name.lowercase() } else filtered
}

sealed interface BrowseEvent {
    data class SelectFilter(val filter: BrowseFilter) : BrowseEvent
    data object ToggleSort : BrowseEvent
    data object Retry : BrowseEvent
}

class BrowsePresenter(
    repository: TwitchRepository,
    session: SessionRepository,
    scope: CoroutineScope,
    loadDispatcher: CoroutineDispatcher = Dispatchers.Default,
    snapshots: SnapshotCache? = null,
    foreground: ForegroundSignal? = null,
) {
    private val cache = snapshots?.let { store ->
        LoadCache<List<Category>>(
            read = { user -> store.read(SNAPSHOT, user, ListSerializer(Category.serializer())) },
            write = { user, categories -> store.write(SNAPSHOT, user, ListSerializer(Category.serializer()), categories) },
        )
    }
    private val loader = Loader(scope, session, loadDispatcher, cache, foreground) { repository.topCategories() }
    private val filter = MutableStateFlow(BrowseFilter.All)
    private val sort = MutableStateFlow(BrowseSort.Popularity)

    val state: StateFlow<Load<BrowseUiState>> = combine(loader.state, filter, sort) { load, f, s ->
        when (load) {
            is Load.Ready -> Load.Ready(BrowseUiState(f, s, applyBrowse(load.data, f, s).toImmutableList()))
            Load.Loading -> Load.Loading
            Load.SignedOut -> Load.SignedOut
            Load.Failed -> Load.Failed
        }
    }.stateIn(scope, SharingStarted.Lazily, Load.Loading)

    fun onEvent(event: BrowseEvent) {
        when (event) {
            is BrowseEvent.SelectFilter -> filter.update { event.filter }
            BrowseEvent.ToggleSort -> sort.update { if (it == BrowseSort.Popularity) BrowseSort.Alphabetical else BrowseSort.Popularity }
            BrowseEvent.Retry -> loader.reload()
        }
    }

    private companion object {
        const val SNAPSHOT = "browse"
    }
}
