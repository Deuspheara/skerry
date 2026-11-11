package com.twitchplayer.tv.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

internal const val TARGET_PACKAGE = "com.twitchplayer.tv"

/** D-pad driven user journeys over the demo data. Each ends where it started so iterations are comparable. */
internal object TvScenarios {
    private fun MacrobenchmarkScope.waitForContent() {
        startActivityAndWait()
        device.wait(Until.hasObject(By.pkg(TARGET_PACKAGE).depth(0)), 5_000)
        device.waitForIdle()
    }

    fun launch(scope: MacrobenchmarkScope) = scope.waitForContent()

    /** Home: sweep a row, step across rows (vertical anchor scroll), come back. */
    fun homeNavigation(scope: MacrobenchmarkScope) = with(scope) {
        repeat(3) {
            repeat(6) { device.pressDPadRight() }
            device.pressDPadDown()
            repeat(6) { device.pressDPadLeft() }
            device.pressDPadUp()
        }
        device.waitForIdle()
    }

    /** Browse: rail -> Browse, then move over the poster grid. */
    fun browseGrid(scope: MacrobenchmarkScope) = with(scope) {
        device.pressDPadLeft()
        device.pressDPadDown()
        device.pressDPadDown()
        device.pressDPadCenter()
        device.waitForIdle()
        repeat(2) {
            repeat(3) { device.pressDPadDown() }
            repeat(6) { device.pressDPadRight() }
            repeat(3) { device.pressDPadUp() }
            repeat(6) { device.pressDPadLeft() }
        }
    }

    /** Search (from Home): rail -> Search, type a few letters on the on-screen keyboard (results update), back to Home. */
    fun searchKeyboard(scope: MacrobenchmarkScope) = with(scope) {
        device.pressDPadLeft()
        repeat(3) { device.pressDPadDown() }
        device.pressDPadCenter()
        device.waitForIdle()
        device.pressDPadRight()
        device.pressDPadCenter()
        device.pressDPadRight()
        device.pressDPadCenter()
        device.pressDPadDown()
        device.pressDPadCenter()
        device.waitForIdle()
        device.pressBack()
        device.waitForIdle()
    }

    /** Follows (from Home): rail -> Follows, walk its rows, back to Home. */
    fun follows(scope: MacrobenchmarkScope) = with(scope) {
        device.pressDPadLeft()
        device.pressDPadDown()
        device.pressDPadCenter()
        device.waitForIdle()
        repeat(4) { device.pressDPadRight() }
        repeat(2) { device.pressDPadDown() }
        repeat(4) { device.pressDPadLeft() }
        device.waitForIdle()
        device.pressBack()
        device.waitForIdle()
    }

    /**
     * Player: open a channel, cycle the chat mode, open the chat options and change a value, walk into the chat and
     * browse its history up and down (OK returns to live), then go down through the rows under the player, switch channel, come back up, leave.
     * Chat streams ~1 message/s with emotes (set `twitchtv_demo_chat_heavy` to 1 for ~16/s).
     */
    fun playerChatAndZapper(scope: MacrobenchmarkScope) = with(scope) {
        device.pressDPadCenter()
        device.waitForIdle()
        device.pressDPadRight() // the first key after idle only wakes the control bar
        repeat(5) { device.pressDPadRight() } // Pause -> Sound -> Full screen -> Channel + -> Channel - -> Chat
        repeat(3) { device.pressDPadCenter() } // chat compact -> large -> hidden -> compact
        device.pressDPadRight()
        device.pressDPadCenter() // chat options
        device.pressDPadDown()
        device.pressDPadRight() // next row, step its value
        device.pressBack() // closes the options, focus back on their button
        device.pressDPadRight() // into the chat list
        repeat(3) { device.pressDPadUp() }
        repeat(3) { device.pressDPadDown() }
        device.pressDPadCenter()
        device.pressDPadLeft()
        device.pressDPadDown() // rows under the player (Following)
        repeat(3) { device.pressDPadRight() }
        device.pressDPadCenter() // switch channel, the rows stay open
        repeat(2) { device.pressDPadDown() } // category row, then recommendations
        repeat(3) { device.pressDPadUp() } // back up, the first row's Up closes the rows
        device.pressBack()
        Thread.sleep(2_000)
    }

    /**
     * Native player journey, from Home: open the first stream (OK) and let playback start (the demo resolver serves a
     * bundled clip to the Media3 player, so the resolve -> prepare -> first frame path runs offline). The control bar's
     * first key after idle only wakes it, so every journey below starts with a wake key.
     */
    fun openStreamAndPlay(scope: MacrobenchmarkScope) = with(scope) {
        device.pressDPadCenter()
        device.waitForIdle()
        Thread.sleep(PLAYBACK_START_MS)
    }

    /** Chat expand and collapse: Chat button (5 steps right of Pause), OK cycles compact -> large -> overlay -> hidden -> compact. */
    fun chatExpandCollapse(scope: MacrobenchmarkScope, cycles: Int = 2) = with(scope) {
        device.pressDPadRight() // wake the bar
        repeat(5) { device.pressDPadRight() } // Pause -> Sound -> Full screen -> Channel + -> Channel - -> Chat
        repeat(4 * cycles) {
            device.pressDPadCenter()
            Thread.sleep(CHAT_SETTLE_MS)
        }
        repeat(5) { device.pressDPadLeft() } // back on Pause
    }

    /** Channel switch on the same player: Channel + then Channel - (CH+ / CH- buttons), the stream restarts each time. */
    fun switchChannel(scope: MacrobenchmarkScope) = with(scope) {
        device.pressDPadRight() // wake the bar
        repeat(3) { device.pressDPadRight() } // Pause -> Sound -> Full screen -> Channel +
        device.pressDPadCenter()
        Thread.sleep(PLAYBACK_START_MS)
        device.pressDPadRight()
        device.pressDPadCenter()
        Thread.sleep(PLAYBACK_START_MS) // Channel - : back to the first channel
        repeat(4) { device.pressDPadLeft() }
    }

    fun leavePlayer(scope: MacrobenchmarkScope) = with(scope) {
        repeat(4) {
            device.pressBack()
            Thread.sleep(300)
        }
        device.waitForIdle()
    }

    private const val PLAYBACK_START_MS = 2_500L
    private const val CHAT_SETTLE_MS = 700L
}
