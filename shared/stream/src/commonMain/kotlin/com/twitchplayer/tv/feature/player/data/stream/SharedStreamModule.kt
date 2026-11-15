package com.twitchplayer.tv.feature.player.data.stream

import com.twitchplayer.tv.core.platform.AppConfig
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The platform-independent bindings of `:shared:stream`. Needs from the app: an `HttpClient` (provided by
 * `sharedCoreModule()`) and a [com.twitchplayer.tv.core.platform.Logger]. The usher / gql endpoints are the
 * resolvers' defaults; the client id comes from [com.twitchplayer.tv.core.platform.AppConfig.playbackClientId]. Swap the `TwitchPlaybackResolver` binding for a client of your own
 * server to resolve streams elsewhere.
 */
fun sharedStreamModule(): Module = module {
    single<TwitchPlaybackResolver> { GqlPlaybackResolver(http = get(), logger = get(), clientId = get<AppConfig>().playbackClientId) }
    single<VodResolver> { GqlVodResolver(http = get(), logger = get(), clientId = get<AppConfig>().playbackClientId) }
    single<ClipResolver> { GqlClipResolver(http = get(), logger = get(), clientId = get<AppConfig>().playbackClientId) }
}
