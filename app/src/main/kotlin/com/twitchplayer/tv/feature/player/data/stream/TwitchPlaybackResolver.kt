package com.twitchplayer.tv.feature.player.data.stream

/**
 * Turns a channel login into something a native player can play. The Twitch implementation talks to gql + usher
 * directly from the device; a server-side one (your own backend that returns the same [ResolvedStream]) can replace it
 * behind this interface without touching the player.
 *
 * Throws [ChannelOfflineException] when the channel is not live, [StreamAccessException] when Twitch refuses, and
 * `java.io.IOException` once the network stayed down through all retries.
 */
interface TwitchPlaybackResolver {
    suspend fun resolve(channel: String): ResolvedStream
}
