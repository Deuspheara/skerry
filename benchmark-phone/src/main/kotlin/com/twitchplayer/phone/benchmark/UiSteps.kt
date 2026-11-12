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

/** The streamer (picture and name) under the live picture: its name is the channel's display name. */
internal val streamerRow: BySelector = By.pkg(TARGET_PACKAGE).text("Lunaria")

/** The strip's close button; it exists only while the player rests as the strip. */
internal val stripClose: BySelector = By.pkg(TARGET_PACKAGE).desc("Close the player")

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

/** The first live stream's channel page, reached the way a viewer does: play the stream, then tap the streamer row under the picture. */
internal fun MacrobenchmarkScope.openChannel() {
    val watch = By.pkg(TARGET_PACKAGE).text("Watch")
    if (device.hasObject(watch)) return
    PlayerScenarios.openLivePlayer(this)
    // The row's text is not itself clickable (the row is): tap its centre and look again if the page did not open.
    repeat(TAP_ATTEMPTS) {
        val row = device.findObject(streamerRow) ?: return@repeat
        val bounds = row.visibleBounds
        device.click(bounds.centerX(), bounds.centerY())
        device.waitForIdle()
        if (device.wait(Until.hasObject(watch), CHANNEL_OPEN_MS)) return
    }
    await(watch, "the channel page")
}

/** The bottom bar's tabs have no text, only a description (Home, Following, Browse). */
internal fun MacrobenchmarkScope.tab(label: String) = tap(By.pkg(TARGET_PACKAGE).desc(label), "the $label tab")

/** The header's search pill and account circle open the Search and Settings pages. */
internal fun MacrobenchmarkScope.openSearch() = tap(By.pkg(TARGET_PACKAGE).desc("Search"), "the search pill")

internal fun MacrobenchmarkScope.openSettings() = tap(By.pkg(TARGET_PACKAGE).desc("Account"), "the account button")

internal fun MacrobenchmarkScope.back() = tap(By.pkg(TARGET_PACKAGE).desc("Back"), "the back arrow")

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
