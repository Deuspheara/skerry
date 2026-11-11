package com.twitchplayer.tv.feature.browse

import com.twitchplayer.tv.core.data.model.CategoryGroup
import com.twitchplayer.tv.core.presentation.Load
import com.twitchplayer.tv.feature.browse.presentation.BrowseFilter
import com.twitchplayer.tv.feature.browse.presentation.BrowseSort
import com.twitchplayer.tv.feature.browse.presentation.BrowseViewModel
import com.twitchplayer.tv.feature.browse.presentation.applyBrowse
import com.twitchplayer.tv.testing.FakeRepository
import com.twitchplayer.tv.testing.FakeSession
import com.twitchplayer.tv.testing.category
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BrowseViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val cats = listOf(
        category("zelda"),
        category("chat", CategoryGroup.Irl),
        category("beats", CategoryGroup.Music),
        category("alpha"),
    )

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun filterKeepsPopularityOrderAndSortIsAlphabetical() {
        assertEquals(listOf("Zelda", "Alpha"), applyBrowse(cats, BrowseFilter.Games, BrowseSort.Popularity).map { it.name })
        assertEquals(listOf("Alpha", "Zelda"), applyBrowse(cats, BrowseFilter.Games, BrowseSort.Alphabetical).map { it.name })
        assertEquals(4, applyBrowse(cats, BrowseFilter.All, BrowseSort.Popularity).size)
    }

    @Test fun stateReactsToFilterAndSort() = runTest(dispatcher) {
        val vm = BrowseViewModel(FakeRepository(categories = cats), FakeSession(), dispatcher)
        val job = vm.state.onEach { }.launchIn(backgroundScope)
        advanceUntilIdle()
        vm.selectFilter(BrowseFilter.Irl)
        advanceUntilIdle()
        val ready = vm.state.value as Load.Ready
        assertEquals(BrowseFilter.Irl, ready.data.filter)
        assertEquals(listOf("Chat"), ready.data.categories.map { it.name })
        vm.toggleSort()
        advanceUntilIdle()
        assertEquals(BrowseSort.Alphabetical, (vm.state.value as Load.Ready).data.sort)
        job.cancel()
    }

    @Test fun failureThenRetryRecovers() = runTest(dispatcher) {
        val repo = FakeRepository(categories = cats).apply { failNext = true }
        val vm = BrowseViewModel(repo, FakeSession(), dispatcher)
        vm.state.onEach { }.launchIn(backgroundScope)
        advanceUntilIdle()
        assertEquals(Load.Failed, vm.state.value)
        vm.retry()
        advanceUntilIdle()
        assertTrue(vm.state.value is Load.Ready)
    }
}
