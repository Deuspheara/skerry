package com.twitchplayer.tv.core.presentation

import com.twitchplayer.tv.core.data.model.Category
import com.twitchplayer.tv.core.data.model.ChannelDetails
import com.twitchplayer.tv.core.data.model.ChannelRef
import com.twitchplayer.tv.core.data.model.Follows
import com.twitchplayer.tv.core.data.model.LiveStream
import com.twitchplayer.tv.core.data.model.Replay
import com.twitchplayer.tv.core.data.model.ReplayPage
import com.twitchplayer.tv.core.data.repository.SessionRepository
import com.twitchplayer.tv.core.data.repository.TwitchRepository
import com.twitchplayer.tv.core.network.auth.DeviceCodeResponse
import com.twitchplayer.tv.feature.auth.presentation.AuthEvent
import com.twitchplayer.tv.feature.auth.presentation.AuthPresenter
import com.twitchplayer.tv.feature.channel.presentation.ChannelEvent
import com.twitchplayer.tv.feature.channel.presentation.ChannelPresenter
import com.twitchplayer.tv.feature.home.presentation.HomePresenter
import com.twitchplayer.tv.feature.search.presentation.SearchEvent
import com.twitchplayer.tv.feature.search.presentation.SearchPresenter
import com.twitchplayer.tv.feature.search.presentation.VoiceInput
import com.twitchplayer.tv.feature.search.presentation.VoiceState
import com.twitchplayer.tv.feature.vod.data.VodProgress
import com.twitchplayer.tv.feature.vod.data.VodProgressStore
import com.twitchplayer.tv.testing.FakeRepository
import com.twitchplayer.tv.testing.FakeSession
import com.twitchplayer.tv.testing.FakeSettings
import com.twitchplayer.tv.testing.stream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The UI owns a presenter's scope (Android: `viewModelScope`, cancelled in `onCleared`). Once it is cancelled nothing the
 * presenter started may change its state or touch a repository: loads, the Search debounce and voice job, the sign-in job.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PresenterLifetimeTest {
    private val dispatcher = UnconfinedTestDispatcher()

    /** A scope the test cancels itself (unlike `backgroundScope`), on the test's virtual time. */
    private fun TestScope.uiScope() = CoroutineScope(SupervisorJob() + dispatcher)

    @Test fun homeLoadStopsWhenTheScopeIsCancelled() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val repo = object : TwitchRepository by FakeRepository(live = listOf(stream("a"))) {
            override suspend fun follows(force: Boolean): Follows {
                gate.await()
                return Follows(listOf(stream("a")), emptyList())
            }
            override suspend fun recommended(): List<LiveStream> {
                gate.await()
                return emptyList()
            }
            override suspend fun topCategories(): List<Category> {
                gate.await()
                return emptyList()
            }
        }
        val scope = uiScope()
        val home = HomePresenter(repo, FakeSession(), null, scope, dispatcher)
        home.state.onEach { }.launchIn(scope)
        assertEquals(Load.Loading, home.state.value)

        scope.cancel()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(Load.Loading, home.state.value, "a result arriving after the screen is gone is never published")
    }

    @Test fun searchDebounceAndVoiceStopWhenTheScopeIsCancelled() = runTest(dispatcher) {
        val repo = FakeRepository(live = listOf(stream("a")))
        val settings = FakeSettings()
        val heard = CompletableDeferred<String?>()
        val voice = object : VoiceInput {
            override fun isAvailable() = true
            override suspend fun listen(): String? = heard.await()
        }
        val scope = uiScope()
        val search = SearchPresenter(repo, settings, voice, scope, debounceMs = 300)
        search.state.onEach { }.launchIn(scope)

        search.onEvent(SearchEvent.Append("o")) // debounce pending
        search.onEvent(SearchEvent.StartVoice)
        assertEquals(VoiceState.Listening, search.state.value.voice)

        scope.cancel()
        heard.complete("hello world")
        advanceTimeBy(5_000)
        advanceUntilIdle()

        assertTrue(repo.searchCalls.isEmpty(), "the pending debounced search never runs")
        assertEquals(VoiceState.Listening, search.state.value.voice, "the voice job never moves on to Heard")
        assertTrue(settings.settings.value.recentSearches.isEmpty(), "nothing is saved after cancellation")
        // Events sent to a dead presenter are harmless no-ops.
        search.onEvent(SearchEvent.CommitQuery)
        advanceUntilIdle()
        assertTrue(settings.settings.value.recentSearches.isEmpty())
    }

    @Test fun signInStopsWhenTheScopeIsCancelled() = runTest(dispatcher) {
        val code = DeviceCodeResponse("dc", "ABCD-EFGH", "https://twitch.tv/activate", 600, 5)
        val finish = CompletableDeferred<Unit>()
        var signedOut = false
        val session = object : SessionRepository by FakeSession() {
            override suspend fun signIn(onCode: (DeviceCodeResponse) -> Unit): Boolean {
                onCode(code)
                finish.await()
                return true
            }
            override suspend fun signOut() {
                signedOut = true
            }
        }
        val scope = uiScope()
        val auth = AuthPresenter(session, scope)
        auth.onEvent(AuthEvent.SignIn)
        assertEquals(code, auth.state.value.code)

        scope.cancel()
        finish.complete(Unit)
        advanceUntilIdle()
        auth.onEvent(AuthEvent.SignOut)
        advanceUntilIdle()

        assertEquals(code, auth.state.value.code, "the sign-in job did not run its epilogue (which would clear the code)")
        assertFalse(signedOut, "a sign-out launched on a cancelled scope never runs")
    }

    @Test fun channelLoadAndReplayPagingStopWhenTheScopeIsCancelled() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        var pagesAsked = 0
        val repo = object : TwitchRepository by FakeRepository() {
            override suspend fun channel(login: String): ChannelDetails {
                gate.await()
                return ChannelDetails(ChannelRef("1", login, login, null, false, 0), null, false, emptyList(), similar = emptyList())
            }
            override suspend fun replays(channelId: String, cursor: String): ReplayPage {
                pagesAsked++
                return ReplayPage(listOf(Replay("r1", "t", "", "")), null)
            }
        }
        val progress = object : VodProgressStore {
            override val progress = MutableStateFlow(emptyMap<String, VodProgress>())
            override suspend fun save(vodId: String, positionMs: Long, durationMs: Long) = Unit
            override suspend fun clear(vodId: String) = Unit
        }
        val scope = uiScope()
        val channel = ChannelPresenter("camille", repo, FakeSession(), progress, scope, dispatcher)
        channel.state.onEach { }.launchIn(scope)

        scope.cancel()
        gate.complete(Unit)
        channel.onEvent(ChannelEvent.LoadMoreReplays("1", "p2"))
        advanceUntilIdle()

        assertIs<Load.Loading>(channel.state.value.details)
        assertEquals(0, pagesAsked, "no replay page is requested for a screen that is gone")
    }
}
