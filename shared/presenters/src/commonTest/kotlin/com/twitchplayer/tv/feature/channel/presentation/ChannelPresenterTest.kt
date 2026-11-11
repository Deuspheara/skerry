package com.twitchplayer.tv.feature.channel.presentation

import com.twitchplayer.tv.core.data.model.ChannelDetails
import com.twitchplayer.tv.core.data.model.ChannelRef
import com.twitchplayer.tv.core.data.model.Replay
import com.twitchplayer.tv.core.data.model.ReplayPage
import com.twitchplayer.tv.core.data.repository.TwitchRepository
import com.twitchplayer.tv.feature.vod.data.VodProgress
import com.twitchplayer.tv.feature.vod.data.VodProgressStore
import com.twitchplayer.tv.testing.FakeRepository
import com.twitchplayer.tv.testing.FakeSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ChannelPresenterTest {
    private val dispatcher = UnconfinedTestDispatcher()

    private class Progress : VodProgressStore {
        override val progress = MutableStateFlow(emptyMap<String, VodProgress>())
        override suspend fun save(vodId: String, positionMs: Long, durationMs: Long) = Unit
        override suspend fun clear(vodId: String) = Unit
    }

    /** Pages: cursor "p2" -> r3, r4 (next "p3"); "p3" -> r5 (last). */
    private class Repo : TwitchRepository by FakeRepository() {
        val asked = mutableListOf<String>()
        var gate: CompletableDeferred<Unit>? = null
        var fail = false
        override suspend fun channel(login: String) = ChannelDetails(ChannelRef("1", login, login, null, false, 0), null, false, emptyList(), similar = emptyList())
        override suspend fun replays(channelId: String, cursor: String): ReplayPage {
            asked += cursor
            gate?.await()
            if (fail) error("offline")
            return when (cursor) {
                "p2" -> ReplayPage(listOf(Replay("r3", "t", "", ""), Replay("r4", "t", "", "")), "p3")
                else -> ReplayPage(listOf(Replay("r5", "t", "", "")), null)
            }
        }
    }

    private fun TestScope.presenter(repo: TwitchRepository) = ChannelPresenter("camille", repo, FakeSession(), Progress(), backgroundScope, dispatcher)
        .also { it.state.onEach { }.launchIn(backgroundScope) }

    @Test fun pagesAreAppendedInOrderAndStopAtTheEnd() = runTest(dispatcher) {
        val repo = Repo()
        val vm = presenter(repo)
        vm.onEvent(ChannelEvent.LoadMoreReplays("1", "p2"))
        assertEquals(listOf("r3", "r4"), vm.state.value.tail.replays.map { it.id })
        vm.onEvent(ChannelEvent.LoadMoreReplays("1", "p2")) // the details' cursor is only the first page's: the tail's own cursor wins now
        assertEquals(listOf("r3", "r4", "r5"), vm.state.value.tail.replays.map { it.id })
        assertNull(vm.state.value.tail.cursor)
        vm.onEvent(ChannelEvent.LoadMoreReplays("1", "p2"))
        assertEquals(listOf("p2", "p3"), repo.asked, "nothing is asked for past the last page")
    }

    @Test fun aPageInFlightIsNotAskedForTwice() = runTest(dispatcher) {
        val repo = Repo().apply { gate = CompletableDeferred() }
        val vm = presenter(repo)
        vm.onEvent(ChannelEvent.LoadMoreReplays("1", "p2"))
        vm.onEvent(ChannelEvent.LoadMoreReplays("1", "p2"))
        vm.onEvent(ChannelEvent.LoadMoreReplays("1", "p2"))
        assertEquals(listOf("p2"), repo.asked)
        assertTrue(vm.state.value.tail.loading)
        repo.gate!!.complete(Unit)
        assertFalse(vm.state.value.tail.loading)
    }

    @Test fun aFailedPageKeepsItsCursorSoTheNextFocusRetries() = runTest(dispatcher) {
        val repo = Repo().apply { fail = true }
        val vm = presenter(repo)
        vm.onEvent(ChannelEvent.LoadMoreReplays("1", "p2"))
        assertEquals("p2", vm.state.value.tail.cursor)
        assertFalse(vm.state.value.tail.loading)
        repo.fail = false
        vm.onEvent(ChannelEvent.LoadMoreReplays("1", "p2"))
        assertEquals(listOf("r3", "r4"), vm.state.value.tail.replays.map { it.id })
    }

    @Test fun noCursorMeansNoRequest() = runTest(dispatcher) {
        val repo = Repo()
        val vm = presenter(repo)
        vm.onEvent(ChannelEvent.LoadMoreReplays("1", null))
        assertTrue(repo.asked.isEmpty())
    }

    @Test fun aPageThatRepeatsAReplayDoesNotDuplicateIt() = runTest(dispatcher) {
        val repo = object : TwitchRepository by FakeRepository() {
            override suspend fun channel(login: String) = ChannelDetails(ChannelRef("1", login, login, null, false, 0), null, false, emptyList(), similar = emptyList())
            override suspend fun replays(channelId: String, cursor: String) = ReplayPage(listOf(Replay("r3", "t", "", "")), if (cursor == "p2") "p3" else null)
        }
        val vm = presenter(repo)
        vm.onEvent(ChannelEvent.LoadMoreReplays("1", "p2"))
        vm.onEvent(ChannelEvent.LoadMoreReplays("1", "p2"))
        assertEquals(listOf("r3"), vm.state.value.tail.replays.map { it.id })
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChannelPresenterStateTest {
    private val dispatcher = UnconfinedTestDispatcher()

    private class Progress : VodProgressStore {
        override val progress = MutableStateFlow(emptyMap<String, VodProgress>())
        override suspend fun save(vodId: String, positionMs: Long, durationMs: Long) = Unit
        override suspend fun clear(vodId: String) = Unit
    }

    /** The channel's display name changes on every load, so a refresh is visible in the state. */
    private class Versioned : TwitchRepository by FakeRepository() {
        var loads = 0
        override suspend fun channel(login: String) = ChannelDetails(ChannelRef("1", login, "v${++loads}", null, false, 0), null, false, emptyList(), similar = emptyList())
    }

    private fun ChannelUiState.name() = (details as com.twitchplayer.tv.core.presentation.Load.Ready<ChannelDetails>).data.channel.name

    @Test fun watchProgressIsMergedIntoTheState() = runTest(dispatcher) {
        val progress = Progress()
        val presenter = ChannelPresenter("camille", Versioned(), FakeSession(), progress, backgroundScope, dispatcher)
        presenter.state.onEach { }.launchIn(backgroundScope)
        assertTrue(presenter.state.value.progress.isEmpty())

        progress.progress.value = mapOf("r1" to VodProgress(60_000, 600_000))
        assertEquals(mapOf("r1" to VodProgress(60_000, 600_000)), presenter.state.value.progress)
    }

    @Test fun returningAfterMoreThanTheOldStopTimeoutShowsFreshStateWithoutANewCollector() = runTest(dispatcher) {
        val repo = Versioned()
        val presenter = ChannelPresenter("camille", repo, FakeSession(), Progress(), backgroundScope, dispatcher)
        val collector = presenter.state.onEach { }.launchIn(backgroundScope)
        assertEquals("v1", presenter.state.value.name())

        collector.cancel() // the screen left composition (the player opened)
        advanceTimeBy(6_000) // longer than the 5 s a WhileSubscribed policy would have waited
        presenter.onEvent(ChannelEvent.Retry)
        advanceUntilIdle()

        // No collector at all: a stopped upstream would still show "v1" here.
        assertEquals("v2", presenter.state.value.name())
        assertEquals(2, repo.loads)
    }

    @Test fun theChannelIsNotLoadedBeforeTheFirstCollector() = runTest(dispatcher) {
        val repo = Versioned()
        val presenter = ChannelPresenter("camille", repo, FakeSession(), Progress(), backgroundScope, dispatcher)
        advanceUntilIdle()
        assertEquals(0, repo.loads, "same start as the old ViewModel: loading begins when the screen first collects")
        presenter.state.onEach { }.launchIn(backgroundScope)
        assertEquals(1, repo.loads)
    }
}
