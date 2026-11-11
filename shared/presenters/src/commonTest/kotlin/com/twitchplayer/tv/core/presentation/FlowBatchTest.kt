package com.twitchplayer.tv.core.presentation

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class FlowBatchTest {
    @Test fun itemsArriveInOrderGroupedPerWindowAndQuietWindowsEmitNothing() = runTest {
        val upstream = MutableSharedFlow<Int>(extraBufferCapacity = 64)
        val batches = mutableListOf<List<Int>>()
        backgroundScope.launch { upstream.batch(100).collect { batches += it } }
        runCurrent()
        upstream.emit(1)
        upstream.emit(2)
        upstream.emit(3)
        advanceTimeBy(99)
        runCurrent()
        assertEquals(emptyList<List<Int>>(), batches)
        advanceTimeBy(2)
        runCurrent()
        assertEquals(listOf(listOf(1, 2, 3)), batches)
        advanceTimeBy(300)
        runCurrent()
        assertEquals(1, batches.size, "a window with no items emits nothing")
        upstream.emit(4)
        advanceTimeBy(101)
        runCurrent()
        assertEquals(listOf(listOf(1, 2, 3), listOf(4)), batches)
    }
}
