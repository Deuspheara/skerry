package com.twitchplayer.phone.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By

/** The chat input journeys of the live player. Same rules as [HomeScenarios]. */
internal object ChatScenarios {
    /** The emote picker: open it from the chat pill, focus its search field, back to the keyboard. */
    fun emotePicker(scope: MacrobenchmarkScope) = with(scope) {
        tap(By.pkg(TARGET_PACKAGE).desc("Emotes"), "the emote button")
        val searchField = By.pkg(TARGET_PACKAGE).textStartsWith("Search emotes")
        await(searchField, "the emote picker")
        tap(searchField, "the emote search field")
        tap(By.pkg(TARGET_PACKAGE).desc("Keyboard"), "the keyboard button")
        device.pressBack() // hides the keyboard
        device.waitForIdle()
    }
}
