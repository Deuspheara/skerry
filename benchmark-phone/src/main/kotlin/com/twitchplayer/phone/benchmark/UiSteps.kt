package com.twitchplayer.phone.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

internal const val TARGET_PACKAGE = "com.twitchplayer.phone"

// Generous: a starved emulator takes seconds to show Home; the wait is never part of a measurement.
private const val WAIT_MS = 25_000L
private const val SWIPE_STEPS = 25
private const val TAP_ATTEMPTS = 3
private const val CHANNEL_OPEN_MS = 3_000L
private const val MAX_LABELS = 25

/** A followed live stream in the demo data: its card's accessibility label starts with the channel name. */
internal val firstStream: BySelector = By.pkg(TARGET_PACKAGE).descStartsWith("Lunaria, live")

internal val firstReplay: BySelector = By.pkg(TARGET_PACKAGE).desc("Session du mardi soir")

/** Only the live player (signed in, portrait) shows the message box. */
internal val livePlayerMarker: BySelector = By.pkg(TARGET_PACKAGE).textStartsWith("Send a message")

/** "Full screen" in portrait, "Exit full screen" in landscape. */
internal val fullscreenButton: BySelector = By.pkg(TARGET_PACKAGE).desc(Pattern.compile("(?i)(exit )?full screen"))

internal fun MacrobenchmarkScope.await(selector: BySelector, what: String): UiObject2 = device.wait(Until.findObject(selector), WAIT_MS)
    ?: error("$what not found within $WAIT_MS ms; on screen: ${visibleLabels()}${screenshotNote()}")

/** A failing step leaves a screenshot where `adb pull` finds it (the test APK's media folder), and says so in the message. */
internal fun MacrobenchmarkScope.screenshotNote(): String {
    val dir = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.externalMediaDirs.firstOrNull() ?: return ""
    val file = java.io.File(dir, "failure.png")
    return if (device.takeScreenshot(file)) "; screenshot: ${file.absolutePath}" else ""
}

/** What the screen shows, for a failure message (a missing selector is otherwise a guessing game). */
internal fun MacrobenchmarkScope.visibleLabels(): List<String> = runCatching {
    device.findObjects(By.pkg(TARGET_PACKAGE)).mapNotNull { runCatching { it.text ?: it.contentDescription }.getOrNull() }.take(MAX_LABELS)
}.getOrDefault(emptyList())

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

/** One swipe through the middle third of the screen (or, with [fromY]/[toY], a chosen band): an explicit gesture, never mistaken for a tap. */
internal fun MacrobenchmarkScope.swipe(down: Boolean, fromY: Int = device.displayHeight / 3, toY: Int = device.displayHeight * 2 / 3) {
    val x = device.displayWidth / 2
    if (down) device.swipe(x, fromY, x, toY, SWIPE_STEPS) else device.swipe(x, toY, x, fromY, SWIPE_STEPS)
    device.waitForIdle()
}

/** [times] swipes one way and as many back, so a journey ends where it started. */
internal fun MacrobenchmarkScope.swipeBothWays(times: Int, fromY: Int = device.displayHeight / 3, toY: Int = device.displayHeight * 2 / 3) {
    repeat(times) { swipe(down = false, fromY, toY) }
    repeat(times) { swipe(down = true, fromY, toY) }
}
