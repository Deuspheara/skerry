package com.twitchplayer.tv.platform.apple

import com.twitchplayer.tv.core.data.repository.SessionRepository
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
import com.twitchplayer.tv.feature.vod.data.VodProgressStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.koin.core.module.Module
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

    /** Client id for the unofficial playback requests (Info.plist `PlaybackClientId`); set before [start]. Blank means native playback stays off. */
    var playbackClientId: String = ""

    /**
     * Starts Koin with the production graph (once), loads the stored Twitch session and returns the graph. Later calls return the same
     * graph. Without the session start every screen would wait forever for "who is signed in".
     */
    fun start(clientId: String): TwitchGraph = graph ?: TwitchGraph(startKoin { modules(appleModules(AppleConfig(clientId, playbackClientId = playbackClientId))) }.koin).also {
        it.startSession()
        graph = it
    }

    /** Starts Koin with the production graph plus [extra] modules loaded after it (they override its bindings); used by the demo, which is not in release builds. */
    internal fun startWith(clientId: String, extra: List<Module>): TwitchGraph = graph ?: TwitchGraph(
        startKoin { modules(appleModules(AppleConfig(clientId, playbackClientId = playbackClientId)) + extra) }.koin,
    ).also {
        it.startSession()
        graph = it
    }
}

/** The presenters and services of the running app. */
class TwitchGraph internal constructor(private val koin: Koin) {
    internal fun startSession() = koin.get<SessionRepository>().start()

    /** Call from `scenePhase == .active` (and `applicationDidBecomeActive` on macOS): visible feeds refresh when old enough. */
    val foreground: ForegroundSignal get() = koin.get()
    val channelKeys: ChannelKeyBus get() = koin.get()
    val tokens: TokenStore get() = koin.get()

    /** Checks of the Apple platform seams, for the smoke app and for diagnostics. */
    val selfTest: AppleSelfTest get() = AppleSelfTest(koin.get(), koin.get(), koin.get())

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

    /** Where the viewer stopped in replays: read on open, saved while a replay plays ("continue watching" lists these). */
    val replayProgress: ReplayProgress get() = ReplayProgress(koin.get())

    /** What `AVPlayer` needs: the signed HLS URLs. Swift builds an `AVPlayerItem` from `masterUrl` (live), `playlistUrl` (replay) or a clip file. */
    val playback: PlaybackUrls get() = PlaybackUrls(koin.get(), koin.get(), koin.get())
}

/**
 * The replay positions, as concrete suspend functions Swift can call (the store itself is a Kotlin interface).
 *
 * Every suspend function Swift calls is `@Throws(Throwable::class)`: without it Kotlin/Native does not turn an exception into a Swift error (SKIE included), it terminates
 * the app ("Uncaught Kotlin exception"): opening a subscriber-only, removed or failing replay, a clip that is gone or an offline channel crashed the iOS app.
 */
class ReplayProgress internal constructor(private val store: VodProgressStore) {
    @Throws(Throwable::class)
    suspend fun position(vodId: String): Long = store.progress.first()[vodId]?.positionMs ?: 0L

    @Throws(Throwable::class)
    suspend fun save(vodId: String, positionMs: Long, durationMs: Long) = store.save(vodId, positionMs, durationMs)
}

/** Resolves a live channel, a replay or a clip to URLs `AVPlayer` can open (native HLS, no web view). Throws on offline or refused (`ChannelOfflineException`, `VodAccessException`...). */
class PlaybackUrls internal constructor(
    private val live: TwitchPlaybackResolver,
    private val vods: VodResolver,
    private val clips: ClipResolver,
) {
    @Throws(Throwable::class)
    suspend fun live(channel: String): ResolvedStream = live.resolve(channel)

    @Throws(Throwable::class)
    suspend fun replay(vodId: String): ResolvedVod = vods.resolve(vodId)

    @Throws(Throwable::class)
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
