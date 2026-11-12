package com.twitchplayer.tv.platform.apple

import com.twitchplayer.tv.core.network.auth.TokenStore
import com.twitchplayer.tv.core.platform.ForegroundSignal
import com.twitchplayer.tv.core.ui.ChannelKeyBus
import com.twitchplayer.tv.feature.auth.presentation.AuthPresenter
import com.twitchplayer.tv.feature.browse.presentation.BrowsePresenter
import com.twitchplayer.tv.feature.category.presentation.CategoryPresenter
import com.twitchplayer.tv.feature.channel.presentation.ChannelPresenter
import com.twitchplayer.tv.feature.feed.presentation.ClipsFeedPresenter
import com.twitchplayer.tv.feature.feed.presentation.FeedChatPresenter
import com.twitchplayer.tv.feature.feed.presentation.FeedPresenter
import com.twitchplayer.tv.feature.follows.presentation.ContinueWatchingPresenter
import com.twitchplayer.tv.feature.follows.presentation.FollowsPresenter
import com.twitchplayer.tv.feature.home.presentation.HomePresenter
import com.twitchplayer.tv.feature.player.data.stream.ClipResolver
import com.twitchplayer.tv.feature.player.data.stream.ClipSource
import com.twitchplayer.tv.feature.player.data.stream.ResolvedStream
import com.twitchplayer.tv.feature.player.data.stream.ResolvedVod
import com.twitchplayer.tv.feature.player.data.stream.TwitchPlaybackResolver
import com.twitchplayer.tv.feature.player.data.stream.VodResolver
import com.twitchplayer.tv.feature.player.presentation.PlayerPresenter
import com.twitchplayer.tv.feature.search.presentation.SearchPresenter
import com.twitchplayer.tv.feature.settings.presentation.SettingsPresenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.koin.core.parameter.parametersOf

/**
 * The one entry point Swift talks to. Call [start] once at launch (Koin is process-wide), then ask the returned graph for
 * presenters: Koin's generics do not cross the Objective-C boundary, so each presenter has a typed factory here.
 *
 * ```swift
 * let app = TwitchApp.shared.start(clientId: "...")
 * let scope = PresenterScope()              // cancelled when the screen goes away
 * let home = app.home(scope: scope)
 * ```
 */
object TwitchApp {
    private var graph: TwitchGraph? = null

    /** Starts Koin with the production graph (once) and returns it. Later calls return the same graph. */
    fun start(clientId: String): TwitchGraph = graph ?: TwitchGraph(startKoin { modules(appleModules(AppleConfig(clientId))) }.koin).also { graph = it }
}

/** The presenters and services of the running app. */
class TwitchGraph internal constructor(private val koin: Koin) {
    /** Call from `scenePhase == .active` (and `applicationDidBecomeActive` on macOS): visible feeds refresh when old enough. */
    val foreground: ForegroundSignal get() = koin.get()
    val channelKeys: ChannelKeyBus get() = koin.get()
    val tokens: TokenStore get() = koin.get()

    fun auth(scope: CoroutineScope): AuthPresenter = koin.get { parametersOf(scope) }
    fun home(scope: CoroutineScope): HomePresenter = koin.get { parametersOf(scope) }
    fun follows(scope: CoroutineScope): FollowsPresenter = koin.get { parametersOf(scope) }
    fun continueWatching(scope: CoroutineScope): ContinueWatchingPresenter = koin.get { parametersOf(scope) }
    fun browse(scope: CoroutineScope): BrowsePresenter = koin.get { parametersOf(scope) }
    fun category(categoryId: String, scope: CoroutineScope): CategoryPresenter = koin.get { parametersOf(categoryId, scope) }
    fun channel(login: String, scope: CoroutineScope): ChannelPresenter = koin.get { parametersOf(login, scope) }
    fun search(scope: CoroutineScope): SearchPresenter = koin.get { parametersOf(scope) }
    fun settings(scope: CoroutineScope): SettingsPresenter = koin.get { parametersOf(scope) }
    fun player(login: String, scope: CoroutineScope): PlayerPresenter = koin.get { parametersOf(login, scope) }
    fun feed(scope: CoroutineScope): FeedPresenter = koin.get { parametersOf(scope) }
    fun clipsFeed(scope: CoroutineScope): ClipsFeedPresenter = koin.get { parametersOf(scope) }
    fun feedChat(channelId: String, scope: CoroutineScope): FeedChatPresenter = koin.get { parametersOf(channelId, scope) }

    /** What `AVPlayer` needs: the signed HLS URLs. Swift builds an `AVPlayerItem` from `masterUrl` (live), `playlistUrl` (replay) or a clip file. */
    val playback: PlaybackUrls get() = PlaybackUrls(koin.get(), koin.get(), koin.get())
}

/** Resolves a live channel, a replay or a clip to URLs `AVPlayer` can open (native HLS, no web view). Throws on offline or refused. */
class PlaybackUrls internal constructor(
    private val live: TwitchPlaybackResolver,
    private val vods: VodResolver,
    private val clips: ClipResolver,
) {
    suspend fun live(channel: String): ResolvedStream = live.resolve(channel)

    suspend fun replay(vodId: String): ResolvedVod = vods.resolve(vodId)

    suspend fun clip(slug: String): List<ClipSource> = clips.resolve(slug)
}

/**
 * The lifetime of one screen's presenters: a `SupervisorJob` on the main dispatcher. Swift creates one per screen and calls
 * [cancel] in `deinit` (or `.onDisappear`); everything the presenters launched stops with it.
 */
class PresenterScope : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) {
    fun cancel() {
        coroutineContext[Job]?.cancel()
    }
}

/** Collects this flow on the main thread and calls [onEach] with each value, until [Watch.cancel]. For code that cannot use `async` sequences. */
fun <T> Flow<T>.watch(scope: CoroutineScope, onEach: (T) -> Unit): Watch = Watch(onEach(onEach).launchIn(scope))

class Watch internal constructor(private val job: Job) {
    fun cancel() = job.cancel()
}
