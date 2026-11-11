package com.twitchplayer.tv.feature.home.presentation

import com.twitchplayer.tv.core.data.repository.SnapshotCache
import com.twitchplayer.tv.core.platform.SnapshotStorage
import com.twitchplayer.tv.core.presentation.Load
import com.twitchplayer.tv.testing.FakeRepository
import com.twitchplayer.tv.testing.FakeSession
import com.twitchplayer.tv.testing.category
import com.twitchplayer.tv.testing.stream
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HomePresenterTest {
    private val dispatcher = UnconfinedTestDispatcher()

    private class MemoryStorage : SnapshotStorage {
        val files = mutableMapOf<String, String>()
        override suspend fun read(name: String): String? = files[name]
        override suspend fun write(name: String, text: String) {
            files[name] = text
        }
        override suspend fun clear() = files.clear()
    }

    @Test fun showsFollowedForYouAndTheFirstEightCategories() = runTest(dispatcher) {
        val repo = FakeRepository(live = listOf(stream("a"), stream("b")), categories = (1..12).map { category("c$it") })
        val home = HomePresenter(repo, FakeSession(), null, backgroundScope, dispatcher)
        home.state.onEach { }.launchIn(backgroundScope)
        advanceUntilIdle()

        val ready = assertIs<Load.Ready<HomeContent>>(home.state.value)
        assertEquals(listOf("a", "b"), ready.data.following.map { it.channelId })
        assertEquals(listOf("a", "b"), ready.data.forYou.map { it.channelId })
        assertEquals(8, ready.data.categories.size)
    }

    @Test fun signedOutAndFailureAreReportedAndRetryRecovers() = runTest(dispatcher) {
        val repo = FakeRepository(live = listOf(stream("a"))).apply { failNext = true }
        val home = HomePresenter(repo, FakeSession(), null, backgroundScope, dispatcher)
        home.state.onEach { }.launchIn(backgroundScope)
        advanceUntilIdle()
        assertEquals(Load.Failed, home.state.value)

        home.onEvent(HomeEvent.Retry)
        advanceUntilIdle()
        assertIs<Load.Ready<HomeContent>>(home.state.value)
    }

    @Test fun theSnapshotIsWrittenAndShownAtOnceOnTheNextStart() = runTest(dispatcher) {
        val storage = MemoryStorage()
        val cache = SnapshotCache(storage, io = dispatcher)
        val first = HomePresenter(FakeRepository(live = listOf(stream("a")), categories = listOf(category("x"))), FakeSession(), cache, backgroundScope, dispatcher)
        first.state.onEach { }.launchIn(backgroundScope)
        advanceUntilIdle()
        assertNotNull(storage.files["home"], "the fresh copy is stored under the account")

        // Next start: the network fails, the stored copy is what the screen shows instead of an error.
        val offline = FakeRepository().apply { failNext = true }
        val second = HomePresenter(offline, FakeSession(), cache, backgroundScope, dispatcher)
        second.state.onEach { }.launchIn(backgroundScope)
        advanceUntilIdle()
        val ready = assertIs<Load.Ready<HomeContent>>(second.state.value)
        assertEquals(listOf("a"), ready.data.following.map { it.channelId })
        assertEquals(listOf("x"), ready.data.categories.map { it.id })
    }

    @Test fun anotherAccountsSnapshotIsNeverShown() = runTest(dispatcher) {
        val storage = MemoryStorage()
        val cache = SnapshotCache(storage, io = dispatcher)
        HomePresenter(FakeRepository(live = listOf(stream("a"))), FakeSession(), cache, backgroundScope, dispatcher).also { it.state.onEach { }.launchIn(backgroundScope) }
        advanceUntilIdle()

        val otherUser = FakeSession(com.twitchplayer.tv.core.data.repository.SessionState.SignedIn(com.twitchplayer.tv.core.data.model.Account("someone-else", "other")))
        val offline = FakeRepository().apply { failNext = true }
        val home = HomePresenter(offline, otherUser, cache, backgroundScope, dispatcher)
        home.state.onEach { }.launchIn(backgroundScope)
        advanceUntilIdle()
        assertEquals(Load.Failed, home.state.value)
        assertNull((home.state.value as? Load.Ready<*>))
        assertTrue(storage.files.isNotEmpty())
    }
}
