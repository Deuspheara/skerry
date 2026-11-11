package com.twitchplayer.phone

import android.content.Context
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.twitchplayer.phone.di.emptyModule
import com.twitchplayer.phone.di.offlineModule
import com.twitchplayer.phone.di.phoneModules
import com.twitchplayer.phone.feature.settings.CHOICE_LIST_TAG
import com.twitchplayer.phone.ui.theme.SharedPreferencesThemePreference
import com.twitchplayer.tv.core.data.repository.SessionRepository
import com.twitchplayer.tv.core.data.repository.SessionState
import com.twitchplayer.tv.core.network.auth.DeviceCodeResponse
import com.twitchplayer.tv.demo.demoModule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The phone app on the deterministic demo data (the library module `:demo-android`) and on debug fixtures (offline, empty, a signed-out
 * session whose sign-in the test controls): the four tabs, the category and channel pages, Back, search, the theme setting, the
 * sign-in dialog and its result across a rotation, density and touch targets, through the real activity, Koin graph and presenters.
 */
@RunWith(AndroidJUnit4::class)
class PhoneUiTest {
    @get:Rule val rule = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null
    private var loaded: List<Module> = emptyList()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun string(id: Int) = context.getString(id)

    /** A session that starts signed out and whose sign-in shows a code, then waits for the test to decide how it ends. */
    private class ControllableSession : SessionRepository {
        val outcome = CompletableDeferred<Boolean>()
        override val state: StateFlow<SessionState> = MutableStateFlow(SessionState.SignedOut)

        override fun current(): String? = null

        override suspend fun refresh(rejected: String?): String? = null

        override fun start() = Unit

        override suspend fun signIn(onCode: (DeviceCodeResponse) -> Unit): Boolean {
            onCode(DeviceCodeResponse("device", "ABCDEFGH", "https://www.twitch.tv/activate?device-code=ABCDEFGH", 1800, 5))
            return outcome.await()
        }

        override suspend fun signOut() = Unit
    }

