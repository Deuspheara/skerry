package com.twitchplayer.tv.core.presentation

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Groups upstream items into lists emitted at most once per [windowMs], so bursts cause one UI update. */
fun <T> Flow<T>.batch(windowMs: Long): Flow<List<T>> = channelFlow {
    val buffer = ArrayList<T>()
    val lock = Mutex()
    val flusher = launch {
        while (true) {
            delay(windowMs)
            lock.withLock {
                if (buffer.isNotEmpty()) {
                    send(buffer.toList())
                    buffer.clear()
                }
            }
        }
    }
    try {
        collect { item -> lock.withLock { buffer.add(item) } }
    } finally {
        flusher.cancel()
    }
}
