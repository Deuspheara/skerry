package com.twitchplayer.tv.feature.player.data

import io.ktor.client.HttpClient

/**
 * Builds the `HttpClient` that reads 7TV's server-sent event stream. It must not time out while the stream is quiet
 * (the server heartbeats about every 46 s), which is an engine setting, so each platform builds it
 * (OkHttp with `readTimeout(0)` on Android). Called once per followed channel; the caller closes the client.
 */
fun interface EventStreamClientFactory {
    fun create(): HttpClient
}
