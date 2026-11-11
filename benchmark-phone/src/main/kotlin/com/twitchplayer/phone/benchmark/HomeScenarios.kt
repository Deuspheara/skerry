package com.twitchplayer.phone.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By

/**
 * Touch journeys over the demo data (the benchmark and nonMinified builds always run on it). Every step waits for what it needs and
 * fails loudly when the screen is not what it expects, so a recorded profile never silently misses a journey. Each journey ends where
 * it started (Home, "For you"), so iterations are comparable.
 */
internal object HomeScenarios {
    /** Cold or warm start up to Home showing its streams. */
    fun launch(scope: MacrobenchmarkScope) = with(scope) {
        startActivityAndWait()
        await(firstStream, "the first live stream on Home")
        device.waitForIdle()
    }

    /** Home: scroll the stream list down and back up, then the "Following" tab and back. */
    fun homeScroll(scope: MacrobenchmarkScope) = with(scope) {
        flingBothWays(times = 3)
        device.waitForIdle()
    }

    fun homeTabs(scope: MacrobenchmarkScope) = with(scope) {
        tap(By.pkg(TARGET_PACKAGE).text("Following"), "the Following tab")
        tap(By.pkg(TARGET_PACKAGE).text("For you"), "the For you tab")
    }

    /** Browse, then into the first category (its streams), scroll, back. */
    fun browseAndCategory(scope: MacrobenchmarkScope) = with(scope) {
        tab("Browse")
        flingBothWays(times = 2)
        // The first card of the grid: the demo data's first category.
        scrollable().children.firstOrNull { it.isClickable }?.click() ?: error("no category card in Browse")
        device.waitForIdle()
        flingBothWays(times = 1)
        device.pressBack()
        device.waitForIdle()
        tab("Home")
    }

    /** Search: type a query, let the results update, clear back to Home. */
    fun search(scope: MacrobenchmarkScope) = with(scope) {
        tab("Search")
        val field = await(By.pkg(TARGET_PACKAGE).clazz("android.widget.EditText"), "the search field")
        field.click()
        field.text = "lun"
        device.waitForIdle()
        Thread.sleep(SEARCH_SETTLE_MS)
        device.pressBack() // closes the keyboard
        device.waitForIdle()
        tab("Home")
    }

    /** A channel page and its replay list, back. */
    fun channel(scope: MacrobenchmarkScope) = with(scope) {
        openChannel()
        flingBothWays(times = 1)
        device.pressBack()
        device.waitForIdle()
        await(firstStream, "Home after the channel page")
    }

    /** Settings: scroll to the end and back. */
    fun settings(scope: MacrobenchmarkScope) = with(scope) {
        tab("Settings")
        flingBothWays(times = 2)
        tab("Home")
    }

    private const val SEARCH_SETTLE_MS = 800L
}
