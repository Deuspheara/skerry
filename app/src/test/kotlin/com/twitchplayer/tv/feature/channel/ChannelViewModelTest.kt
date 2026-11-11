package com.twitchplayer.tv.feature.channel

import com.twitchplayer.tv.core.data.model.ChannelDetails
import com.twitchplayer.tv.core.data.model.ChannelRef
import com.twitchplayer.tv.core.data.model.Replay
import com.twitchplayer.tv.core.data.model.ReplayPage
import com.twitchplayer.tv.core.data.repository.TwitchRepository
import com.twitchplayer.tv.feature.channel.presentation.ChannelViewModel
import com.twitchplayer.tv.feature.vod.data.VodProgress
import com.twitchplayer.tv.feature.vod.data.VodProgressStore
import com.twitchplayer.tv.testing.FakeRepository
import com.twitchplayer.tv.testing.FakeSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChannelViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun replay(id: String) = Replay(id, "t$id", "1:00:00", "")

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

    private fun viewModel(repo: Repo) = ChannelViewModel("camille", repo, FakeSession(), Progress())

    @Test fun pagesAreAppendedInOrderAndStopAtTheEnd() = runTest(dispatcher) {
        val repo = Repo(); val vm = viewModel(repo)
        vm.loadMoreReplays("1", "p2")
        assertEquals(listOf("r3", "r4"), vm.tail.value.replays.map { it.id })
        vm.loadMoreReplays("1", "p2") // the details' cursor is only the first page's: the tail's own cursor wins now
        assertEquals(listOf("r3", "r4", "r5"), vm.tail.value.replays.map { it.id })
        assertNull(vm.tail.value.cursor)
        vm.loadMoreReplays("1", "p2")
        assertEquals("nothing is asked for past the last page", listOf("p2", "p3"), repo.asked)
    }

    @Test fun aPageInFlightIsNotAskedForTwice() = runTest(dispatcher) {
        val repo = Repo().apply { gate = CompletableDeferred() }; val vm = viewModel(repo)
        vm.loadMoreReplays("1", "p2"); vm.loadMoreReplays("1", "p2"); vm.loadMoreReplays("1", "p2")
        assertEquals(listOf("p2"), repo.asked)
        assertTrue(vm.tail.value.loading)
        repo.gate!!.complete(Unit)
        assertFalse(vm.tail.value.loading)
    }

    @Test fun aFailedPageKeepsItsCursorSoTheNextFocusRetries() = runTest(dispatcher) {
        val repo = Repo().apply { fail = true }; val vm = viewModel(repo)
        vm.loadMoreReplays("1", "p2")
        assertEquals("p2", vm.tail.value.cursor); assertFalse(vm.tail.value.loading)
        repo.fail = false
        vm.loadMoreReplays("1", "p2")
        assertEquals(listOf("r3", "r4"), vm.tail.value.replays.map { it.id })
    }

    @Test fun noCursorMeansNoRequest() = runTest(dispatcher) {
        val repo = Repo(); val vm = viewModel(repo)
        vm.loadMoreReplays("1", null)
        assertTrue(repo.asked.isEmpty())
    }

    @Test fun aPageThatRepeatsAReplayDoesNotDuplicateIt() = runTest(dispatcher) {
        val repo = object : TwitchRepository by FakeRepository() {
            override suspend fun channel(login: String) = ChannelDetails(ChannelRef("1", login, login, null, false, 0), null, false, emptyList(), similar = emptyList())
            override suspend fun replays(channelId: String, cursor: String) = ReplayPage(listOf(Replay("r3", "t", "", "")), if (cursor == "p2") "p3" else null)
        }
        val vm = ChannelViewModel("camille", repo, FakeSession(), Progress())
        vm.loadMoreReplays("1", "p2"); vm.loadMoreReplays("1", "p2")
        assertEquals(listOf("r3"), vm.tail.value.replays.map { it.id })
    }
}
