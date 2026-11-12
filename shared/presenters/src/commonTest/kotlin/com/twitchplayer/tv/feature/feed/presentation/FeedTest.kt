package com.twitchplayer.tv.feature.feed.presentation

import com.twitchplayer.tv.core.data.model.FeedAutoplay
import com.twitchplayer.tv.core.data.model.Follows
import com.twitchplayer.tv.core.data.model.LiveStream
import com.twitchplayer.tv.core.data.repository.TwitchRepository
import com.twitchplayer.tv.core.platform.DeviceProfile
import com.twitchplayer.tv.core.platform.NetworkStatus
import com.twitchplayer.tv.core.presentation.Load
import com.twitchplayer.tv.testing.FakeRepository
import com.twitchplayer.tv.testing.FakeSession
import com.twitchplayer.tv.testing.FakeSettings
import com.twitchplayer.tv.testing.stream
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

private fun live(id: String, viewers: Int, language: String = "") = stream(id, viewers).copy(language = language)

class FeedRankingTest {
    @Test fun followedChannelsLeadThenTheBusiestStreamsInTheViewersLanguage() {
        val ranked = FeedRanking.rank(
            followed = listOf(live("f1", 50), live("f2", 500)),
            top = listOf(live("t1", 9_000, "en"), live("t2", 8_000, "fr"), live("t3", 7_000, "")),
            language = "fr",
        )
        assertEquals(listOf("f2", "f1", "t2", "t3"), ranked.map { it.channelId }, "English is left out, an unknown language stays")
    }

    @Test fun aChannelThatIsBothFollowedAndTopAppearsOnce() {
        val ranked = FeedRanking.rank(listOf(live("a", 10)), listOf(live("a", 10), live("b", 5)), language = null)
        assertEquals(listOf("a", "b"), ranked.map { it.channelId })
    }

    @Test fun noLanguageMeansEveryLanguage() {
        val ranked = FeedRanking.rank(emptyList(), listOf(live("a", 5, "en"), live("b", 4, "ja")), language = "")
        assertEquals(2, ranked.size)
    }

    @Test fun onlyTheFirstFollowedChannelsLeadSoTheFeedStaysFresh() {
        val followed = (1..12).map { live("f$it", 1_000 - it) }
        val ranked = FeedRanking.rank(followed, listOf(live("t", 1)), language = null)
        assertEquals(FeedRanking.MAX_FOLLOWED + 1, ranked.size, "the other four followed channels are not in the feed")
        assertEquals("t", ranked.last().channelId)
    }

    @Test fun channelsAlreadySeenGoToTheEnd() {
        val ranked = FeedRanking.rank(listOf(live("f", 9)), listOf(live("a", 5), live("b", 4)), language = null, seen = setOf("f", "a"))
        assertEquals(listOf("b", "f", "a"), ranked.map { it.channelId })
    }

    @Test fun theFeedIsBounded() {
        val top = (1..100).map { live("t$it", it) }
        assertEquals(FeedRanking.LIMIT, FeedRanking.rank(emptyList(), top, language = null).size)
    }

