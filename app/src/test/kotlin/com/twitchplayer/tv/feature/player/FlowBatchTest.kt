package com.twitchplayer.tv.feature.player

import com.twitchplayer.tv.core.presentation.batch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FlowBatchTest {
    @Test fun itemsArriveInOrderGroupedPerWindowAndQuietWindowsEmitNothing() = runTest {
        val upstream = MutableSharedFlow<Int>(extraBufferCapacity = 64)
        val batches = mutableListOf<List<Int>>()
        backgroundScope.launch { upstream.batch(100).collect { batches += it } }
        runCurrent()
        upstream.emit(1); upstream.emit(2); upstream.emit(3)
        advanceTimeBy(99); runCurrent()
        assertEquals(emptyList<List<Int>>(), batches)
        advanceTimeBy(2); runCurrent()
        assertEquals(listOf(listOf(1, 2, 3)), batches)
        advanceTimeBy(300); runCurrent()
        assertEquals("a window with no items emits nothing", 1, batches.size)
        upstream.emit(4)
        advanceTimeBy(101); runCurrent()
        assertEquals(listOf(listOf(1, 2, 3), listOf(4)), batches)
    }
}
