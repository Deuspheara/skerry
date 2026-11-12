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

    /** Home: scroll the stream list down and back up. */
    fun homeScroll(scope: MacrobenchmarkScope) = with(scope) {
        swipeBothWays(times = 3)
        device.waitForIdle()
    }

    /** The bar's Following tab and back to Home. */
    fun homeTabs(scope: MacrobenchmarkScope) = with(scope) {
        tab("Following")
        tab("Home")
    }

    /** Browse, then into the first category (its streams), scroll, back. */
    fun browseAndCategory(scope: MacrobenchmarkScope) = with(scope) {
        tab("Browse")
        swipeBothWays(times = 2)
        // The first card of the grid: the demo data's first category.
        scrollable().children.firstOrNull { it.isClickable }?.click() ?: error("no category card in Browse")
        device.waitForIdle()
        swipeBothWays(times = 1)
        device.pressBack()
        device.waitForIdle()
        tab("Home")
    }

    /** The search pill opens the Search page: type a query, let the results update, back to Home. */
    fun search(scope: MacrobenchmarkScope) = with(scope) {
        openSearch()
        val field = await(By.pkg(TARGET_PACKAGE).clazz("android.widget.EditText"), "the search field")
        field.click()
        field.text = "lun"
        device.waitForIdle()
        Thread.sleep(SEARCH_SETTLE_MS)
        back()
        await(firstStream, "Home after the search page")
    }

    /** The streamer row under the player leads to the channel page (its replays), back, and the player is closed. */
    fun channel(scope: MacrobenchmarkScope) = with(scope) {
        openChannel()
        swipeBothWays(times = 1)
        device.pressBack()
        device.waitForIdle()
        PlayerScenarios.closeStrip(this)
        await(firstStream, "Home after the channel page")
    }

    /** The account circle opens Settings: scroll to the end and back, back to Home. */
    fun settings(scope: MacrobenchmarkScope) = with(scope) {
        openSettings()
        swipeBothWays(times = 2)
        back()
        await(firstStream, "Home after Settings")
    }

    private const val SEARCH_SETTLE_MS = 800L
}
