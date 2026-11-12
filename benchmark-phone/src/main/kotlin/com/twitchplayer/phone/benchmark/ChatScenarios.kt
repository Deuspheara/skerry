package com.twitchplayer.phone.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By

/** The chat input journeys of the live player. Same rules as [HomeScenarios]. */
internal object ChatScenarios {
    /** The emote picker: open it from the chat pill, scroll its grid, search, long-press an emote's preview is skipped; back to the keyboard. */
    fun emotePicker(scope: MacrobenchmarkScope) = with(scope) {
        tap(By.pkg(TARGET_PACKAGE).desc("Emotes"), "the emote button")
        val search = await(By.pkg(TARGET_PACKAGE).textStartsWith("Search emotes"), "the emote picker")
        swipeBothWays(times = 1, fromY = device.displayHeight * 5 / 8, toY = device.displayHeight * 7 / 8)
        search.click()
        device.waitForIdle()
        tap(By.pkg(TARGET_PACKAGE).desc("Keyboard"), "the keyboard button")
        device.pressBack() // hides the keyboard
        device.waitForIdle()
    }
}
