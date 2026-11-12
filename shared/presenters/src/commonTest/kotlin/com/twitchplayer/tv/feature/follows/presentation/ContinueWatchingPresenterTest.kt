package com.twitchplayer.tv.feature.follows.presentation

import com.twitchplayer.tv.core.data.model.Replay
import com.twitchplayer.tv.core.data.model.WatchedReplay
import com.twitchplayer.tv.core.data.repository.TwitchRepository
import com.twitchplayer.tv.feature.vod.data.VodProgress
import com.twitchplayer.tv.feature.vod.data.VodProgressStore
import com.twitchplayer.tv.testing.FakeRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ContinueWatchingPresenterTest {
    private val dispatcher = UnconfinedTestDispatcher()

    private class Store(initial: Map<String, VodProgress> = emptyMap()) : VodProgressStore {
        val all = MutableStateFlow(initial)
        override val progress = all
        override suspend fun save(vodId: String, positionMs: Long, durationMs: Long) = Unit
        override suspend fun clear(vodId: String) = Unit
    }

    private class Repo(private val known: Set<String>, private val inner: TwitchRepository) : TwitchRepository by inner {
        val asked = mutableListOf<List<String>>()
        var fail = false
        override suspend fun replaysByIds(ids: List<String>): List<WatchedReplay> {
            asked += ids
            if (fail) error("offline")
            return ids.filter { it in known }.map { id -> WatchedReplay(Replay(id, "Title $id", "1:00:00", "https://t/$id"), "c$id", "login$id", "Name$id", null) }
        }
    }

    private fun progress(position: Long, saved: Long, duration: Long = 3_600_000L) = VodProgress(position, duration, saved)

    @Test fun listsTheUnfinishedReplaysMostRecentFirst() = runTest(dispatcher) {
        val store = Store(mapOf("a" to progress(600_000, 10), "b" to progress(900_000, 30), "c" to progress(300_000, 20)))
        val presenter = ContinueWatchingPresenter(Repo(setOf("a", "b", "c"), FakeRepository()), store, backgroundScope)
        advanceUntilIdle()
        assertEquals(listOf("b", "c", "a"), presenter.items.value.map { it.replay.replay.id })
        assertEquals(900_000L, presenter.items.value.first().progress.positionMs)
    }

    @Test fun theFirstSecondsAndTheCreditsAreNotOffered() = runTest(dispatcher) {
        val store = Store(mapOf("early" to progress(5_000, 1), "end" to progress(3_595_000, 2), "ok" to progress(1_200_000, 3)))
        val presenter = ContinueWatchingPresenter(Repo(setOf("early", "end", "ok"), FakeRepository()), store, backgroundScope)
        advanceUntilIdle()
        assertEquals(listOf("ok"), presenter.items.value.map { it.replay.replay.id })
    }

    @Test fun aDeletedReplayIsLeftOutAndTheRestStays() = runTest(dispatcher) {
        val store = Store(mapOf("gone" to progress(600_000, 2), "here" to progress(600_000, 1)))
        val presenter = ContinueWatchingPresenter(Repo(setOf("here"), FakeRepository()), store, backgroundScope)
        advanceUntilIdle()
        assertEquals(listOf("here"), presenter.items.value.map { it.replay.replay.id })
    }

    @Test fun everyDetailIsAskedForOnceInOneBatch() = runTest(dispatcher) {
        val repo = Repo(setOf("a", "b"), FakeRepository())
        val store = Store(mapOf("a" to progress(600_000, 1)))
        val presenter = ContinueWatchingPresenter(repo, store, backgroundScope)
        advanceUntilIdle()
        store.all.value = mapOf("a" to progress(700_000, 5), "b" to progress(600_000, 6))
        advanceUntilIdle()
        assertEquals(listOf(listOf("a"), listOf("b")), repo.asked, "a replay already known is not fetched again")
        assertEquals(listOf("b", "a"), presenter.items.value.map { it.replay.replay.id })
    }

    @Test fun aFailedCallKeepsWhatIsKnownAndShowsNothingNew() = runTest(dispatcher) {
        val repo = Repo(setOf("a", "b"), FakeRepository())
        val store = Store(mapOf("a" to progress(600_000, 1)))
        val presenter = ContinueWatchingPresenter(repo, store, backgroundScope)
        advanceUntilIdle()
        repo.fail = true
        store.all.value = mapOf("a" to progress(600_000, 1), "b" to progress(600_000, 2))
        advanceUntilIdle()
        assertEquals(listOf("a"), presenter.items.value.map { it.replay.replay.id })
    }

    @Test fun itNeverListsMoreThanTheLimit() = runTest(dispatcher) {
        val all = (1..15).associate { "v$it" to progress(600_000, it.toLong()) }
        val repo = Repo(all.keys, FakeRepository())
        val presenter = ContinueWatchingPresenter(repo, Store(all), backgroundScope, limit = 4)
        advanceUntilIdle()
        assertEquals(4, presenter.items.value.size)
        assertTrue(presenter.items.value.all { it.replay.replay.id in setOf("v15", "v14", "v13", "v12") })
    }
}