    private fun launch(vararg modules: Module) {
        // A known starting point whatever a previous run or the viewer chose.
        context.getSharedPreferences(SharedPreferencesThemePreference.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        loaded = listOf(demoModule) + modules
        // Production definitions first, then this test's overrides: unloading an overriding module removes the definition it replaced
        // for good, so a test never relies on what the previous one left in the container.
        loadKoinModules(phoneModules() + loaded)
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After fun close() {
        scenario?.close()
        if (loaded.isNotEmpty()) unloadKoinModules(loaded)
    }

    private fun waitForDescription(text: String) = rule.waitUntil(WAIT_MS) {
        rule.onAllNodes(hasContentDescription(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun waitForText(text: String) = rule.waitUntil(WAIT_MS) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun openTab(labelRes: Int) = rule.onNode(isSelectable() and hasText(string(labelRes))).performClick()

    private fun pressBack() = scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }

    private fun recreate() = scenario!!.recreate()

    /** The area that answers a touch, not the visible bounds (a 40 dp button has a 48 dp target). */
    private fun assertTouchTarget(matcher: SemanticsMatcher) {
        val node = rule.onNode(matcher).fetchSemanticsNode()
        val minPx = MIN_TARGET.value * context.resources.displayMetrics.density
        check(node.touchBoundsInRoot.height >= minPx - 1 && node.touchBoundsInRoot.width >= minPx - 1) {
            "touch target ${node.touchBoundsInRoot.width} x ${node.touchBoundsInRoot.height} px is under 48 dp ($minPx px)"
        }
    }

    @Test fun homeListsTheFollowedLiveStreams() {
        launch()
        waitForDescription("Lunaria")

        rule.onNodeWithContentDescription("Lunaria", substring = true).assertIsDisplayed()
    }

    @Test fun theForYouTabLabelsItsLiveFollowsSoTheyAreNotMistakenForTheFollowingTab() {
        launch()
        waitForText(string(R.string.phone_home_live_following))

        // "Following" is the name of the other tab only: exactly one node says it.
        check(rule.onAllNodesWithText(string(R.string.sec_following)).fetchSemanticsNodes().size == 1) { "'Following' appears more than once on For you" }
    }

    @Test fun aPhoneShowsSeveralStreamsAtOnce() {
        launch()
        waitForDescription("Lunaria")

        // Compact rows: well over the two big cards a screen used to hold.
        val live = rule.onAllNodes(hasContentDescription(string(R.string.live).lowercase(), substring = true)).fetchSemanticsNodes().size
        check(live >= MIN_VISIBLE_STREAMS) { "only $live stream rows composed" }
    }

    @Test fun theFourTabsOpenTheirScreens() {
        launch()
        waitForDescription("Lunaria")

        openTab(R.string.nav_browse)
        waitForDescription("Valmoor")
        openTab(R.string.nav_search)
        waitForText(string(R.string.sec_trends))
        openTab(R.string.phone_nav_settings)
        waitForText(string(R.string.tile_account))
        rule.onNodeWithText("Camille").assertIsDisplayed()
        openTab(R.string.nav_home)
        waitForDescription("Lunaria")
    }

    @Test fun aCategoryShowsItsStreamsAndBackReturnsToBrowse() {
        launch()
        openTab(R.string.nav_browse)
        waitForDescription("Valmoor")

        rule.onNodeWithContentDescription("Valmoor").performClick()
        waitForDescription("Lunaria")
        // The bar is for tab roots only: a page uses the whole screen.
        rule.onNode(isSelectable() and hasText(string(R.string.nav_browse))).assertDoesNotExist()
        // The language filter names its choices.
        rule.onNodeWithText(string(R.string.filter_all_languages)).assertIsDisplayed()

        pressBack()
        waitForDescription("Valmoor")
        rule.onNode(isSelectable() and hasText(string(R.string.nav_browse))).assertIsDisplayed()
    }

    @Test fun aChannelPageOffersWatchAndTheStubSaysThePlayerIsComing() {
        launch()
        waitForDescription("Lunaria")
        rule.onNodeWithContentDescription("Lunaria", substring = true).performClick()

        waitForText(string(R.string.watch))
        rule.onNodeWithText(string(R.string.sec_replays)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.watch)).performClick()

        waitForText(string(R.string.phone_player_soon))
    }

    @Test fun theChannelPageKeepsItsBackStackAcrossARotation() {
        launch()
        waitForDescription("Lunaria")
        rule.onNodeWithContentDescription("Lunaria", substring = true).performClick()
        waitForText(string(R.string.watch))

        recreate()

        waitForText(string(R.string.watch))
        pressBack()
        waitForDescription("Lunaria")
        rule.onNode(isSelectable() and hasText(string(R.string.nav_home))).assertIsDisplayed()
    }

    @Test fun searchFindsChannelsAsYouType() {
        launch()
        openTab(R.string.nav_search)
        waitForText(string(R.string.sec_trends))

        rule.onNode(hasSetTextAction()).performTextInput("lun")

        waitForText(string(R.string.sec_channels))
        waitForDescription("Lunaria")
    }

    @Test fun theThemeChoiceIsOfferedAndApplied() {
        launch()
        openTab(R.string.phone_nav_settings)
        waitForText(string(R.string.phone_settings_theme))

        rule.onNodeWithText(string(R.string.phone_settings_theme)).performClick()
        waitForText(string(R.string.phone_theme_light))
        rule.onNodeWithText(string(R.string.phone_theme_light)).performClick()

        // The dialog closes and the row now says Light.
        rule.waitUntil(WAIT_MS) { rule.onAllNodesWithText(string(R.string.phone_theme_system)).fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithText(string(R.string.phone_theme_light)).assertIsDisplayed()
    }

    @Test fun theLanguageChoiceListsEveryTranslatedLanguage() {
        launch()
        openTab(R.string.phone_nav_settings)
        waitForText(string(R.string.tile_language))

        rule.onNode(hasText(string(R.string.tile_language)) and hasClickAction()).performClick()

        // Lazy list: the entries far down are composed once scrolled to.
        rule.onNodeWithTag(CHOICE_LIST_TAG).performScrollToNode(hasText("Français"))
        rule.onNodeWithText("Français").assertIsDisplayed()
        rule.onNodeWithTag(CHOICE_LIST_TAG).performScrollToNode(hasText("日本語"))
        rule.onNodeWithText("日本語").assertIsDisplayed()
    }

    @Test fun settingsRowsAreAtLeast48DpTall() {
        launch()
        openTab(R.string.phone_nav_settings)
        waitForText(string(R.string.phone_settings_theme))

        assertTouchTarget(hasText(string(R.string.phone_settings_theme)) and hasClickAction())
        assertTouchTarget(hasText(string(R.string.tile_animations)) and hasClickAction())
    }

    @Test fun theFollowButtonOnAChannelPageHasA48DpTarget() {
        launch()
        waitForDescription("Lunaria")
        rule.onNodeWithContentDescription("Lunaria", substring = true).performClick()
        waitForText(string(R.string.watch))

        // The follow control is a real button, with a touch target tall enough to hit.
        assertTouchTarget(hasText(string(R.string.followed)) and hasClickAction())
    }

    @Test fun whenEveryRequestFailsHomeOffersARetry() {
        launch(offlineModule)

        waitForText(string(R.string.state_error))
        rule.onNodeWithText(string(R.string.state_retry)).assertIsDisplayed().performClick()
        waitForText(string(R.string.state_error))
    }

    @Test fun whenNothingIsFollowedOrLiveTheEmptyStatesSaySo() {
        launch(emptyModule)

        waitForText(string(R.string.state_no_live))
        rule.onNode(hasText(string(R.string.sec_following)) and isSelectable()).performClick()
        waitForText(string(R.string.phone_following_empty))
    }

    @Test fun theSignInDialogShowsTheCodeConfirmsACopyAndCancels() {
        val session = ControllableSession()
        launch(module { single<SessionRepository> { session } })
        waitForText(string(R.string.state_signin_title))

        rule.onNodeWithText(string(R.string.state_signin)).performClick()
        waitForText("ABCDEFGH")
        rule.onNodeWithText(string(R.string.phone_signin_open)).assertIsDisplayed()
        rule.onNodeWithContentDescription(string(R.string.phone_signin_copy)).performClick()
        waitForText(string(R.string.phone_signin_copied))

        rule.onNodeWithText(string(R.string.dialog_cancel)).performClick()
        rule.waitUntil(WAIT_MS) { rule.onAllNodesWithText("ABCDEFGH").fetchSemanticsNodes().isEmpty() }
    }

    @Test fun aSuccessfulSignInSaysSo() {
        val session = ControllableSession()
        launch(module { single<SessionRepository> { session } })
        waitForText(string(R.string.state_signin_title))
        rule.onNodeWithText(string(R.string.state_signin)).performClick()
        waitForText("ABCDEFGH")

        session.outcome.complete(true)

        waitForText(string(R.string.phone_signin_done))
    }

    @Test fun aFailedSignInIsReportedAndStillReportedAfterARotation() {
        val session = ControllableSession()
        launch(module { single<SessionRepository> { session } })
        waitForText(string(R.string.state_signin_title))
        rule.onNodeWithText(string(R.string.state_signin)).performClick()
        waitForText("ABCDEFGH")

        session.outcome.complete(false)
        waitForText(string(R.string.phone_signin_failed))
        // The screen is recreated before the message has gone away: the result lives in the presenter, so it is shown again.
        recreate()

        waitForText(string(R.string.phone_signin_failed))
    }

    private companion object {
        const val WAIT_MS = 10_000L
        const val MIN_VISIBLE_STREAMS = 4
        val MIN_TARGET = 48.dp
    }
}
