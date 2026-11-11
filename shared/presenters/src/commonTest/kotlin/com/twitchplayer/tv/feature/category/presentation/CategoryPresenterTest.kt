package com.twitchplayer.tv.feature.category.presentation

import com.twitchplayer.tv.core.data.model.LiveStream
import com.twitchplayer.tv.core.data.repository.TwitchRepository
import com.twitchplayer.tv.core.platform.DeviceProfile
import com.twitchplayer.tv.core.presentation.Load
import com.twitchplayer.tv.testing.FakeRepository
import com.twitchplayer.tv.testing.FakeSession
import com.twitchplayer.tv.testing.FakeSettings
import com.twitchplayer.tv.testing.stream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CategoryPresenterTest {
    private val dispatcher = UnconfinedTestDispatcher()

    private fun device(tag: String) = object : DeviceProfile {
        override val languageTag = tag
    }

    /** Records the language each request asked for. */
    private class Repo : TwitchRepository by FakeRepository(live = listOf(stream("a", category = "cat"))) {
        val languages = mutableListOf<String?>()
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun categoryStreams(categoryId: String, language: String?): List<LiveStream> {
            languages += language
            gate?.await()
            return listOf(stream("a", category = "cat"))
        }
    }

    @Test fun twitchLanguageMapsTagsToTwitchCodes() {
        assertEquals("fr", twitchLanguage("fr-FR"))
        assertEquals("en", twitchLanguage("en"))
        assertEquals("no", twitchLanguage("nb-NO"))
        assertEquals("no", twitchLanguage("nn"))
        assertEquals("zh-hk", twitchLanguage("zh-HK"))
        assertEquals("zh-hk", twitchLanguage("zh-Hant-HK"))
        assertEquals("zh", twitchLanguage("zh-TW"))
        assertEquals("zh", twitchLanguage("zh-Hans"))
        assertEquals("tl", twitchLanguage("fil"))
        assertEquals("tl", twitchLanguage("fil-PH"))
        assertEquals("id", twitchLanguage("in-ID"))
        assertEquals("id", twitchLanguage("id"))
        assertEquals("he", twitchLanguage("iw-IL"))
        assertEquals("pt", twitchLanguage("pt_BR"))
        assertEquals("FR".lowercase(), twitchLanguage("FR"))
        assertEquals("", twitchLanguage(""), "an empty tag stays empty, as `Locale.ROOT.language` did")
    }

    @Test fun startsNarrowedToTheDeviceLanguageAndTheToggleReloadsWithoutIt() = runTest(dispatcher) {
        val repo = Repo()
        val presenter = CategoryPresenter("cat", repo, FakeSession(), FakeSettings(), device("fr-FR"), backgroundScope, dispatcher)
        presenter.state.onEach { }.launchIn(backgroundScope)
        advanceUntilIdle()

        assertEquals("fr", presenter.state.value.language)
        assertTrue(presenter.state.value.languageOnly)
        assertEquals(listOf<String?>("fr"), repo.languages)

        presenter.onEvent(CategoryEvent.ToggleLanguage)
        advanceUntilIdle()
        assertFalse(presenter.state.value.languageOnly)
        assertEquals(listOf("fr", null), repo.languages)

        presenter.onEvent(CategoryEvent.ToggleLanguage)
        advanceUntilIdle()
        assertEquals(listOf("fr", null, "fr"), repo.languages)
    }

    @Test fun theLanguageIsKnownBeforeTheStreamsLoad() = runTest(dispatcher) {
        val repo = Repo().apply { gate = CompletableDeferred() }
        val presenter = CategoryPresenter("cat", repo, FakeSession(), FakeSettings(), device("de-DE"), backgroundScope, dispatcher)
        presenter.state.onEach { }.launchIn(backgroundScope)
        assertEquals(CategoryScreenState(Load.Loading, "de", true), presenter.state.value)
        repo.gate!!.complete(Unit)
        advanceUntilIdle()
        assertIs<Load.Ready<CategoryUiState>>(presenter.state.value.load)
    }

    @Test fun favoriteFollowsTheSettingsAndToggles() = runTest(dispatcher) {
        val settings = FakeSettings()
        val presenter = CategoryPresenter("cat", Repo(), FakeSession(), settings, device("en-US"), backgroundScope, dispatcher)
        presenter.state.onEach { }.launchIn(backgroundScope)
        advanceUntilIdle()
        assertFalse(assertIs<Load.Ready<CategoryUiState>>(presenter.state.value.load).data.favorite)

        presenter.onEvent(CategoryEvent.ToggleFavorite)
        advanceUntilIdle()
        assertTrue(assertIs<Load.Ready<CategoryUiState>>(presenter.state.value.load).data.favorite)
        assertTrue("cat" in settings.settings.value.followedCategories)

        presenter.onEvent(CategoryEvent.ToggleFavorite)
        advanceUntilIdle()
        assertFalse(assertIs<Load.Ready<CategoryUiState>>(presenter.state.value.load).data.favorite)
    }

    @Test fun aFailedLoadIsShownAndRetryReloads() = runTest(dispatcher) {
        val repo = FakeRepository(live = listOf(stream("a", category = "cat"))).apply { failNext = true }
        val presenter = CategoryPresenter("cat", repo, FakeSession(), FakeSettings(), device("en"), backgroundScope, dispatcher)
        presenter.state.onEach { }.launchIn(backgroundScope)
        advanceUntilIdle()
        assertEquals(Load.Failed, presenter.state.value.load)

        presenter.onEvent(CategoryEvent.Retry)
        advanceUntilIdle()
        assertIs<Load.Ready<CategoryUiState>>(presenter.state.value.load)
    }
}
