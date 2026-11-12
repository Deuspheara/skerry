package com.twitchplayer.phone

import android.content.Context
import android.content.pm.ActivityInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.twitchplayer.phone.di.phoneModules
import com.twitchplayer.phone.feature.player.EMOTE_BUTTON_TAG
import com.twitchplayer.phone.feature.player.EMOTE_PANEL_TAG
import com.twitchplayer.phone.feature.player.EMOTE_SEARCH_TAG
import com.twitchplayer.phone.feature.player.EMOTE_SUGGESTIONS_TAG
import com.twitchplayer.phone.ui.theme.SharedPreferencesThemePreference
import com.twitchplayer.tv.demo.demoModule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.core.module.Module

/**
 * The emote picker and the `:name` suggestions on the demo chat (whose picker lists have every tab, a locked list and
 * lists of other channels), through the real activity, presenter and chat box.
 */
@RunWith(AndroidJUnit4::class)
class PhoneEmoteUiTest {
    @get:Rule val rule = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null
    private var loaded: List<Module> = emptyList()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun string(id: Int) = context.getString(id)

    private fun launchPlayer() {
        context.getSharedPreferences(SharedPreferencesThemePreference.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        loaded = listOf(demoModule)
        loadKoinModules(phoneModules() + loaded)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        rule.openFirstStreamPlayer(context)
    }

    @After fun close() {
        scenario?.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
        scenario?.close()
        if (loaded.isNotEmpty()) unloadKoinModules(loaded)
    }

    /** The message box (the picker has a text field of its own). */
    private val messageField = hasSetTextAction() and !hasTestTag(EMOTE_SEARCH_TAG)

    private fun messageText(): String = rule.onNode(messageField).fetchSemanticsNode().config
        .getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()

    private fun waitFor(matcher: SemanticsMatcher) = rule.waitUntil(WAIT_MS) { rule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }

    private fun openPicker() {
        rule.onNodeWithTag(EMOTE_BUTTON_TAG).performClick()
        waitFor(hasTestTag(EMOTE_PANEL_TAG))
    }

    @Test fun theEmoteButtonOpensThePickerWithATabPerSourceAndTheButtonBecomesTheKeyboard() {
        launchPlayer()
        openPicker()

        rule.onNodeWithTag(EMOTE_PANEL_TAG).assertIsDisplayed()
        listOf("Twitch", "7TV", "BTTV", "FFZ").forEach { rule.onNodeWithText(it).assertIsDisplayed() }
        rule.onNodeWithContentDescription(string(R.string.phone_emote_keyboard)).assertIsDisplayed()
    }

    @Test fun aTappedEmoteIsPutInTheMessageWithASpaceAndNothingIsSent() {
        launchPlayer()
        openPicker()

        rule.onNodeWithTag(EMOTE_SEARCH_TAG).performTextInput("demoHeart")
        waitFor(androidx.compose.ui.test.hasContentDescription(context.getString(R.string.phone_emote_description, "demoHeart")))
        rule.onNodeWithContentDescription(context.getString(R.string.phone_emote_description, "demoHeart")).performClick()

        rule.waitUntil(WAIT_MS) { messageText() == "demoHeart " }
        rule.onNodeWithTag(EMOTE_PANEL_TAG).assertIsDisplayed()
    }

    @Test fun anEmoteTheViewerCannotUseIsListedAsLockedAndCannotBeInserted() {
        launchPlayer()
        openPicker()

        rule.onNodeWithText("Twitch").performClick()
        val locked = context.getString(R.string.phone_emote_locked_description, "demoTierThree")
        waitFor(androidx.compose.ui.test.hasContentDescription(locked))
        rule.onNodeWithContentDescription(locked).performClick()

        rule.waitForIdle()
        assertEquals("", messageText())
    }

    @Test fun searchingSkipsLockedEmotesAndSaysWhenNothingMatches() {
        launchPlayer()
        openPicker()

        rule.onNodeWithTag(EMOTE_SEARCH_TAG).performTextInput("demoTier")

        waitFor(androidx.compose.ui.test.hasText(string(R.string.phone_emote_empty)))
    }

    @Test fun aColonNameShowsSuggestionsAndTappingOneCompletesIt() {
        launchPlayer()

        rule.onNode(messageField).performTextInput("hi :demoCl")
        waitFor(hasTestTag(EMOTE_SUGGESTIONS_TAG))
        rule.onNodeWithTag(EMOTE_SUGGESTIONS_TAG).assertIsDisplayed()
        rule.onNodeWithContentDescription(context.getString(R.string.phone_emote_description, "demoClap")).performClick()

        rule.waitUntil(WAIT_MS) { messageText() == "hi demoClap " }
        assertEquals("the strip goes away once the name is complete", 0, rule.onAllNodes(hasTestTag(EMOTE_SUGGESTIONS_TAG)).fetchSemanticsNodes().size)
    }

    @Test fun backClosesThePickerFirstAndKeepsThePlayer() {
        launchPlayer()
        openPicker()

        scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }

        rule.waitUntil(WAIT_MS) { rule.onAllNodes(hasTestTag(EMOTE_PANEL_TAG)).fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithText(string(R.string.phone_player_chat_hint)).assertIsDisplayed()
        assertEquals("still on the player", 0, rule.onAllNodesWithText(string(R.string.watch)).fetchSemanticsNodes().size)
    }

    @Test fun theSendButtonSendsTheComposedMessageAndClearsTheBox() {
        launchPlayer()
        openPicker()
        rule.onNodeWithTag(EMOTE_SEARCH_TAG).performTextInput("demoStar")
        val star = context.getString(R.string.phone_emote_description, "demoStar")
        waitFor(androidx.compose.ui.test.hasContentDescription(star))
        rule.onNodeWithContentDescription(star).performClick()
        rule.waitUntil(WAIT_MS) { messageText() == "demoStar " }

        rule.onNodeWithContentDescription(string(R.string.chat_send)).performClick()

        rule.waitUntil(WAIT_MS) { messageText().isEmpty() }
        assertEquals(0, rule.onAllNodesWithContentDescription(string(R.string.chat_send)).fetchSemanticsNodes().size)
    }

    private companion object {
        const val WAIT_MS = 20_000L
    }
}
