package com.twitchplayer.tv.feature.feed.presentation

import com.twitchplayer.tv.core.data.model.FeedAutoplay
import com.twitchplayer.tv.core.data.model.LiveStream
import com.twitchplayer.tv.core.data.repository.SessionRepository
import com.twitchplayer.tv.core.data.repository.SettingsRepository
import com.twitchplayer.tv.core.data.repository.TwitchRepository
import com.twitchplayer.tv.core.platform.DeviceProfile
import com.twitchplayer.tv.core.platform.ForegroundSignal
import com.twitchplayer.tv.core.platform.NetworkStatus
import com.twitchplayer.tv.core.presentation.Load
import com.twitchplayer.tv.core.presentation.Loader
import com.twitchplayer.tv.feature.category.presentation.twitchLanguage
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** The feed's streams, in the order they are swiped through. */
data class FeedContent(val streams: ImmutableList<LiveStream>)

/** What the screen draws: the load, the viewer's autoplay choice and whether autoplay is allowed right now (setting and connection). */
data class FeedUiState(
    val load: Load<FeedContent> = Load.Loading,
    val autoplay: FeedAutoplay = FeedAutoplay.WifiOnly,
    val autoplayAllowed: Boolean = false,
    /** The page the viewer has stayed on long enough for chat to connect (see [FeedEvent.Settled]); null before any. */
    val settledLogin: String? = null,
)

sealed interface FeedEvent {
    data object Retry : FeedEvent

    /** The viewer rests on [login]'s page: the channel counts as seen and chat may connect to it. */
    data class Settled(val login: String) : FeedEvent
}

/**
 * The vertical Discover feed: followed live channels first, then the busiest streams in the viewer's language ([FeedRanking]), refreshed
 * like the other live lists (stale-while-revalidate, again when the app returns). Which stream is on screen, its player and its chat are the
 * UI's: this holds the list, the autoplay rule and the channels already seen, so a refresh does not bring the same first stream back.
 */
class FeedPresenter(
    repository: TwitchRepository,
    session: SessionRepository,
    settings: SettingsRepository,
    network: NetworkStatus,
    device: DeviceProfile,
    scope: CoroutineScope,
    loadDispatcher: CoroutineDispatcher = Dispatchers.Default,
    foreground: ForegroundSignal? = null,
) {
    private val language = twitchLanguage(device.languageTag)

    /** Channels the viewer already rested on; a snapshot is read on the load dispatcher, so it is an immutable set behind a flow. */
    private val seen = MutableStateFlow<Set<String>>(emptySet())
    private val settled = MutableStateFlow<String?>(null)

    private val loader = Loader(scope, session, loadDispatcher, null, foreground) {
        coroutineScope {
            val follows = async { runCatching { repository.follows(force = true).live }.getOrDefault(emptyList()) }
            val top = async { repository.recommended() }
            val ranked = FeedRanking.rank(follows.await(), top.await(), language, seen.value)
            FeedContent(ranked.toImmutableList())
        }
    }

    val state: StateFlow<FeedUiState> = combine(loader.state, settings.settings.map { it.feedAutoplay }, network.metered, settled) { load, autoplay, metered, login ->
        FeedUiState(load, autoplay, FeedAutoplayPolicy.allowed(autoplay, metered), login)
    }.stateIn(scope, SharingStarted.Lazily, FeedUiState())

    fun onEvent(event: FeedEvent) {
        when (event) {
            FeedEvent.Retry -> loader.reload()

            is FeedEvent.Settled -> {
                seen.update { it + event.login.lowercase() }
                settled.update { event.login }
            }
        }
    }
}

/** The streams of a ready feed, empty otherwise. */
val FeedUiState.streams: ImmutableList<LiveStream> get() = (load as? Load.Ready)?.data?.streams ?: persistentListOf()
