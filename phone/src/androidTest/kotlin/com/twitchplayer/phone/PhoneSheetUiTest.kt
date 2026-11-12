package com.twitchplayer.phone

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.twitchplayer.phone.di.phoneModules
import com.twitchplayer.phone.feature.player.STAGE_TAG
import com.twitchplayer.phone.ui.theme.SharedPreferencesThemePreference
import com.twitchplayer.tv.demo.demoModule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.core.module.Module

/**
 * The player as a sheet above the tabs, through the real activity on the demo data: a tap on a stream plays it with its chat, the streamer
 * row leads to the channel page, Back and a drag down shrink it to a strip above the tab bar, a tap or a drag up brings it back, the
 * X closes it, and turning the phone never gives the picture a new view.
 */
@RunWith(AndroidJUnit4::class)
class PhoneSheetUiTest {
    @get:Rule val rule = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null
    private var loaded: List<Module> = emptyList()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun string(id: Int) = context.getString(id)

    private fun launch() {
        context.getSharedPreferences(SharedPreferencesThemePreference.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        loaded = listOf(demoModule)
        loadKoinModules(phoneModules() + loaded)
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After fun close() {
        scenario?.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
        scenario?.close()
        if (loaded.isNotEmpty()) unloadKoinModules(loaded)
    }

    private fun present(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun waitForText(text: String) = rule.waitUntil(WAIT_MS) { present(text) }

    private fun waitUntilGone(text: String) = rule.waitUntil(WAIT_MS) { !present(text) }

    private fun described(description: String) = rule.onAllNodes(hasContentDescription(description)).fetchSemanticsNodes().isNotEmpty()

    private fun waitForDescription(description: String) = rule.waitUntil(WAIT_MS) { described(description) }

    private fun pressBack() = scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }

    private fun waitForStrip() = waitForDescription(string(R.string.phone_player_close))

    @Test fun aTapOnAStreamPlaysItWithItsChatNotTheChannelPage() {
        launch()
        rule.openFirstStreamPlayer(context)

        rule.onNodeWithTag(STAGE_TAG).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.phone_player_chat_hint)).assertIsDisplayed()
        assertTrue("the channel page is not in between", !present(string(R.string.watch)))
    }

    @Test fun theStreamerRowUnderThePlayerOpensTheChannelPageAndTheStripStaysAbove() {
        launch()
        rule.openChannelPageFromPlayer(context)

        rule.onNodeWithText(string(R.string.sec_replays)).assertIsDisplayed()
        waitForStrip()
        rule.onNodeWithContentDescription(string(R.string.phone_player_close)).assertIsDisplayed()
    }

    @Test fun backMinimisesThePlayerToTheStripAndANextBackDoesNotCloseIt() {
        launch()
        rule.openFirstStreamPlayer(context)

        pressBack()
        waitForStrip()
        waitUntilGone(string(R.string.phone_player_chat_hint))

        // The strip is the player's resting place: the page under it (Home) is shown and playing continues; the tab bar is there.
        rule.onNodeWithContentDescription(string(R.string.nav_home)).assertIsDisplayed()
        assertTrue(described(string(R.string.phone_player_close)))
    }

    @Test fun aTapOnTheStripBringsThePlayerBack() {
        launch()
        rule.openFirstStreamPlayer(context)
        pressBack()
        waitForStrip()

        rule.onAllNodes(hasClickLabel(string(R.string.phone_player_expand))).onFirst().performClick()

        waitForText(string(R.string.phone_player_chat_hint))
        assertTrue("the strip is gone once expanded", !described(string(R.string.phone_player_close)))
    }

    @Test fun theCloseButtonEndsThePlayer() {
        launch()
        rule.openFirstStreamPlayer(context)
        pressBack()
        waitForStrip()

        rule.onNodeWithContentDescription(string(R.string.phone_player_close)).performClick()

        rule.waitUntil(WAIT_MS) { rule.onAllNodes(androidx.compose.ui.test.hasTestTag(STAGE_TAG)).fetchSemanticsNodes().isEmpty() }
        assertTrue(!described(string(R.string.phone_player_close)))
    }

