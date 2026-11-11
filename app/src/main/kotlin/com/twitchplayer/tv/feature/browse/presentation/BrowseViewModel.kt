package com.twitchplayer.tv.feature.browse.presentation

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twitchplayer.tv.core.data.model.Category
import com.twitchplayer.tv.core.data.model.CategoryGroup
import com.twitchplayer.tv.core.data.repository.SessionRepository
import com.twitchplayer.tv.core.data.repository.TwitchRepository
import com.twitchplayer.tv.core.presentation.Load
import com.twitchplayer.tv.core.presentation.Loader
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

enum class BrowseFilter(val group: CategoryGroup?) { All(null), Games(CategoryGroup.Games), Irl(CategoryGroup.Irl), Music(CategoryGroup.Music), Creative(CategoryGroup.Creative) }

enum class BrowseSort { Popularity, Alphabetical }

@Immutable
data class BrowseUiState(
    val filter: BrowseFilter = BrowseFilter.All,
    val sort: BrowseSort = BrowseSort.Popularity,
    val categories: ImmutableList<Category> = kotlinx.collections.immutable.persistentListOf(),
)

/** Filter then sort. Twitch returns categories by popularity, so that order is the source order. */
internal fun applyBrowse(source: List<Category>, filter: BrowseFilter, sort: BrowseSort): List<Category> {
    val filtered = filter.group?.let { g -> source.filter { it.group == g } } ?: source
    return if (sort == BrowseSort.Alphabetical) filtered.sortedBy { it.name.lowercase() } else filtered
}

class BrowseViewModel(
    repository: TwitchRepository,
    session: SessionRepository,
    loadDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val loader = Loader(viewModelScope, session, loadDispatcher) { repository.topCategories() }
    private val filter = MutableStateFlow(BrowseFilter.All)
    private val sort = MutableStateFlow(BrowseSort.Popularity)

    val state: StateFlow<Load<BrowseUiState>> = combine(loader.state, filter, sort) { load, f, s ->
        when (load) {
            is Load.Ready -> Load.Ready(BrowseUiState(f, s, applyBrowse(load.data, f, s).toImmutableList()))
            Load.Loading -> Load.Loading
            Load.SignedOut -> Load.SignedOut
            Load.Failed -> Load.Failed
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, Load.Loading)

    fun selectFilter(value: BrowseFilter) = filter.update { value }
    fun toggleSort() = sort.update { if (it == BrowseSort.Popularity) BrowseSort.Alphabetical else BrowseSort.Popularity }
    fun retry() = loader.reload()
}
