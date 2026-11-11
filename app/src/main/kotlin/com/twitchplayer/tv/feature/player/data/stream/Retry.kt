package com.twitchplayer.tv.feature.player.data.stream

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.io.IOException

/** Exponential backoff for transient failures only: network errors and 5xx / 429 answers. Everything else surfaces at once. */
class RetryPolicy(
    val attempts: Int = 4,
    val initialDelayMs: Long = 400,
    val maxDelayMs: Long = 4_000,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    suspend fun <T> run(block: suspend () -> T): T {
        var wait = initialDelayMs
        var last: Exception? = null
        repeat(attempts) { attempt ->
            try {
                return block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!isTransient(e)) throw e
                last = e
                if (attempt < attempts - 1) {
                    sleep(wait)
                    wait = (wait * 2).coerceAtMost(maxDelayMs)
                }
            }
        }
        throw last!!
    }

    companion object {
        fun isTransient(e: Exception): Boolean = e is IOException || (e is StreamAccessException && e.retryable)
    }
}
