package com.twitchplayer.tv.feature.browse.presentation

import com.twitchplayer.tv.core.data.model.CategoryGroup
import com.twitchplayer.tv.core.presentation.Load
import com.twitchplayer.tv.testing.FakeRepository
import com.twitchplayer.tv.testing.FakeSession
import com.twitchplayer.tv.testing.category
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BrowsePresenterTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val cats = listOf(
        category("zelda"),
        category("chat", CategoryGroup.Irl),
        category("beats", CategoryGroup.Music),
        category("alpha"),
    )

    @Test fun filterKeepsPopularityOrderAndSortIsAlphabetical() {
        assertEquals(listOf("Zelda", "Alpha"), applyBrowse(cats, BrowseFilter.Games, BrowseSort.Popularity).map { it.name })
        assertEquals(listOf("Alpha", "Zelda"), applyBrowse(cats, BrowseFilter.Games, BrowseSort.Alphabetical).map { it.name })
        assertEquals(4, applyBrowse(cats, BrowseFilter.All, BrowseSort.Popularity).size)
    }

    @Test fun stateReactsToFilterAndSort() = runTest(dispatcher) {
        val vm = BrowsePresenter(FakeRepository(categories = cats), FakeSession(), backgroundScope, dispatcher)
        val job = vm.state.onEach { }.launchIn(backgroundScope)
        advanceUntilIdle()
        vm.onEvent(BrowseEvent.SelectFilter(BrowseFilter.Irl))
        advanceUntilIdle()
        val ready = vm.state.value as Load.Ready
        assertEquals(BrowseFilter.Irl, ready.data.filter)
        assertEquals(listOf("Chat"), ready.data.categories.map { it.name })
        vm.onEvent(BrowseEvent.ToggleSort)
        advanceUntilIdle()
        assertEquals(BrowseSort.Alphabetical, (vm.state.value as Load.Ready).data.sort)
        job.cancel()
    }

    @Test fun failureThenRetryRecovers() = runTest(dispatcher) {
        val repo = FakeRepository(categories = cats).apply { failNext = true }
        val vm = BrowsePresenter(repo, FakeSession(), backgroundScope, dispatcher)
        vm.state.onEach { }.launchIn(backgroundScope)
        advanceUntilIdle()
        assertEquals(Load.Failed, vm.state.value)
        vm.onEvent(BrowseEvent.Retry)
        advanceUntilIdle()
        assertTrue(vm.state.value is Load.Ready)
    }
}
