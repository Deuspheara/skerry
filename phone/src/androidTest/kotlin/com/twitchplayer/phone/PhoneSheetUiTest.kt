package com.twitchplayer.phone

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
import org.koin.dsl.module

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

    private fun launch(vararg modules: Module) {
        context.getSharedPreferences(SharedPreferencesThemePreference.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        loaded = listOf(demoModule) + modules
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

    @Test fun theWindowOfPictureInPictureIsThePictureAloneEvenWhenThePlayerWasAStrip() {
        launch()
        rule.openFirstStreamPlayer(context)
        pressBack()
        waitForStrip()

        scenario!!.onActivity { it.onPictureInPictureModeChanged(true, it.resources.configuration) }

        rule.waitUntil(WAIT_MS) {
            val stage = rule.onAllNodes(androidx.compose.ui.test.hasTestTag(STAGE_TAG)).fetchSemanticsNodes().firstOrNull()
            val root = rule.onRoot().fetchSemanticsNode()
            stage != null && stage.boundsInRoot == root.boundsInRoot
        }
        assertTrue("no tab bar in the window", !described(string(R.string.nav_home)))
        assertTrue("no strip in the window", !described(string(R.string.phone_player_close)))

        scenario!!.onActivity { it.onPictureInPictureModeChanged(false, it.resources.configuration) }
        waitForStrip()
        rule.onNodeWithContentDescription(string(R.string.nav_home)).assertIsDisplayed()
    }

    @Test fun aHardFlingOfTheExpandedSheetOnlyMinimisesIt() {
        launch()
        rule.openFirstStreamPlayer(context)

        rule.onNodeWithTag(STAGE_TAG).performTouchInput { swipeDown(startY = top + 40f, endY = bottom + 1_500f, durationMillis = 120) }
        waitForStrip()
        assertTrue("a fling of the expanded sheet never closes the player", stagePresent())
    }

    @Test fun aShortOrSlightSwipeOfTheStripDoesNotClosePlayer() {
        launch()
        rule.openFirstStreamPlayer(context)
        pressBack()
        waitForStrip()

        // A small, quick flick (a thumb brushing it while scrolling) and a slow drag that stops short: both leave the player alone.
        rule.onNodeWithTag(STAGE_TAG).performTouchInput { swipeDown(startY = top + 5f, endY = top + 90f, durationMillis = 150) }
        rule.waitForIdle()
        Thread.sleep(SETTLE_WAIT_MS)
        rule.onNodeWithTag(STAGE_TAG).performTouchInput { swipeDown(startY = top + 5f, endY = top + 120f, durationMillis = 900) }
        rule.waitForIdle()
        Thread.sleep(SETTLE_WAIT_MS)

        assertTrue(stagePresent())
        waitForStrip()
    }

    @Test fun aFirmSwipeOfTheStripDownClosesThePlayer() {
        launch()
        rule.openFirstStreamPlayer(context)
        pressBack()
        waitForStrip()

        rule.onNodeWithTag(STAGE_TAG).performTouchInput { swipeDown(startY = top + 5f, endY = top + 450f, durationMillis = 900) }

        rule.waitUntil(WAIT_MS) { !stagePresent() }
    }

    private fun stagePresent() = rule.onAllNodes(androidx.compose.ui.test.hasTestTag(STAGE_TAG)).fetchSemanticsNodes().isNotEmpty()

    @Test fun backOnAPageAboveTheStripPopsThePageAndKeepsThePlayer() {
        launch()
        rule.openChannelPageFromPlayer(context)
        waitForStrip()

        pressBack()

        rule.waitUntil(WAIT_MS) { !present(string(R.string.watch)) }
        assertTrue("the strip is still there", described(string(R.string.phone_player_close)))
        rule.onNodeWithContentDescription(string(R.string.nav_home)).assertIsDisplayed()
    }

    @Test fun theChatBoxIsLabelledForTalkBack() {
        launch()
        rule.openFirstStreamPlayer(context)

        rule.onNode(androidx.compose.ui.test.hasSetTextAction() and hasContentDescription(string(R.string.phone_player_chat_hint))).assertIsDisplayed()
    }

    @Test fun theExpandedPlayerHidesThePageBehindItFromAccessibilityAndTheStripGivesItBack() {
        launch()
        rule.openFirstStreamPlayer(context)
        assertTrue("TalkBack cannot reach the tabs behind the expanded player", !described(string(R.string.nav_home)))

        pressBack()
        waitForStrip()
        waitForDescription(string(R.string.nav_home))
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

    @Test fun backInFullscreenBeforeAReplayHasLoadedTurnsTheScreenUprightAndKeepsTheSession() {
        launch(module { single<com.twitchplayer.tv.feature.player.data.stream.VodResolver> { com.twitchplayer.tv.feature.player.data.stream.VodResolver { kotlinx.coroutines.awaitCancellation() } } })
        rule.openChannelPageFromPlayer(context)
        waitForText("Session du mardi soir")
        rule.onNodeWithText("Session du mardi soir").performClick()
        // The replay is resolving for good: no page, no picture, only the spinner and the back arrow.
        waitForDescription(string(R.string.player_back))

        scenario!!.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE }
        rule.waitUntil(WAIT_MS) { orientation() == Configuration.ORIENTATION_LANDSCAPE }
        rule.waitForIdle()

        pressBack()

        rule.waitUntil(WAIT_MS) { orientation() == Configuration.ORIENTATION_PORTRAIT }
        waitForDescription(string(R.string.player_back))
        var requested = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        rule.waitUntil(WAIT_MS) {
            scenario!!.onActivity { requested = it.requestedOrientation }
            requested == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        assertTrue("the session is still open", described(string(R.string.player_back)))
    }

    @Test fun aLiveStreamComesBackPausedAfterTheActivityWasRecreatedFromSavedState() {
        launch()
        rule.openFirstStreamPlayer(context)
        waitForDescription(string(R.string.player_pause))

        scenario!!.recreate()

        waitForText(string(R.string.phone_player_chat_hint))
        // Loaded and paused: the player is ready with a picture and is not asked to play, and the control offers Play.
        rule.waitUntil(WAIT_MS) { player()?.playbackState == androidx.media3.common.Player.STATE_READY }
        waitForDescription(string(R.string.player_play))
        Thread.sleep(SETTLE_WAIT_MS * 2)
        assertEquals(false, player()?.playWhenReady)
        assertEquals(false, player()?.isPlaying)
        assertTrue("the control says Play, not Pause", described(string(R.string.player_play)) && !described(string(R.string.player_pause)))
    }

    @Test fun aRestoredPausedStreamThatTheViewerStartedIsNeverPausedAgainByATurnOfThePhone() {
        launch()
        rule.openFirstStreamPlayer(context)
        scenario!!.recreate()
        waitForText(string(R.string.phone_player_chat_hint))
        rule.waitUntil(WAIT_MS) { player()?.playbackState == androidx.media3.common.Player.STATE_READY }
        waitForDescription(string(R.string.player_play))

        rule.onNodeWithContentDescription(string(R.string.player_play)).performClick()
        rule.waitUntil(WAIT_MS) { player()?.playWhenReady == true }

        scenario!!.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE }
        rule.waitUntil(WAIT_MS) { orientation() == Configuration.ORIENTATION_LANDSCAPE }
        scenario!!.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT }
        rule.waitUntil(WAIT_MS) { orientation() == Configuration.ORIENTATION_PORTRAIT }
        Thread.sleep(SETTLE_WAIT_MS * 2)

        assertEquals("the viewer's Play stays", true, player()?.playWhenReady)
    }

    @Test fun theChatBoxIsNamedWhileEmptyAndReadsWhatWasTypedOnceItHasText() {
        launch()
        rule.openFirstStreamPlayer(context)
        val field = rule.onNode(androidx.compose.ui.test.hasSetTextAction())

        assertEquals(string(R.string.phone_player_chat_hint), field.fetchSemanticsNode().config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription)?.firstOrNull())
        field.performTextInput("hello")
        rule.waitForIdle()

        val config = field.fetchSemanticsNode().config
        assertEquals("hello", config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.EditableText)?.text)
        assertTrue("a description would hide the typed text from TalkBack", config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription) == null)
    }

    @Test fun aReplayComesBackPausedAfterTheActivityWasRecreatedFromSavedState() {
        launch()
        rule.openChannelPageFromPlayer(context)
        waitForText("Session du mardi soir")
        rule.onNodeWithText("Session du mardi soir").performClick()
        waitForText(string(R.string.phone_player_speed))
        waitForDescription(string(R.string.player_pause))

        scenario!!.recreate()

        waitForDescription(string(R.string.player_play))
        Thread.sleep(SETTLE_WAIT_MS * 2)
        assertTrue("it did not start by itself", !described(string(R.string.player_pause)))
    }

    /** What the picture's player is doing, read on the main thread (Media3 insists on it). */
    private data class PlayerState(val playbackState: Int, val playWhenReady: Boolean, val isPlaying: Boolean)

    private fun player(): PlayerState? {
        var found: PlayerState? = null
        scenario!!.onActivity { activity ->
            found = playerViews(activity.window.decorView).firstOrNull()?.player?.let { PlayerState(it.playbackState, it.playWhenReady, it.isPlaying) }
        }
        return found
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
        const val SETTLE_WAIT_MS = 1_200L
    }
}
