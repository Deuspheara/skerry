package com.twitchplayer.tv.feature.search.presentation

import com.twitchplayer.tv.core.data.model.ChannelRef
import com.twitchplayer.tv.core.data.model.SearchResults
import com.twitchplayer.tv.testing.FakeRepository
import com.twitchplayer.tv.testing.FakeSettings
import com.twitchplayer.tv.testing.category
import com.twitchplayer.tv.testing.stream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SearchPresenterTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val repo = FakeRepository(live = listOf(stream("a", 900), stream("b", 800)))

    private class FakeVoice(var available: Boolean = true) : VoiceInput {
        val heard = CompletableDeferred<String?>()
        override fun isAvailable() = available
        override suspend fun listen(): String? = heard.await()
    }

    private fun TestScope.presenter(voice: VoiceInput = FakeVoice(), settings: FakeSettings = FakeSettings()) = SearchPresenter(repo, settings, voice, backgroundScope, debounceMs = 300).also { it.state.onEach { }.launchIn(backgroundScope) }

    @Test fun emptyQueryShowsTrends() = runTest(dispatcher) {
        val vm = presenter()
        advanceTimeBy(1)
        runCurrent()
        val results = vm.state.value.results as ResultsUi.Trends
        assertEquals(listOf("a", "b"), results.channels.map { it.id })
        assertEquals(false, results.noResult)
    }

    @Test fun keyboardEditsTheQuery() = runTest(dispatcher) {
        val vm = presenter()
        vm.onEvent(SearchEvent.Append("e"))
        vm.onEvent(SearchEvent.Append("c"))
        vm.onEvent(SearchEvent.Space)
        vm.onEvent(SearchEvent.Space)
        vm.onEvent(SearchEvent.Append("h"))
        assertEquals("ec h", vm.state.value.query)
        vm.onEvent(SearchEvent.Delete)
        assertEquals("ec ", vm.state.value.query)
        vm.onEvent(SearchEvent.Clear)
        assertEquals("", vm.state.value.query)
    }

    @Test fun typingIsDebouncedIntoOneSearch() = runTest(dispatcher) {
        repo.searchResult = SearchResults(listOf(ChannelRef("1", "one", "One", null, true, 5)), listOf(category("one")))
        val vm = presenter()
        vm.onEvent(SearchEvent.Append("o"))
        advanceTimeBy(100)
        vm.onEvent(SearchEvent.Append("n"))
        advanceTimeBy(100)
        vm.onEvent(SearchEvent.Append("e"))
        advanceTimeBy(299)
        runCurrent()
        assertTrue(repo.searchCalls.isEmpty())
        advanceTimeBy(2)
        runCurrent()
        assertEquals(listOf("one"), repo.searchCalls)
        assertTrue(vm.state.value.results is ResultsUi.Found)
    }

    @Test fun noMatchFallsBackToTrendsFlagged() = runTest(dispatcher) {
        val vm = presenter()
        vm.onEvent(SearchEvent.SetQuery("zzz"))
        advanceTimeBy(400)
        runCurrent()
        val results = vm.state.value.results as ResultsUi.Trends
        assertTrue(results.noResult)
        assertEquals(2, results.channels.size)
    }

    @Test fun voiceHeardFillsQueryAndRecents() = runTest(dispatcher) {
        val voice = FakeVoice()
        val settings = FakeSettings()
        val vm = presenter(voice, settings)
        vm.onEvent(SearchEvent.StartVoice)
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
        val vm = presenter(voice)
        vm.onEvent(SearchEvent.StartVoice)
        runCurrent()
        assertEquals(VoiceState.Failed, vm.state.value.voice)
        advanceTimeBy(1_201)
        runCurrent()
        assertEquals(VoiceState.Idle, vm.state.value.voice)
        assertEquals("", vm.state.value.query)
    }

    @Test fun unavailableRecognizerFailsImmediatelyAndCancelResets() = runTest(dispatcher) {
        val vm = presenter(FakeVoice(available = false))
        vm.onEvent(SearchEvent.StartVoice)
        assertEquals(VoiceState.Failed, vm.state.value.voice)
        vm.onEvent(SearchEvent.CancelVoice)
        assertEquals(VoiceState.Idle, vm.state.value.voice)
    }

    @Test fun recentsAreDedupedAndCappedAtFour() = runTest(dispatcher) {
        val settings = FakeSettings()
        val vm = presenter(settings = settings)
        listOf("a", "b", "c", "d", "e", "b").forEach {
            vm.onEvent(SearchEvent.SetQuery(it))
            vm.onEvent(SearchEvent.CommitQuery)
        }
        assertEquals(listOf("b", "e", "d", "c"), vm.state.value.recents)
    }
}
