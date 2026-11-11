package com.twitchplayer.phone.feature.player

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ControlsVisibilityTest {
    @Test fun hidesAfterTheIdleTime() = runTest {
        val controls = ControlsVisibility(idleMs = 1_000)
        val job = launch { controls.autoHide(pinned = false) }
        runCurrent()
        advanceTimeBy(999)
        assertTrue(controls.visible)
        advanceTimeBy(2)
        assertFalse(controls.visible)
        job.join()
    }

    @Test fun aPinnedBarNeverHides() = runTest {
        val controls = ControlsVisibility(idleMs = 1_000)
        launch { controls.autoHide(pinned = true) }
        advanceTimeBy(10_000)
        assertTrue("paused, ended, dragging or a sheet open keeps it up", controls.visible)
    }

    @Test fun anInteractionRestartsTheTimer() = runTest {
        val controls = ControlsVisibility(idleMs = 1_000)
        var job = launch { controls.autoHide(false) }
        runCurrent()
        advanceTimeBy(800)
        controls.touch()
        job.cancel() // what LaunchedEffect does when `interactions` changes
        job = launch { controls.autoHide(false) }
        runCurrent()
        advanceTimeBy(800)
        assertTrue("800 ms after the touch is still inside the new window", controls.visible)
        advanceTimeBy(300)
        assertFalse(controls.visible)
        job.join()
    }

    @Test fun toggleShowsAndHides() {
        val controls = ControlsVisibility(initiallyVisible = false)
        controls.toggle()
        assertTrue(controls.visible)
        controls.toggle()
        assertFalse(controls.visible)
    }

    @Test fun showCountsAsAnInteraction() {
        val controls = ControlsVisibility()
        val before = controls.interactions
        controls.show()
        assertTrue(controls.interactions > before)
    }

    @Test fun aHiddenBarDoesNotWait() = runTest {
        val controls = ControlsVisibility(idleMs = 1_000, initiallyVisible = false)
        controls.autoHide(false) // returns at once: runTest would hang on an unfinished delay
        assertFalse(controls.visible)
    }
}