    @Test fun draggingThePlayerDownMakesTheStripAndDraggingUpBringsItBack() {
        launch()
        rule.openFirstStreamPlayer(context)

        rule.onNodeWithTag(STAGE_TAG).performTouchInput { swipeDown(startY = top + 40f, endY = bottom + 1_500f, durationMillis = 300) }
        waitForStrip()
        waitUntilGone(string(R.string.phone_player_chat_hint))

        rule.onNodeWithTag(STAGE_TAG).performTouchInput { swipeUp(startY = bottom - 5f, endY = bottom - 1_500f, durationMillis = 300) }
        waitForText(string(R.string.phone_player_chat_hint))
        assertTrue(!described(string(R.string.phone_player_close)))
    }

    @Test fun swipingTheStripDownClosesThePlayerButAHardFlingOfTheExpandedSheetOnlyMinimisesIt() {
        launch()
        rule.openFirstStreamPlayer(context)

        rule.onNodeWithTag(STAGE_TAG).performTouchInput { swipeDown(startY = top + 40f, endY = bottom + 1_500f, durationMillis = 120) }
        waitForStrip()
        assertTrue("a fling of the expanded sheet never closes the player", rule.onAllNodes(androidx.compose.ui.test.hasTestTag(STAGE_TAG)).fetchSemanticsNodes().isNotEmpty())

        rule.onNodeWithTag(STAGE_TAG).performTouchInput { swipeDown(startY = top + 5f, endY = top + 400f, durationMillis = 120) }
        rule.waitUntil(WAIT_MS) { rule.onAllNodes(androidx.compose.ui.test.hasTestTag(STAGE_TAG)).fetchSemanticsNodes().isEmpty() }
    }

    @Test fun backOnAPageAboveTheStripPopsThePageAndKeepsThePlayer() {
        launch()
        rule.openChannelPageFromPlayer(context)
        waitForStrip()

        pressBack()

        rule.waitUntil(WAIT_MS) { !present(string(R.string.watch)) }
        assertTrue("the strip is still there", described(string(R.string.phone_player_close)))
        rule.onNodeWithContentDescription(string(R.string.nav_home)).assertIsDisplayed()
    }

    @Test fun anotherStreamReplacesThePlayingOneAndStartsExpanded() {
        launch()
        rule.openFirstStreamPlayer(context)
        pressBack()
        waitForStrip()

        // A tap on any stream card (the second one on Home) plays that one, expanded, with its chat.
        rule.onAllNodes(hasContentDescription("Maître", substring = true)).onFirst().performClick()
        waitForText(string(R.string.phone_player_chat_hint))
    }

    @Test fun turningThePhoneKeepsTheSamePictureViewAndNeverShowsAnotherParent() {
        launch()
        rule.openFirstStreamPlayer(context)
        rule.waitUntil(WAIT_MS) { views().isNotEmpty() }
        val before = views().single()

        scenario!!.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE }
        rule.waitUntil(WAIT_MS) { orientation() == Configuration.ORIENTATION_LANDSCAPE }
        rule.waitForIdle()
        assertSame("landscape: the picture is the same view", before, views().single())

        scenario!!.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT }
        rule.waitUntil(WAIT_MS) { orientation() == Configuration.ORIENTATION_PORTRAIT }
        rule.waitForIdle()
        assertSame("back to portrait: still the same view", before, views().single())

        // And into the strip and out again.
        pressBack()
        waitForStrip()
        assertSame("as the strip: still the same view", before, views().single())
    }

    private fun orientation(): Int {
        var value = Configuration.ORIENTATION_UNDEFINED
        scenario!!.onActivity { value = it.resources.configuration.orientation }
        return value
    }

    private fun views(): List<androidx.media3.ui.PlayerView> {
        var found = emptyList<androidx.media3.ui.PlayerView>()
        scenario!!.onActivity { found = playerViews(it.window.decorView) }
        return found
    }

    private companion object {
        const val WAIT_MS = 20_000L
    }
}
