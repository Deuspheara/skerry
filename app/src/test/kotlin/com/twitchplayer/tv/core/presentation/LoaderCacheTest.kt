package com.twitchplayer.tv.core.presentation

import com.twitchplayer.tv.testing.FakeSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoaderCacheTest {
    private val dispatcher = UnconfinedTestDispatcher()

    private class MemoryCache(var stored: Map<String, String> = emptyMap()) {
        val cache = LoadCache<String>(read = { stored[it] }, write = { user, data -> stored = stored + (user to data) })
    }

    @Test fun showsTheCachedCopyThenTheFreshOneAndStoresIt() = runTest(dispatcher) {
        val memory = MemoryCache(mapOf("me" to "old"))
        val fresh = CompletableDeferred<String>()
        val loader = Loader(backgroundScope, FakeSession(), dispatcher, memory.cache) { fresh.await() }
        loader.state.onEach { }.launchIn(backgroundScope)
        assertEquals(Load.Ready("old"), loader.state.value)
        fresh.complete("new")
        assertEquals(Load.Ready("new"), loader.state.value)
        assertEquals("new", memory.stored["me"])
    }

    @Test fun aFailedLoadKeepsTheCachedCopy() = runTest(dispatcher) {
        val loader = Loader<String>(backgroundScope, FakeSession(), dispatcher, MemoryCache(mapOf("me" to "old")).cache) { error("offline") }
        loader.state.onEach { }.launchIn(backgroundScope)
        assertEquals(Load.Ready("old"), loader.state.value)
    }

    @Test fun anotherAccountsCopyIsNeverShown() = runTest(dispatcher) {
        val loader = Loader<String>(backgroundScope, FakeSession(), dispatcher, MemoryCache(mapOf("someone" to "theirs")).cache) { error("offline") }
        loader.state.onEach { }.launchIn(backgroundScope)
        assertEquals(Load.Failed, loader.state.value)
    }

    @Test fun aReloadKeepsShowingTheCurrentDataInsteadOfASpinner() = runTest(dispatcher) {
        var next = CompletableDeferred("first")
        val loader = Loader(backgroundScope, FakeSession(), dispatcher) { next.await() }
        loader.state.onEach { }.launchIn(backgroundScope)
        assertEquals(Load.Ready("first"), loader.state.value)
        next = CompletableDeferred()
        loader.reload()
        assertEquals(Load.Ready("first"), loader.state.value)
        next.complete("second")
        assertEquals(Load.Ready("second"), loader.state.value)
    }
}
