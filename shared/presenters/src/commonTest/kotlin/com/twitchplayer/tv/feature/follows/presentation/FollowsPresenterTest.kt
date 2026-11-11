package com.twitchplayer.tv.feature.follows.presentation

import com.twitchplayer.tv.core.data.model.ChannelRef
import com.twitchplayer.tv.core.presentation.Load
import com.twitchplayer.tv.testing.FakeRepository
import com.twitchplayer.tv.testing.FakeSession
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

@OptIn(ExperimentalCoroutinesApi::class)
class FollowsPresenterTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Test fun splitsLiveAndOfflineChannels() = runTest(dispatcher) {
        val repo = FakeRepository(live = listOf(stream("live")), offline = listOf(ChannelRef("9", "off", "Off", null, false, 0)))
        val follows = FollowsPresenter(repo, FakeSession(), backgroundScope, dispatcher)
        follows.state.onEach { }.launchIn(backgroundScope)
        advanceUntilIdle()

        val ready = assertIs<Load.Ready<FollowsContent>>(follows.state.value)
        assertEquals(listOf("live"), ready.data.live.map { it.channelId })
        assertEquals(listOf("off"), ready.data.offline.map { it.login })
    }

    @Test fun failureThenRetryRecovers() = runTest(dispatcher) {
        val repo = FakeRepository(live = listOf(stream("live"))).apply { failNext = true }
        val follows = FollowsPresenter(repo, FakeSession(), backgroundScope, dispatcher)
        follows.state.onEach { }.launchIn(backgroundScope)
        advanceUntilIdle()
        assertEquals(Load.Failed, follows.state.value)

        follows.onEvent(FollowsEvent.Retry)
        advanceUntilIdle()
        assertIs<Load.Ready<FollowsContent>>(follows.state.value)
    }
}
