package com.twitchplayer.tv.feature.feed.presentation

import com.twitchplayer.tv.core.data.model.FeedClip
import com.twitchplayer.tv.core.data.repository.SessionRepository
import com.twitchplayer.tv.core.data.repository.SettingsRepository
import com.twitchplayer.tv.core.data.repository.TwitchRepository
import com.twitchplayer.tv.core.platform.ForegroundSignal
import com.twitchplayer.tv.core.platform.NetworkStatus
import com.twitchplayer.tv.core.presentation.Load
import com.twitchplayer.tv.core.presentation.Loader
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class ClipsContent(val clips: ImmutableList<FeedClip>)

/** What the clips page draws: the load and whether a clip may start by itself (the same autoplay choice and connection as the live feed). */
data class ClipsUiState(val load: Load<ClipsContent> = Load.Loading, val autoplayAllowed: Boolean = false)

/**
 * The clips half of Discover: the most viewed clips of the week in the busiest categories (`TwitchRepository.feedClips`), swiped like the live
 * feed. Which clip is on screen and its player belong to the UI. A clip is short and small next to a live stream, but it follows the same
 * autoplay rule so a viewer who turned previews off meets no surprise.
 */
class ClipsFeedPresenter(
    repository: TwitchRepository,
    session: SessionRepository,
    settings: SettingsRepository,
    network: NetworkStatus,
    scope: CoroutineScope,
    loadDispatcher: CoroutineDispatcher = Dispatchers.Default,
    foreground: ForegroundSignal? = null,
) {
    private val loader = Loader(scope, session, loadDispatcher, null, foreground) {
        ClipsContent(repository.feedClips().toImmutableList())
    }

    val state: StateFlow<ClipsUiState> = combine(loader.state, settings.settings.map { it.feedAutoplay }, network.metered) { load, autoplay, metered ->
        ClipsUiState(load, FeedAutoplayPolicy.allowed(autoplay, metered))
    }.stateIn(scope, SharingStarted.Lazily, ClipsUiState())

    fun retry() = loader.reload()
}

/** The clips of a ready page, empty otherwise. */
val ClipsUiState.clips: ImmutableList<FeedClip> get() = (load as? Load.Ready)?.data?.clips ?: persistentListOf()
