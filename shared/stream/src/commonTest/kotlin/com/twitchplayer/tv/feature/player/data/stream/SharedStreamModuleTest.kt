package com.twitchplayer.tv.feature.player.data.stream

import com.twitchplayer.tv.core.platform.AppConfig
import com.twitchplayer.tv.core.platform.Logger
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import kotlinx.coroutines.test.runTest
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertTrue

/** Exactly the bindings listed in [sharedStreamModule]'s KDoc: an `HttpClient`, a `Logger` and an `AppConfig` (its playback client id). */
private val fakePlatform = module {
    single { HttpClient(MockEngine { respondOk() }) }
    single { Logger { _, _ -> } }
    single { AppConfig(clientId = "cid", playbackClientId = "web-test-id") }
}

class SharedStreamModuleTest {
    @Test fun withoutAPlaybackClientIdTheResolversRefuseToRun() = runTest {
        val unconfigured = module { single { AppConfig(clientId = "cid") } }
        val koin = startKoin { modules(sharedStreamModule(), fakePlatform, unconfigured) }.koin
        try {
            val failure = runCatching { koin.get<TwitchPlaybackResolver>().resolve("lunaria") }.exceptionOrNull()
            assertTrue(failure is IllegalStateException && failure.message.orEmpty().contains("playbackClientId"))
        } finally {
            stopKoin()
        }
    }

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
