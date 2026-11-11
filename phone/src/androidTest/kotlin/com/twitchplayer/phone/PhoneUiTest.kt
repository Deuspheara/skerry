package com.twitchplayer.phone

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.twitchplayer.phone.ui.theme.SharedPreferencesThemePreference
import com.twitchplayer.tv.demo.demoModule
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules

/**
 * The phone app on the deterministic demo data (the library module `:demo-android`): the four tabs, the category and channel pages,
 * back navigation, search and the theme setting, through the real activity, Koin graph and presenters.
 */
@RunWith(AndroidJUnit4::class)
class PhoneUiTest {
    @get:Rule val rule = createEmptyComposeRule()
    private lateinit var scenario: ActivityScenario<MainActivity>
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun string(id: Int) = context.getString(id)

    @Before fun launch() {
        // A known starting point whatever a previous run or the viewer chose.
        context.getSharedPreferences(SharedPreferencesThemePreference.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        loadKoinModules(demoModule)
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After fun close() {
        scenario.close()
        unloadKoinModules(demoModule)
    }

    private fun waitForDescription(text: String) = rule.waitUntil(WAIT_MS) {
        rule.onAllNodes(hasContentDescription(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun waitForText(text: String) = rule.waitUntil(WAIT_MS) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun openTab(labelRes: Int) = rule.onNode(isSelectable() and hasText(string(labelRes))).performClick()

    private fun pressBack() = scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

    @Test fun homeListsTheFollowedLiveStreams() {
        waitForDescription("Lunaria")

        rule.onNodeWithContentDescription("Lunaria", substring = true).assertIsDisplayed()
    }

    @Test fun theFourTabsOpenTheirScreens() {
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
        openTab(R.string.nav_browse)
        waitForDescription("Valmoor")

        rule.onNodeWithContentDescription("Valmoor").performClick()
        waitForDescription("Lunaria")
        // The bar is for tab roots only: a page uses the whole screen.
        rule.onNode(isSelectable() and hasText(string(R.string.nav_browse))).assertDoesNotExist()

        pressBack()
        waitForDescription("Valmoor")
        rule.onNode(isSelectable() and hasText(string(R.string.nav_browse))).assertIsDisplayed()
    }

    @Test fun aChannelPageOffersWatchAndTheStubSaysThePlayerIsComing() {
        waitForDescription("Lunaria")
        rule.onNodeWithContentDescription("Lunaria", substring = true).performClick()

        waitForText(string(R.string.watch))
        rule.onNodeWithText(string(R.string.sec_replays)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.watch)).performClick()

        waitForText(string(R.string.phone_player_soon))
    }

    @Test fun searchFindsChannelsAsYouType() {
        openTab(R.string.nav_search)
        waitForText(string(R.string.sec_trends))

        rule.onNode(hasSetTextAction()).performTextInput("lun")

        waitForText(string(R.string.sec_channels))
        waitForDescription("Lunaria")
    }

    @Test fun theThemeChoiceIsOfferedAndApplied() {
        openTab(R.string.phone_nav_settings)
        waitForText(string(R.string.phone_settings_theme))

        rule.onNodeWithText(string(R.string.phone_settings_theme)).performClick()
        waitForText(string(R.string.phone_theme_light))
        rule.onNodeWithText(string(R.string.phone_theme_light)).performClick()

        // The dialog closes and the row now says Light.
        rule.waitUntil(WAIT_MS) { rule.onAllNodesWithText(string(R.string.phone_theme_system)).fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithText(string(R.string.phone_theme_light)).assertIsDisplayed()
    }

    private companion object {
        const val WAIT_MS = 10_000L
    }
}
