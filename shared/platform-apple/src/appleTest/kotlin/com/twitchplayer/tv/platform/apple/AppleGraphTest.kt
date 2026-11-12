package com.twitchplayer.tv.platform.apple

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.koin.dsl.koinApplication
import kotlin.test.Test
import kotlin.test.assertNotNull

/** The Apple counterpart of Android's `ProductionGraphResolutionTest`: every presenter and playback service resolves from exactly the production module list. */
class AppleGraphTest {
    @Test fun everyPresenterAndPlaybackServiceResolvesFromTheProductionModules() {
        val koin = koinApplication { modules(appleModules(AppleConfig("test-client-id", keychainService = "com.twitchplayer.tests.graph"))) }.koin
        val graph = TwitchGraph(koin)
        val scope = CoroutineScope(Dispatchers.Default + Job())
        try {
            assertNotNull(graph.auth(scope))
            assertNotNull(graph.home(scope))
            assertNotNull(graph.follows(scope))
            assertNotNull(graph.continueWatching(scope))
            assertNotNull(graph.browse(scope))
            assertNotNull(graph.category("509658", scope))
            assertNotNull(graph.channel("somebody", scope))
            assertNotNull(graph.search(scope))
            assertNotNull(graph.settings(scope))
            assertNotNull(graph.player("somebody", scope))
            assertNotNull(graph.feed(scope))
            assertNotNull(graph.clipsFeed(scope))
            assertNotNull(graph.feedChat("1", scope))
            assertNotNull(graph.playback)
            assertNotNull(graph.foreground)
            assertNotNull(graph.tokens)
        } finally {
            scope.cancel()
            koin.close()
        }
    }
}
