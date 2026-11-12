package com.twitchplayer.tv.core.presentation

import com.twitchplayer.tv.core.platform.ForegroundSignal
import com.twitchplayer.tv.core.platform.WallClock
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
class ForegroundRefreshTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private var now = 1_000_000L
    private val clock = WallClock { now }

    private class Source {
        var calls = 0
        var gate: CompletableDeferred<Unit>? = null
        suspend fun load(): Int {
            calls++
            gate?.await()
            return calls
        }
    }

    @Test fun comingBackAfterTheTtlRefreshesAndKeepsTheOldDataUntilTheNewArrives() = runTest(dispatcher) {
        val signal = ForegroundSignal()
        val source = Source()
        val loader = Loader(backgroundScope, FakeSession(), dispatcher, null, signal, clock, 60_000) { source.load() }
        loader.state.onEach { }.launchIn(backgroundScope)
        assertEquals(1, (loader.state.value as Load.Ready<*>).data)

        now += 61_000
        source.gate = CompletableDeferred()
        signal.appCameToForeground()
        assertEquals(2, source.calls)
        // The refresh is on its way: what is shown is still the old data, never a spinner.
        assertEquals(1, (loader.state.value as Load.Ready<*>).data)
        source.gate!!.complete(Unit)
        assertEquals(2, (loader.state.value as Load.Ready<*>).data)
    }

    @Test fun comingBackSoonerThanTheTtlDoesNothing() = runTest(dispatcher) {
        val signal = ForegroundSignal()
        val source = Source()
        val loader = Loader(backgroundScope, FakeSession(), dispatcher, null, signal, clock, 60_000) { source.load() }
        loader.state.onEach { }.launchIn(backgroundScope)

        now += 59_000
        signal.appCameToForeground()
        assertEquals(1, source.calls)
        assertEquals(1, (loader.state.value as Load.Ready<*>).data)
    }

    @Test fun aRefreshAlreadyRunningIsNotRestartedByAnotherForeground() = runTest(dispatcher) {
        val signal = ForegroundSignal()
        val source = Source()
        val loader = Loader(backgroundScope, FakeSession(), dispatcher, null, signal, clock, 60_000) { source.load() }
        loader.state.onEach { }.launchIn(backgroundScope)

        now += 120_000
        source.gate = CompletableDeferred()
        signal.appCameToForeground()
        signal.appCameToForeground()
        signal.appCameToForeground()
        assertEquals(2, source.calls, "one refresh, the others were ignored while it ran")
        source.gate!!.complete(Unit)
        assertEquals(2, (loader.state.value as Load.Ready<*>).data)
    }

    @Test fun theAgeRestartsWhenARefreshLands() = runTest(dispatcher) {
        val signal = ForegroundSignal()
        val source = Source()
        val loader = Loader(backgroundScope, FakeSession(), dispatcher, null, signal, clock, 60_000) { source.load() }
        loader.state.onEach { }.launchIn(backgroundScope)

        now += 61_000
        signal.appCameToForeground()
        assertEquals(2, source.calls)
        now += 30_000
        signal.appCameToForeground()
        assertEquals(2, source.calls, "only 30 s since the last refresh")
        now += 31_000
        signal.appCameToForeground()
        assertEquals(3, source.calls)
    }

    @Test fun aFailedFirstLoadIsRetriedWhenTheAppComesBack() = runTest(dispatcher) {
        val signal = ForegroundSignal()
        var fail = true
        val loader = Loader(backgroundScope, FakeSession(), dispatcher, null, signal, clock, 60_000) {
            if (fail) error("offline") else "ok"
        }
        loader.state.onEach { }.launchIn(backgroundScope)
        assertEquals(Load.Failed, loader.state.value)

        fail = false
        signal.appCameToForeground()
        assertEquals("ok", (loader.state.value as Load.Ready<*>).data)
    }

    @Test fun withoutASignalNothingRefreshesOnItsOwn() = runTest(dispatcher) {
        val source = Source()
        val loader = Loader(backgroundScope, FakeSession(), dispatcher, null, null, clock, 60_000) { source.load() }
        loader.state.onEach { }.launchIn(backgroundScope)
        now += 600_000
        assertEquals(1, source.calls)
        assertEquals(1, (loader.state.value as Load.Ready<*>).data)
    }
}
