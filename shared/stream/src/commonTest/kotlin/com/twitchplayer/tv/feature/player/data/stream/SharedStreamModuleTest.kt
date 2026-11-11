package com.twitchplayer.tv.feature.player.data.stream

import com.twitchplayer.tv.core.platform.Logger
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertTrue

/** Exactly the bindings listed in [sharedStreamModule]'s KDoc: an `HttpClient` and a `Logger`. */
private val fakePlatform = module {
    single { HttpClient(MockEngine { respondOk() }) }
    single { Logger { _, _ -> } }
}

class SharedStreamModuleTest {
    @Test fun everyResolverResolvesAgainstTheDocumentedPlatformBindings() {
        val koin = startKoin { modules(sharedStreamModule(), fakePlatform) }.koin
        try {
            assertTrue(koin.get<TwitchPlaybackResolver>() is GqlPlaybackResolver)
            assertTrue(koin.get<VodResolver>() is GqlVodResolver)
            assertTrue(koin.get<ClipResolver>() is GqlClipResolver)
        } finally {
            stopKoin()
        }
    }
}
