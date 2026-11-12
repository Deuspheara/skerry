package com.twitchplayer.tv.feature

import com.twitchplayer.tv.core.data.repository.SnapshotCache
import com.twitchplayer.tv.core.platform.DeviceProfile
import com.twitchplayer.tv.core.platform.SnapshotStorage
import com.twitchplayer.tv.core.presentation.Load
import com.twitchplayer.tv.feature.browse.presentation.BrowsePresenter
import com.twitchplayer.tv.feature.category.presentation.CategoryEvent
import com.twitchplayer.tv.feature.category.presentation.CategoryPresenter
import com.twitchplayer.tv.feature.follows.presentation.FollowsPresenter
import com.twitchplayer.tv.testing.FakeRepository
import com.twitchplayer.tv.testing.FakeSession
import com.twitchplayer.tv.testing.FakeSettings
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

/** Browse, Following and a category come back at once from the copy on disk (a failing network shows it instead of an error). */
@OptIn(ExperimentalCoroutinesApi::class)
class SnapshotFirstTest {
    private val dispatcher = UnconfinedTestDispatcher()

    private class MemoryStorage : SnapshotStorage {
        val files = mutableMapOf<String, String>()
        override suspend fun read(name: String): String? = files[name]
        override suspend fun write(name: String, text: String) {
            files[name] = text
        }
        override suspend fun clear() = files.clear()
    }

    private fun device(tag: String) = object : DeviceProfile {
        override val languageTag = tag
    }

    @Test fun browseShowsTheStoredCategoriesWhenTheNetworkFails() = runTest(dispatcher) {
        val storage = MemoryStorage()
        val cache = SnapshotCache(storage, io = dispatcher)
        BrowsePresenter(FakeRepository(categories = listOf(category("x"), category("y"))), FakeSession(), backgroundScope, dispatcher, cache)
            .state.onEach { }.launchIn(backgroundScope)
        advanceUntilIdle()
        assertNotNull(storage.files["browse"])

        val offline = BrowsePresenter(FakeRepository().apply { failNext = true }, FakeSession(), backgroundScope, dispatcher, cache)
        offline.state.onEach { }.launchIn(backgroundScope)
        advanceUntilIdle()
        val ready = assertIs<Load.Ready<*>>(offline.state.value)
        assertEquals(2, (ready.data as com.twitchplayer.tv.feature.browse.presentation.BrowseUiState).categories.size)
    }

    @Test fun followingShowsTheStoredChannelsWhenTheNetworkFails() = runTest(dispatcher) {
        val storage = MemoryStorage()
        val cache = SnapshotCache(storage, io = dispatcher)
        FollowsPresenter(FakeRepository(live = listOf(stream("a"))), FakeSession(), backgroundScope, dispatcher, cache)
            .state.onEach { }.launchIn(backgroundScope)
        advanceUntilIdle()
        assertNotNull(storage.files["follows"])

        val offline = FollowsPresenter(FakeRepository().apply { failNext = true }, FakeSession(), backgroundScope, dispatcher, cache)
        offline.state.onEach { }.launchIn(backgroundScope)
        advanceUntilIdle()
        val ready = assertIs<Load.Ready<com.twitchplayer.tv.feature.follows.presentation.FollowsContent>>(offline.state.value)
        assertEquals(listOf("a"), ready.data.live.map { it.channelId })
    }

    @Test fun eachCategoryAndLanguageFilterHasItsOwnStoredCopy() = runTest(dispatcher) {
        val storage = MemoryStorage()
        val cache = SnapshotCache(storage, io = dispatcher)
        val presenter = CategoryPresenter("cat", FakeRepository(), FakeSession(), FakeSettings(), device("fr-FR"), backgroundScope, dispatcher, cache)
        presenter.state.onEach { }.launchIn(backgroundScope)
        advanceUntilIdle()
        assertNotNull(storage.files["category-cat-fr"])
        assertNull(storage.files["category-cat-all"])

        presenter.onEvent(CategoryEvent.ToggleLanguage)
        advanceUntilIdle()
        assertNotNull(storage.files["category-cat-all"])
    }
}
