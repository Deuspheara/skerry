package com.twitchplayer.phone.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

/** The player journeys: the live player (portrait with chat, chat scroll, fullscreen) and a replay. Same rules as [HomeScenarios]. */
internal object PlayerScenarios {
    /** Opens the first live stream from Home (channel page -> Watch) and lets playback start. */
    fun openLivePlayer(scope: MacrobenchmarkScope) = with(scope) {
        openChannel()
        tap(By.pkg(TARGET_PACKAGE).text("Watch"), "the Watch button")
        await(livePlayerMarker, "the live player")
        device.waitForIdle()
        Thread.sleep(PLAYBACK_START_MS)
    }

    /**
     * Setup for the player frame benchmarks: launches the app and makes sure the live player is open, whatever the previous iteration (or
     * test) left: the player itself, Home or one of its tabs, a channel page, a category. Anything else is backed out of.
     */
    fun ensureLivePlayer(scope: MacrobenchmarkScope) = with(scope) {
        startActivityAndWait()
        repeat(ENSURE_ATTEMPTS) {
            // Either the player (message box) or a screen listing the stream; only when neither shows up is the page something else.
            val seen = device.wait(Until.hasObject(By.pkg(TARGET_PACKAGE).textStartsWith("Send a message")), PLAYER_PROBE_MS)
            if (seen) return@with device.waitForIdle()
            if (device.wait(Until.hasObject(firstStream), WAIT_FOR_HOME_MS)) {
                openLivePlayer(scope)
                return@with
            }
            device.pressBack()
            device.waitForIdle()
            startActivityAndWait()
        }
        error("could not reach the live player; on screen: ${visibleLabels()}${screenshotNote()}")
    }

    /** Live player: chat scroll in portrait, back to the end of the list. */
    fun chatScroll(scope: MacrobenchmarkScope) = with(scope) {
        // The chat list is the scrollable under the picture.
        swipeBothWays(times = CHAT_SWIPES, fromY = device.displayHeight * 5 / 12, toY = device.displayHeight * 3 / 4)
        device.waitForIdle()
    }

    /** The fullscreen button twice: portrait -> landscape -> portrait (the activity handles the change itself, no recreation). */
    fun fullscreenToggle(scope: MacrobenchmarkScope) = with(scope) {
        repeat(2) {
            showControlsAndTap(fullscreenButton, "the fullscreen button")
            Thread.sleep(ROTATION_SETTLE_MS)
        }
    }

    /** The controls hide after a few seconds and a tap on the picture toggles them: tap until [button] is there, then press it. */
    private fun MacrobenchmarkScope.showControlsAndTap(button: androidx.test.uiautomator.BySelector, what: String) {
        repeat(ENSURE_ATTEMPTS) {
            val target = device.findObject(button) ?: run {
                device.click(device.displayWidth / 2, device.displayHeight / SHOW_CONTROLS_DIVISOR)
                device.wait(Until.findObject(button), PLAYER_PROBE_MS)
            }
            if (target != null) {
                try {
                    target.click()
                    device.waitForIdle()
                    return
                } catch (_: androidx.test.uiautomator.StaleObjectException) {
                    device.waitForIdle()
                }
            }
        }
        error("$what not found; on screen: ${visibleLabels()}")
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
        swipe(down = false)
        tap(firstReplay, "the first replay")
        await(By.pkg(TARGET_PACKAGE).text("Playback speed"), "the replay page")
        Thread.sleep(PLAYBACK_START_MS)
        device.pressBack()
        device.waitForIdle()
        device.pressBack()
        device.waitForIdle()
        await(firstStream, "Home after the replay")
    }

    private const val PLAYBACK_START_MS = 2_500L
    private const val PLAYER_PROBE_MS = 3_000L
    private const val ENSURE_ATTEMPTS = 4
    private const val WAIT_FOR_HOME_MS = 25_000L
    private const val CHAT_SWIPES = 3
    private const val ROTATION_SETTLE_MS = 700L
    private const val BACK_SETTLE_MS = 400L
    private const val SHOW_CONTROLS_DIVISOR = 6
}
