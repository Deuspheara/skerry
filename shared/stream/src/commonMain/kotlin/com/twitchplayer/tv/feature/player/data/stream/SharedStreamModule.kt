package com.twitchplayer.tv.feature.player.data.stream

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The platform-independent bindings of `:shared:stream`. Needs from the app: an `HttpClient` (provided by
 * `sharedCoreModule()`) and a [com.twitchplayer.tv.core.platform.Logger]. The usher / gql endpoints and the web
 * player client id are the resolvers' defaults. Swap the `TwitchPlaybackResolver` binding for a client of your own
 * server to resolve streams elsewhere.
 */
fun sharedStreamModule(): Module = module {
    single<TwitchPlaybackResolver> { GqlPlaybackResolver(http = get(), logger = get()) }
    single<VodResolver> { GqlVodResolver(http = get(), logger = get()) }
    single<ClipResolver> { GqlClipResolver(http = get(), logger = get()) }
}
