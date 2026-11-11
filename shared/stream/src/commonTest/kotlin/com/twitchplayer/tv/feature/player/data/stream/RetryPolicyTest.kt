package com.twitchplayer.tv.feature.player.data.stream

import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RetryPolicyTest {
    @Test fun waitsLongerBetweenAttemptsUpToTheCap() = runTest {
        val waits = mutableListOf<Long>()
        val policy = RetryPolicy(attempts = 5, initialDelayMs = 100, maxDelayMs = 300, sleep = { waits += it })
        assertFailsWith<IOException> { policy.run<Unit> { throw IOException("x") } }
        assertEquals(listOf(100L, 200L, 300L, 300L), waits)
    }

    @Test fun nonTransientErrorsSurfaceAtOnce() = runTest {
        var calls = 0
        assertFailsWith<IllegalStateException> {
            RetryPolicy(sleep = {}).run<Unit> {
                calls++
                error("no")
            }
        }
        assertEquals(1, calls)
    }

    @Test fun anHttpFailureThatIsRetryableIsRetriedButAnOtherOneIsNot() = runTest {
        var retryable = 0
        assertFailsWith<StreamAccessException> {
            RetryPolicy(attempts = 3, sleep = {}).run<Unit> {
                retryable++
                throw StreamAccessException("gql", 503, "busy", retryable = true)
            }
        }
        assertEquals(3, retryable)
        var refused = 0
        assertFailsWith<StreamAccessException> {
            RetryPolicy(attempts = 3, sleep = {}).run<Unit> {
                refused++
                throw StreamAccessException("gql", 401, "no")
            }
        }
        assertEquals(1, refused)
    }
}
