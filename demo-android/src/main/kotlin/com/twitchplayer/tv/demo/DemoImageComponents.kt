package com.twitchplayer.tv.demo

import coil3.ComponentRegistry

/**
 * Registers the fetchers that draw the demo art and emotes in code. Call it before adding the network fetcher: the demo emote
 * URLs have the shape of a `https://static-cdn.jtvnw.net` URL and must not reach the network.
 */
fun registerDemoImageComponents(builder: ComponentRegistry.Builder) {
    builder.add(DemoArtFetcher.Factory())
    builder.add(DemoEmoteFetcher.Factory())
}
