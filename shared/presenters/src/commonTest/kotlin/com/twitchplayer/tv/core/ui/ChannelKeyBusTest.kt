package com.twitchplayer.tv.core.ui

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ChannelKeyBusTest {
    @Test fun keysReachEveryCollectorInOrder() = runTest(UnconfinedTestDispatcher()) {
        val bus = ChannelKeyBus()
        val seen = mutableListOf<Int>()
        bus.events.onEach { seen += it }.launchIn(backgroundScope)
        bus.emit(1)
        bus.emit(-1)
        bus.emit(1)
        assertEquals(listOf(1, -1, 1), seen)
    }

    @Test fun aKeyWithNobodyListeningIsDroppedNotQueued() = runTest(UnconfinedTestDispatcher()) {
        val bus = ChannelKeyBus()
        assertTrue(bus.emit(1)) // tryEmit with spare buffer succeeds, but a SharedFlow without replay keeps nothing for late collectors
        val seen = mutableListOf<Int>()
        bus.events.onEach { seen += it }.launchIn(backgroundScope)
        assertTrue(seen.isEmpty())
    }
}
