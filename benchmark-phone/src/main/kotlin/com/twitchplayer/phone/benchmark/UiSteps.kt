package com.twitchplayer.phone.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

internal const val TARGET_PACKAGE = "com.twitchplayer.phone"

private const val WAIT_MS = 8_000L
private const val FLING_SPEED = 3_000
private const val TAP_ATTEMPTS = 3
private const val CHANNEL_OPEN_MS = 3_000L
private const val MAX_LABELS = 25
private const val EDGE_DIVISOR = 5

/** A followed live stream in the demo data: its card's accessibility label starts with the channel name. */
internal val firstStream: BySelector = By.pkg(TARGET_PACKAGE).descStartsWith("Lunaria, live")

internal val firstReplay: BySelector = By.pkg(TARGET_PACKAGE).desc("Session du mardi soir")

/** "Full screen" in portrait, "Exit full screen" in landscape. */
internal val fullscreenButton: BySelector = By.pkg(TARGET_PACKAGE).desc(Pattern.compile("(?i)(exit )?full screen"))

internal fun MacrobenchmarkScope.await(selector: BySelector, what: String): UiObject2 = device.wait(Until.findObject(selector), WAIT_MS)
    ?: error("$what not found within $WAIT_MS ms; on screen: ${visibleLabels()}")

/** What the screen shows, for a failure message (a missing selector is otherwise a guessing game). */
internal fun MacrobenchmarkScope.visibleLabels(): List<String> = device.findObjects(By.pkg(TARGET_PACKAGE))
    .mapNotNull { it.text ?: it.contentDescription }
    .take(MAX_LABELS)

/** Finds and clicks; a Compose screen that recomposes between the two invalidates the handle, so a stale one is looked up again. */
internal fun MacrobenchmarkScope.tap(selector: BySelector, what: String) {
    repeat(TAP_ATTEMPTS - 1) {
        try {
            await(selector, what).click()
            device.waitForIdle()
            return
        } catch (_: StaleObjectException) {
            device.waitForIdle()
        }
    }
    await(selector, what).click()
    device.waitForIdle()
}

/** Opens the first live stream's channel page from Home; a stale handle after the click is not clicked again (the page is already open). */
internal fun MacrobenchmarkScope.openChannel() {
    val watch = By.pkg(TARGET_PACKAGE).text("Watch")
    repeat(TAP_ATTEMPTS) {
        if (device.hasObject(watch)) return
        try {
            await(firstStream, "the first live stream on Home").click()
        } catch (_: StaleObjectException) {
            // The click happened or the list recomposed: the check above decides on the next pass.
        }
        if (device.wait(Until.hasObject(watch), CHANNEL_OPEN_MS)) return
    }
    await(watch, "the channel page")
}

internal fun MacrobenchmarkScope.tab(label: String) = tap(By.pkg(TARGET_PACKAGE).text(label), "the $label tab")

internal fun MacrobenchmarkScope.scrollable(): UiObject2 = await(By.pkg(TARGET_PACKAGE).scrollable(true), "a scrollable list")

/** One fling of the page's list; the list is looked up each time (a recomposed Compose list invalidates the old handle). */
internal fun MacrobenchmarkScope.fling(direction: Direction) {
    repeat(TAP_ATTEMPTS) {
        try {
            val list = scrollable()
            // Stay clear of the system gesture areas at the screen's edges.
            list.setGestureMargin(device.displayWidth / EDGE_DIVISOR)
            list.fling(direction, FLING_SPEED)
            return
        } catch (_: StaleObjectException) {
            device.waitForIdle()
        }
    }
    error("the list kept changing under the fling")
}

internal fun MacrobenchmarkScope.flingBothWays(times: Int) {
    repeat(times) { fling(Direction.DOWN) }
    repeat(times) { fling(Direction.UP) }
}
