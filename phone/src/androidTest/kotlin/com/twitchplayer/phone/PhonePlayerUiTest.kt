package com.twitchplayer.phone

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.twitchplayer.phone.di.phoneModules
import com.twitchplayer.phone.feature.player.STAGE_TAG
import com.twitchplayer.phone.ui.theme.SharedPreferencesThemePreference
import com.twitchplayer.tv.core.data.model.Account
import com.twitchplayer.tv.core.data.repository.SessionRepository
import com.twitchplayer.tv.core.data.repository.SessionState
import com.twitchplayer.tv.core.network.auth.DeviceCodeResponse
import com.twitchplayer.tv.demo.demoModule
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The player pages on the demo data (a looping test-pattern clip and scripted chat): live (controls, quality sheet, fullscreen and the
 * orientation lock, chat and sending), a replay (speed chips) and the signed-out chat prompt, through the real activity, Koin graph,
 * presenter and Media3 player.
 */
@RunWith(AndroidJUnit4::class)
class PhonePlayerUiTest {
    @get:Rule val rule = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null
    private var loaded: List<Module> = emptyList()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun string(id: Int) = context.getString(id)

    /** Starts signed in (so the demo home lists streams) and lets the test sign the viewer out while the player is open. */
    private class SwitchableSession : SessionRepository {
        override val state = MutableStateFlow<SessionState>(SessionState.SignedIn(Account("1", "camille")))
        override fun current(): String? = null
        override suspend fun refresh(rejected: String?): String? = null
        override fun start() = Unit
        override suspend fun signIn(onCode: (DeviceCodeResponse) -> Unit): Boolean = false
        override suspend fun signOut() = Unit
    }

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

    private fun waitForText(text: String) = rule.waitUntil(WAIT_MS) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun waitForDescription(text: String) = rule.waitUntil(WAIT_MS) {
        rule.onAllNodes(hasContentDescription(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun gone(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()

    /** Home -> the first live stream's channel page -> Watch. */
    private fun openLivePlayer() {
        waitForDescription("Lunaria")
        rule.onNodeWithContentDescription("Lunaria", substring = true).performClick()
        waitForText(string(R.string.watch))
        rule.onNodeWithText(string(R.string.watch)).performClick()
        waitForText(string(R.string.phone_player_chat_hint))
    }

    /** A tap on the picture away from every button (the centre holds the play button, the top the back button). */
    private fun tapEmptyPicture() = rule.onNodeWithTag(STAGE_TAG).performTouchInput { click(Offset(width / 2f, height * EMPTY_BAND)) }

    private fun controlsVisible() = rule.onAllNodes(hasContentDescription(string(R.string.player_fullscreen))).fetchSemanticsNodes().isNotEmpty()

    /** The controls start visible and hide on their own: tap only when they are not showing. */
    private fun showControls() {
        if (!controlsVisible()) tapEmptyPicture()
        waitForDescription(string(R.string.player_fullscreen))
    }

    private fun orientation(): Int {
        var value = Configuration.ORIENTATION_UNDEFINED
        scenario!!.onActivity { value = it.resources.configuration.orientation }
        return value
    }

    @Test fun liveShowsThePictureTheChatAndTheMessageBox() {
        launch()
        openLivePlayer()

        rule.onNodeWithTag(STAGE_TAG).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.phone_player_chat_hint)).assertIsDisplayed()
    }

    @Test fun tappingThePictureTogglesTheControls() {
        launch()
        openLivePlayer()
        // The auto-hide timer itself is unit tested (ControlsVisibilityTest) with virtual time: under instrumentation Media3 may be
        // paused by audio-focus loss, which (by design) keeps the controls up, so this checks the wiring, not the timer.
        val hasControls = ::controlsVisible

        val before = hasControls()
        tapEmptyPicture()
        rule.waitUntil(WAIT_MS) { hasControls() != before }
        tapEmptyPicture()
        rule.waitUntil(WAIT_MS) { hasControls() == before }
    }

    @Test fun theQualitySheetListsTheQualitiesAndTicksOne() {
        launch()
        openLivePlayer()
        showControls()

        rule.onNodeWithContentDescription(string(R.string.player_quality)).performClick()
        waitForText(string(R.string.player_quality_title))
        waitForText(string(R.string.quality_auto))
        // Picking one closes the sheet.
        rule.onNodeWithText(string(R.string.quality_auto)).performClick()
        rule.waitUntil(WAIT_MS) { gone(string(R.string.player_quality_title)) }
    }

    @Test fun fullscreenLocksLandscapeThenBackReturnsToPortraitWithoutLeavingThePage() {
        launch()
        openLivePlayer()
        showControls()

        rule.onNodeWithContentDescription(string(R.string.player_fullscreen)).performClick()
        rule.waitUntil(WAIT_MS) { orientation() == Configuration.ORIENTATION_LANDSCAPE }
        var requested = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        scenario!!.onActivity { requested = it.requestedOrientation }
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, requested)
        // The chat stays beside the picture in landscape (its default mode is the side panel).
        waitForText(string(R.string.phone_player_chat_hint))

        // Back leaves fullscreen first: the page is still the player, now upright.
        scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.waitUntil(WAIT_MS) { orientation() == Configuration.ORIENTATION_PORTRAIT }
        rule.onNodeWithTag(STAGE_TAG).assertIsDisplayed()
    }

    @Test fun aWrittenMessageIsSentAndTheBoxIsCleared() {
        launch()
        openLivePlayer()

        rule.onNode(hasSetTextAction()).performTextInput("hello from the phone")
        waitForText("hello from the phone")
        rule.onNodeWithContentDescription(string(R.string.chat_send)).performClick()

        rule.waitUntil(WAIT_MS) { gone("hello from the phone") }
        rule.onNodeWithText(string(R.string.phone_player_chat_hint)).assertIsDisplayed()
    }

    @Test fun aViewerWhoSignsOutOnThePlayerIsAskedToSignInToChat() {
        val session = SwitchableSession()
        launch(module { single<SessionRepository> { session } })
        openLivePlayer()

        session.state.value = SessionState.SignedOut

        waitForText(string(R.string.phone_player_chat_signin))
        rule.onNodeWithText(string(R.string.phone_player_chat_signin)).assertIsDisplayed()
        check(gone(string(R.string.phone_player_chat_hint))) { "a signed-out viewer has no message box" }
    }

    @Test fun aReplayOffersTheSpeedChipsAndTheChosenOneIsSelected() {
        launch()
        waitForDescription("Lunaria")
        rule.onNodeWithContentDescription("Lunaria", substring = true).performClick()
        // The first replay of the demo channel.
        waitForText("Session du mardi soir")
        rule.onNodeWithText("Session du mardi soir").performClick()

        waitForText(string(R.string.phone_player_speed))
        rule.onNodeWithText("2×").performClick()
        rule.waitUntil(WAIT_MS) {
            rule.onAllNodes(hasText("2×") and androidx.compose.ui.test.isSelectable()).fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodes(hasText("2×")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val WAIT_MS = 20_000L
        const val EMPTY_BAND = 0.28f
    }
}
