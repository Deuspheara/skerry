package com.twitchplayer.phone

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.twitchplayer.phone.di.phoneModules
import com.twitchplayer.phone.ui.theme.SharedPreferencesThemePreference
import com.twitchplayer.tv.demo.demoModule
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.core.module.Module

/**
 * Search as a page opened from the header's pill, in the pill's own look: focused field with the keyboard up, back arrow on the left, recent
 * searches that persist and can be removed one by one or all at once, the channels live now, a filter row and result rows with a live mark.
 */
@RunWith(AndroidJUnit4::class)
class PhoneSearchUiTest {
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
        scenario?.close()
        if (loaded.isNotEmpty()) unloadKoinModules(loaded)
    }

    private fun waitForText(text: String) = rule.waitUntil(WAIT_MS) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun present(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun described(description: String) = rule.onAllNodes(hasContentDescription(description)).fetchSemanticsNodes().isNotEmpty()

    private fun openSearch() {
        rule.waitUntil(WAIT_MS) { described(string(R.string.nav_search)) }
        rule.onNodeWithContentDescription(string(R.string.nav_search)).performClick()
        waitForText(string(R.string.phone_search_live_now))
    }

    /** What an earlier test (or run) left in the persistent list. */
    private fun forgetRecents() {
        if (present(string(R.string.phone_search_clear_recents))) rule.onNodeWithText(string(R.string.phone_search_clear_recents)).performClick()
        rule.waitUntil(WAIT_MS) { !present(string(R.string.phone_search_clear_recents)) }
    }

    private fun search(query: String) {
        rule.onNode(hasSetTextAction()).performTextInput(query)
        rule.onNode(hasSetTextAction()).performImeAction()
    }

    @Test fun thePillOpensAFocusedFieldInTheSameStyleWithABackArrowAtItsStart() {
        launch()
        openSearch()

        rule.onNode(hasSetTextAction()).assertIsFocused()
        val back = rule.onNodeWithContentDescription(string(R.string.cd_back)).fetchSemanticsNode().boundsInRoot
        val field = rule.onNode(hasSetTextAction()).fetchSemanticsNode().boundsInRoot
        assertTrue("the back arrow is before the field", back.right <= field.left + 1f)
        rule.saveScreenshot("search-empty")
    }

    @Test fun whatIsSearchedIsRememberedAndEachRecentCanBeRemovedOrAllCleared() {
        launch()
        openSearch()
        forgetRecents()

        search("lun")
        rule.waitUntil(WAIT_MS) { present(string(R.string.sec_channels)) }
        scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        openSearch()
        rule.onNodeWithText("lun").assertIsDisplayed()

        search("xyz")
        scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        openSearch()
        rule.onNodeWithText("lun").assertIsDisplayed()
        rule.onNodeWithText("xyz").assertIsDisplayed()

        rule.onNodeWithContentDescription(context.getString(R.string.phone_search_remove_recent, "xyz")).performClick()
        rule.waitUntil(WAIT_MS) { !present("xyz") }
        rule.onNodeWithText("lun").assertIsDisplayed()

        rule.onNodeWithText(string(R.string.phone_search_clear_recents)).performClick()
        rule.waitUntil(WAIT_MS) { !present("lun") }
    }

    @Test fun theChannelsLiveNowAreSuggestedBeforeAnythingIsTyped() {
        launch()
        openSearch()

        rule.onNodeWithText(string(R.string.phone_search_live_now)).assertIsDisplayed()
        rule.waitUntil(WAIT_MS) { present("Kestrel FR") }
    }

    @Test fun resultsComeWithAFilterRowAndAChannelRowShowsItsLiveMarkAndViewers() {
        launch()
        openSearch()

        rule.onNode(hasSetTextAction()).performTextInput("lun")
        waitForText(string(R.string.sec_channels))

        rule.onNode(isSelectable() and hasText(string(R.string.phone_search_filter_all))).assertIsSelected()
        rule.onNode(isSelectable() and hasText(string(R.string.sec_categories))).assertIsDisplayed()
        rule.onNode(hasContentDescription("Lunaria", substring = true)).assertIsDisplayed()
        rule.saveScreenshot("search-results")

        rule.onNode(isSelectable() and hasText(string(R.string.sec_channels))).performClick()
        rule.onNode(isSelectable() and hasText(string(R.string.sec_channels))).assertIsSelected()
        rule.onNode(hasContentDescription("Lunaria", substring = true)).assertIsDisplayed()
    }

    @Test fun aSearchWithNoAnswerSaysSo() {
        launch()
        openSearch()

        rule.onNode(hasSetTextAction()).performTextInput("zzzzqq")

        waitForText(string(R.string.state_no_result))
    }

    @Test fun aChannelThatIsLiveOpensThePlayerNotItsPage() {
        launch()
        openSearch()
        rule.onNode(hasSetTextAction()).performTextInput("lun")
        rule.waitUntil(WAIT_MS) { rule.onAllNodes(hasContentDescription("Lunaria", substring = true)).fetchSemanticsNodes().isNotEmpty() }

        rule.onNode(hasContentDescription("Lunaria", substring = true)).performClick()

        waitForText(string(R.string.phone_player_chat_hint))
    }

    private companion object {
        const val WAIT_MS = 20_000L
    }
}
