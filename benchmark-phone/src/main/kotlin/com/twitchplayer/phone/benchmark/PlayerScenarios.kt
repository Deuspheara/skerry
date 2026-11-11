package com.twitchplayer.phone.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction

/** The player journeys: the live player (portrait with chat, chat scroll, fullscreen) and a replay. Same rules as [HomeScenarios]. */
internal object PlayerScenarios {
    /** Opens the first live stream from Home (channel page -> Watch) and lets playback start. */
    fun openLivePlayer(scope: MacrobenchmarkScope) = with(scope) {
        openChannel()
        tap(By.pkg(TARGET_PACKAGE).text("Watch"), "the Watch button")
        await(fullscreenButton, "the player")
        device.waitForIdle()
        Thread.sleep(PLAYBACK_START_MS)
    }

    /** Live player: chat scroll in portrait, back to the end of the list. */
    fun chatScroll(scope: MacrobenchmarkScope) = with(scope) {
        // The chat list is the scrollable under the picture.
        repeat(3) { fling(Direction.UP) }
        repeat(3) { fling(Direction.DOWN) }
        device.waitForIdle()
    }

    /** The fullscreen button twice: portrait -> landscape -> portrait (the activity handles the change itself, no recreation). */
    fun fullscreenToggle(scope: MacrobenchmarkScope) = with(scope) {
        repeat(2) {
            // "Full screen" in portrait, "Exit full screen" in landscape; the controls hide after a few seconds, a tap brings them back.
            val button = fullscreenButton
            if (!device.hasObject(button)) device.click(device.displayWidth / 2, device.displayHeight / SHOW_CONTROLS_DIVISOR)
            tap(button, "the fullscreen button")
            Thread.sleep(ROTATION_SETTLE_MS)
        }
    }

    /** Leaves the live player back to Home. */
    fun leavePlayer(scope: MacrobenchmarkScope) = with(scope) {
        repeat(2) {
            device.pressBack()
            Thread.sleep(BACK_SETTLE_MS)
        }
        device.waitForIdle()
    }

    /** A replay: open the first replay of the first channel, let it play, seek/speed untouched, leave. */
    fun replay(scope: MacrobenchmarkScope) = with(scope) {
        openChannel()
        scrollable().scroll(Direction.DOWN, SCROLL_TO_REPLAYS)
        tap(firstReplay, "the first replay")
        await(fullscreenButton, "the replay player")
        Thread.sleep(PLAYBACK_START_MS)
        device.pressBack()
        device.waitForIdle()
        device.pressBack()
        device.waitForIdle()
        await(firstStream, "Home after the replay")
    }

    private const val PLAYBACK_START_MS = 2_500L
    private const val ROTATION_SETTLE_MS = 700L
    private const val BACK_SETTLE_MS = 400L
    private const val SHOW_CONTROLS_DIVISOR = 6
    private const val SCROLL_TO_REPLAYS = 0.5f
}