    @Test fun autoplayFollowsTheChoiceAndTheConnection() {
        assertTrue(FeedAutoplayPolicy.allowed(FeedAutoplay.Always, metered = true))
        assertTrue(FeedAutoplayPolicy.allowed(FeedAutoplay.WifiOnly, metered = false))
        assertFalse(FeedAutoplayPolicy.allowed(FeedAutoplay.WifiOnly, metered = true))
        assertFalse(FeedAutoplayPolicy.allowed(FeedAutoplay.Off, metered = false))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class FeedPresenterTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val french = object : DeviceProfile {
        override val languageTag = "fr-FR"
    }

    private class Network(initial: Boolean) : NetworkStatus {
        val flow = MutableStateFlow(initial)
        override val metered: StateFlow<Boolean> = flow
    }

    private class Repo(private val follow: List<LiveStream>, private val top: List<LiveStream>, base: FakeRepository = FakeRepository()) : TwitchRepository by base {
        var failNext = false
        override suspend fun follows(force: Boolean) = Follows(follow, emptyList())
        override suspend fun recommended(): List<LiveStream> {
            if (failNext) {
                failNext = false
                error("boom")
            }
            return top
        }
    }

    private fun repo(follow: List<LiveStream>, top: List<LiveStream>) = Repo(follow, top)

    @Test fun theFeedIsFollowedLiveChannelsThenTheBusiestInTheViewersLanguage() = runTest(dispatcher) {
        val presenter = FeedPresenter(repo(listOf(live("f", 5)), listOf(live("t1", 90, "fr"), live("t2", 80, "en"))), FakeSession(), FakeSettings(), Network(false), french, backgroundScope, dispatcher)
        presenter.state.onEach { }.launchIn(backgroundScope)
        advanceUntilIdle()
        assertIs<Load.Ready<FeedContent>>(presenter.state.value.load)
        assertEquals(listOf("f", "t1"), presenter.state.value.streams.map { it.channelId })
    }

    @Test fun autoplayFollowsTheSettingAndTheConnectionLive() = runTest(dispatcher) {
        val network = Network(true)
        val settings = FakeSettings()
        val presenter = FeedPresenter(repo(emptyList(), listOf(live("t", 1))), FakeSession(), settings, network, french, backgroundScope, dispatcher)
        presenter.state.onEach { }.launchIn(backgroundScope)
        advanceUntilIdle()
        assertFalse(presenter.state.value.autoplayAllowed, "Wi-Fi only on mobile data: off")
        network.flow.value = false
        advanceUntilIdle()
        assertTrue(presenter.state.value.autoplayAllowed, "joined a Wi-Fi: on")
        settings.setFeedAutoplay(FeedAutoplay.Off)
        advanceUntilIdle()
        assertFalse(presenter.state.value.autoplayAllowed)
        assertEquals(FeedAutoplay.Off, presenter.state.value.autoplay)
    }

    @Test fun settlingOnAPageRecordsItAndASlowRefreshDoesNotBringItBackFirst() = runTest(dispatcher) {
        val presenter = FeedPresenter(repo(emptyList(), listOf(live("a", 9), live("b", 8))), FakeSession(), FakeSettings(), Network(false), french, backgroundScope, dispatcher)
        presenter.state.onEach { }.launchIn(backgroundScope)
        advanceUntilIdle()
        presenter.onEvent(FeedEvent.Settled("a"))
        assertEquals("a", presenter.state.value.settledLogin)
        presenter.onEvent(FeedEvent.Retry)
        advanceUntilIdle()
        assertEquals(listOf("b", "a"), presenter.state.value.streams.map { it.channelId })
    }

    @Test fun aFailedLoadWithNothingToShowIsFailedAndRetryRecovers() = runTest(dispatcher) {
        val repository = repo(emptyList(), emptyList()).apply { failNext = true }
        val presenter = FeedPresenter(repository, FakeSession(), FakeSettings(), Network(false), french, backgroundScope, dispatcher)
        presenter.state.onEach { }.launchIn(backgroundScope)
        advanceUntilIdle()
        assertEquals(Load.Failed, presenter.state.value.load)
        presenter.onEvent(FeedEvent.Retry)
        advanceUntilIdle()
        assertIs<Load.Ready<FeedContent>>(presenter.state.value.load)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ClipsFeedPresenterTest {
    private val dispatcher = UnconfinedTestDispatcher()

    private fun clip(slug: String) = com.twitchplayer.tv.core.data.model.FeedClip(slug, "title $slug", "", 20, 100, "c", "chan", "Chan", null)

    private class Repo(val clips: List<com.twitchplayer.tv.core.data.model.FeedClip>, base: FakeRepository = FakeRepository()) : TwitchRepository by base {
        var fail = false
        override suspend fun feedClips(): List<com.twitchplayer.tv.core.data.model.FeedClip> {
            if (fail) {
                fail = false
                error("boom")
            }
            return clips
        }
    }

    private class Net(initial: Boolean) : NetworkStatus {
        val flow = MutableStateFlow(initial)
        override val metered: StateFlow<Boolean> = flow
    }

    @Test fun theClipsLoadInTheOrderTheRepositoryGivesThem() = runTest(dispatcher) {
        val presenter = ClipsFeedPresenter(Repo(listOf(clip("a"), clip("b"))), FakeSession(), FakeSettings(), Net(false), backgroundScope, dispatcher)
        presenter.state.onEach { }.launchIn(backgroundScope)
        advanceUntilIdle()
        assertEquals(listOf("a", "b"), presenter.state.value.clips.map { it.slug })
        assertTrue(presenter.state.value.autoplayAllowed)
    }

    @Test fun autoplayIsOffOnMobileDataByDefault() = runTest(dispatcher) {
        val presenter = ClipsFeedPresenter(Repo(listOf(clip("a"))), FakeSession(), FakeSettings(), Net(true), backgroundScope, dispatcher)
        presenter.state.onEach { }.launchIn(backgroundScope)
        advanceUntilIdle()
        assertFalse(presenter.state.value.autoplayAllowed)
    }

    @Test fun aFailureThenRetryRecovers() = runTest(dispatcher) {
        val repo = Repo(listOf(clip("a"))).apply { fail = true }
        val presenter = ClipsFeedPresenter(repo, FakeSession(), FakeSettings(), Net(false), backgroundScope, dispatcher)
        presenter.state.onEach { }.launchIn(backgroundScope)
        advanceUntilIdle()
        assertEquals(Load.Failed, presenter.state.value.load)
        presenter.retry()
        advanceUntilIdle()
        assertIs<Load.Ready<ClipsContent>>(presenter.state.value.load)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class FeedChatPresenterTest {
    private fun message(id: String) = com.twitchplayer.tv.feature.player.data.chat.ChatEvent.Message(
        com.twitchplayer.tv.feature.player.data.chat.ChatMessage(
            id,
            "b",
            "u",
            "Nick",
            "nick",
            com.twitchplayer.tv.feature.player.data.chat.ChatMessageContent("hi $id", listOf(com.twitchplayer.tv.feature.player.data.chat.ChatFragment("text", "hi $id"))),
        ),
    )

    private val events = kotlinx.coroutines.flow.MutableSharedFlow<com.twitchplayer.tv.feature.player.data.chat.ChatEvent>(extraBufferCapacity = 64)
    private val connections = mutableListOf<Pair<String, String>>()
    private var liteCalls = 0
    private val source = com.twitchplayer.tv.feature.player.data.ChatSource { channel, user ->
        connections += channel to user
        events
    }
    private val lite = com.twitchplayer.tv.feature.player.data.LiteChatAssets {
        liteCalls++
        kotlinx.coroutines.flow.flowOf(com.twitchplayer.tv.feature.player.data.ChatAssets.Empty)
    }

    @Test fun itReadsOneChannelWithTheLightAssetsAndKeepsOnlyTheNewestLines() = runTest {
        val dispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)
        val presenter = FeedChatPresenter("chan-1", FakeSession(), FakeSettings(), source, lite, backgroundScope, dispatcher)
        runCurrent()
        advanceTimeBy(10)
        runCurrent()
        assertEquals(listOf("chan-1" to "me"), connections)
        assertEquals(1, liteCalls, "the light assets, once")
        repeat(30) { events.emit(message("m$it")) }
        advanceTimeBy(300)
        runCurrent()
        assertEquals(12, presenter.lines.value.size)
        assertEquals("m29", presenter.lines.value.last().id)
    }

    @Test fun signedOutItNeverConnects() = runTest {
        val dispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)
        FeedChatPresenter("chan-1", FakeSession(com.twitchplayer.tv.core.data.repository.SessionState.SignedOut), FakeSettings(), source, lite, backgroundScope, dispatcher)
        runCurrent()
        advanceTimeBy(500)
        runCurrent()
        assertTrue(connections.isEmpty())
    }

    @Test fun itFollowsTheViewersChatLook() = runTest {
        val dispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)
        val settings = FakeSettings()
        val presenter = FeedChatPresenter("chan-1", FakeSession(), settings, source, lite, backgroundScope, dispatcher)
        runCurrent()
        settings.updateChat { it.copy(showBadges = false) }
        runCurrent()
        assertFalse(presenter.chatSettings.value.showBadges)
    }
}
