package com.twitchplayer.phone.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

/** The player journeys: the live player (portrait with chat, chat scroll, fullscreen) and a replay. Same rules as [HomeScenarios]. */
internal object PlayerScenarios {
    /** Taps the first live stream on Home: it plays at once, with its chat under the picture. */
    fun openLivePlayer(scope: MacrobenchmarkScope) = with(scope) {
        tap(firstStream, "the first live stream on Home")
        await(livePlayerMarker, "the live player")
        device.waitForIdle()
        Thread.sleep(PLAYBACK_START_MS)
    }

    /**
     * Setup for the player frame benchmarks: launches the app and makes sure the live player is open and expanded, whatever the previous
     * iteration (or test) left: the player itself, the strip over a tab or a channel page, Home, a category. Anything else is backed out of.
     */
    fun ensureLivePlayer(scope: MacrobenchmarkScope) = with(scope) {
        startActivityAndWait()
        repeat(ENSURE_ATTEMPTS) {
            // Either the player (message box), the strip (to expand), or a screen listing the stream; anything else is backed out of.
            if (device.wait(Until.hasObject(livePlayerMarker), PLAYER_PROBE_MS)) return@with device.waitForIdle()
            if (device.hasObject(stripClose)) {
                expandStrip(scope)
                return@repeat
            }
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

    /** A tap on the strip's picture brings the player back. */
    fun expandStrip(scope: MacrobenchmarkScope) = with(scope) {
        val close = await(stripClose, "the strip")
        device.click(device.displayWidth / EXPAND_TAP_DIVISOR, close.visibleBounds.centerY())
        await(livePlayerMarker, "the expanded player")
        device.waitForIdle()
    }

    /** The strip's X: closes the player (and its chat). A no-op when no strip is showing. */
    fun closeStrip(scope: MacrobenchmarkScope) = with(scope) {
        if (device.wait(Until.hasObject(stripClose), PLAYER_PROBE_MS)) tap(stripClose, "the strip's close button")
    }

    /** The sheet gesture: drag the picture down until the player rests as the strip, drag the strip up until it is expanded again. */
    fun sheetDrag(scope: MacrobenchmarkScope) = with(scope) {
        val x = device.displayWidth / 2
        device.swipe(x, device.displayHeight * DRAG_FROM_Y / 100, x, device.displayHeight * DRAG_TO_Y / 100, DRAG_STEPS)
        val close = await(stripClose, "the strip after the drag down")
        device.waitForIdle()
        device.swipe(x / 2, close.visibleBounds.centerY(), x / 2, device.displayHeight / 4, DRAG_STEPS)
        await(livePlayerMarker, "the expanded player after the drag up")
        device.waitForIdle()
    }

    /** Back minimises the player to the strip, a tap on the strip expands it: the other way to the same states. */
    fun sheetBackAndTap(scope: MacrobenchmarkScope) = with(scope) {
        device.pressBack()
        await(stripClose, "the strip after Back")
        device.waitForIdle()
        expandStrip(scope)
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

    /** Leaves the live player: Back minimises it, the strip's X closes it. Home is left showing its streams. */
    fun leavePlayer(scope: MacrobenchmarkScope) = with(scope) {
        device.pressBack()
        await(stripClose, "the strip after Back")
        closeStrip(this)
        device.waitForIdle()
        await(firstStream, "Home after closing the player")
    }

    /** A replay: channel page -> the first replay, let it play, back to the page, close the strip, back to Home. */
    fun replay(scope: MacrobenchmarkScope) = with(scope) {
        openChannel()
        swipe(down = false)
        // A fling still running when the tap lands only stops the list: let it settle, and tap again if the replay did not open.
        val speed = By.pkg(TARGET_PACKAGE).text("Playback speed")
        repeat(REPLAY_TAP_ATTEMPTS) {
            if (device.hasObject(speed)) return@repeat
            Thread.sleep(FLING_SETTLE_MS)
            tap(firstReplay, "the first replay")
            device.wait(Until.hasObject(speed), REPLAY_OPEN_MS)
        }
        await(speed, "the replay page")
        Thread.sleep(PLAYBACK_START_MS)
        device.pressBack() // minimises
        await(stripClose, "the strip after the replay")
        closeStrip(this)
        device.pressBack() // the channel page
        device.waitForIdle()
        closeStrip(this)
        await(firstStream, "Home after the replay")
    }

    private const val PLAYBACK_START_MS = 2_500L
    private const val FLING_SETTLE_MS = 700L
    private const val REPLAY_OPEN_MS = 4_000L
    private const val REPLAY_TAP_ATTEMPTS = 3
    private const val PLAYER_PROBE_MS = 3_000L
    private const val ENSURE_ATTEMPTS = 4
    private const val WAIT_FOR_HOME_MS = 25_000L
    private const val CHAT_SWIPES = 3
    private const val ROTATION_SETTLE_MS = 700L
    private const val SHOW_CONTROLS_DIVISOR = 6
    private const val EXPAND_TAP_DIVISOR = 4
    private const val DRAG_FROM_Y = 18
    private const val DRAG_TO_Y = 75
    private const val DRAG_STEPS = 30
}
