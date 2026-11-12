package com.twitchplayer.tv.core.presentation

import com.twitchplayer.tv.testing.FakeSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

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
        assertEquals("old", (loader.state.value as Load.Ready<*>).data)
        fresh.complete("new")
        assertEquals("new", (loader.state.value as Load.Ready<*>).data)
        assertEquals("new", memory.stored["me"])
    }

    @Test fun aFailedLoadKeepsTheCachedCopy() = runTest(dispatcher) {
        val loader = Loader<String>(backgroundScope, FakeSession(), dispatcher, MemoryCache(mapOf("me" to "old")).cache) { error("offline") }
        loader.state.onEach { }.launchIn(backgroundScope)
        assertEquals("old", (loader.state.value as Load.Ready<*>).data)
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
        assertEquals("first", (loader.state.value as Load.Ready<*>).data)
        next = CompletableDeferred()
        loader.reload()
        assertEquals("first", (loader.state.value as Load.Ready<*>).data)
        next.complete("second")
        assertEquals("second", (loader.state.value as Load.Ready<*>).data)
    }

    @Test fun aFailedRefreshKeepsTheDataAndSaysItWasNotRefreshed() = runTest(dispatcher) {
        var round = 0
        var fail = false
        val loader = Loader(backgroundScope, FakeSession(), dispatcher) {
            if (fail) error("offline")
            "v${++round}"
        }
        loader.state.onEach { }.launchIn(backgroundScope)
        val first = loader.state.value as Load.Ready<*>
        assertEquals(false, first.refreshFailed)
        assertEquals(true, first.loadedAtMs > 0L, "a fresh result knows when it was fetched")

        fail = true
        loader.reload()
        val kept = loader.state.value as Load.Ready<*>
        assertEquals("v1", kept.data)
        assertEquals(true, kept.refreshFailed)
        assertEquals(first.loadedAtMs, kept.loadedAtMs, "the age is the age of the data, not of the failed attempt")

        fail = false
        loader.reload()
        val fresh = loader.state.value as Load.Ready<*>
        assertEquals("v2", fresh.data)
        assertEquals(false, fresh.refreshFailed, "a good refresh clears the mark")
    }

    @Test fun aCopyFromDiskHasNoKnownAge() = runTest(dispatcher) {
        val loader = Loader<String>(backgroundScope, FakeSession(), dispatcher, MemoryCache(mapOf("me" to "old")).cache) { error("offline") }
        loader.state.onEach { }.launchIn(backgroundScope)
        val shown = loader.state.value as Load.Ready<*>
        assertEquals(null, shown.ageMs(nowMs = 1_000_000L))
        assertEquals(true, shown.refreshFailed)
    }

    @Test fun theAgeIsNeverNegative() {
        assertEquals(0L, Load.Ready("x", loadedAtMs = 500L).ageMs(nowMs = 100L))
        assertEquals(400L, Load.Ready("x", loadedAtMs = 100L).ageMs(nowMs = 500L))
    }
}
