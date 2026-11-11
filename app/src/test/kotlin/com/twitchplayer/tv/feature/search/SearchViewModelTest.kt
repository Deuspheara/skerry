package com.twitchplayer.tv.feature.search

import com.twitchplayer.tv.core.data.model.ChannelRef
import com.twitchplayer.tv.core.data.model.SearchResults
import com.twitchplayer.tv.feature.search.data.VoiceRecognizer
import com.twitchplayer.tv.feature.search.presentation.ResultsUi
import com.twitchplayer.tv.feature.search.presentation.SearchViewModel
import com.twitchplayer.tv.feature.search.presentation.VoiceState
import com.twitchplayer.tv.testing.FakeRepository
import com.twitchplayer.tv.testing.FakeSettings
import com.twitchplayer.tv.testing.category
import com.twitchplayer.tv.testing.stream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val repo = FakeRepository(live = listOf(stream("a", 900), stream("b", 800)))

    private class FakeVoice(var available: Boolean = true) : VoiceRecognizer {
        val heard = CompletableDeferred<String?>()
        override fun isAvailable() = available
        override suspend fun listen(): String? = heard.await()
    }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(voice: VoiceRecognizer = FakeVoice(), settings: FakeSettings = FakeSettings()) = SearchViewModel(repo, settings, voice, debounceMs = 300).also { it.state.onEach { }.launchIn(backgroundScope) }

    @Test fun emptyQueryShowsTrends() = runTest(dispatcher) {
        val vm = viewModel()
        advanceTimeBy(1)
        runCurrent()
        val results = vm.state.value.results as ResultsUi.Trends
        assertEquals(listOf("a", "b"), results.channels.map { it.id })
        assertEquals(false, results.noResult)
    }

    @Test fun keyboardEditsTheQuery() = runTest(dispatcher) {
        val vm = viewModel()
        vm.append("e")
        vm.append("c")
        vm.space()
        vm.space()
        vm.append("h")
        assertEquals("ec h", vm.state.value.query)
        vm.delete()
        assertEquals("ec ", vm.state.value.query)
        vm.clear()
        assertEquals("", vm.state.value.query)
    }

    @Test fun typingIsDebouncedIntoOneSearch() = runTest(dispatcher) {
        repo.searchResult = SearchResults(listOf(ChannelRef("1", "one", "One", null, true, 5)), listOf(category("one")))
        val vm = viewModel()
        vm.append("o")
        advanceTimeBy(100)
        vm.append("n")
        advanceTimeBy(100)
        vm.append("e")
        advanceTimeBy(299)
        runCurrent()
        assertTrue(repo.searchCalls.isEmpty())
        advanceTimeBy(2)
        runCurrent()
        assertEquals(listOf("one"), repo.searchCalls)
        assertTrue(vm.state.value.results is ResultsUi.Found)
    }

    @Test fun noMatchFallsBackToTrendsFlagged() = runTest(dispatcher) {
        val vm = viewModel()
        vm.setQuery("zzz")
        advanceTimeBy(400)
        runCurrent()
        val results = vm.state.value.results as ResultsUi.Trends
        assertTrue(results.noResult)
        assertEquals(2, results.channels.size)
    }

    @Test fun voiceHeardFillsQueryAndRecents() = runTest(dispatcher) {
        val voice = FakeVoice()
        val settings = FakeSettings()
        val vm = viewModel(voice, settings)
        vm.startVoice()
        assertEquals(VoiceState.Listening, vm.state.value.voice)
        voice.heard.complete("échecs")
        runCurrent()
        assertEquals(VoiceState.Heard("échecs"), vm.state.value.voice)
        advanceTimeBy(1_001)
        runCurrent()
        assertEquals("échecs", vm.state.value.query)
        assertEquals(VoiceState.Idle, vm.state.value.voice)
        assertEquals(listOf("échecs"), vm.state.value.recents)
    }

    @Test fun voiceFailureShowsErrorThenCloses() = runTest(dispatcher) {
        val voice = FakeVoice().apply { heard.complete(null) }
        val vm = viewModel(voice)
        vm.startVoice()
        runCurrent()
        assertEquals(VoiceState.Failed, vm.state.value.voice)
        advanceTimeBy(1_201)
        runCurrent()
        assertEquals(VoiceState.Idle, vm.state.value.voice)
        assertEquals("", vm.state.value.query)
    }

    @Test fun unavailableRecognizerFailsImmediatelyAndCancelResets() = runTest(dispatcher) {
        val vm = viewModel(FakeVoice(available = false))
        vm.startVoice()
        assertEquals(VoiceState.Failed, vm.state.value.voice)
        vm.cancelVoice()
        assertEquals(VoiceState.Idle, vm.state.value.voice)
    }

    @Test fun recentsAreDedupedAndCappedAtFour() = runTest(dispatcher) {
        val settings = FakeSettings()
        val vm = viewModel(settings = settings)
        listOf("a", "b", "c", "d", "e", "b").forEach {
            vm.setQuery(it)
            vm.commitQuery()
        }
        assertEquals(listOf("b", "e", "d", "c"), vm.state.value.recents)
    }
}
