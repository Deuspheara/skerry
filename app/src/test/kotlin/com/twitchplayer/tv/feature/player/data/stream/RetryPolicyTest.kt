package com.twitchplayer.tv.feature.player.data.stream

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class RetryPolicyTest {
    @Test fun waitsLongerBetweenAttemptsUpToTheCap() = runTest {
        val waits = mutableListOf<Long>()
        val policy = RetryPolicy(attempts = 5, initialDelayMs = 100, maxDelayMs = 300, sleep = { waits += it })
        assertThrows(IOException::class.java) { kotlinx.coroutines.runBlocking { policy.run<Unit> { throw IOException("x") } } }
        assertEquals(listOf(100L, 200L, 300L, 300L), waits)
    }

    @Test fun nonTransientErrorsSurfaceAtOnce() = runTest {
        var calls = 0
        assertThrows(IllegalStateException::class.java) { kotlinx.coroutines.runBlocking { RetryPolicy(sleep = {}).run<Unit> { calls++; error("no") } } }
        assertEquals(1, calls)
    }
}
