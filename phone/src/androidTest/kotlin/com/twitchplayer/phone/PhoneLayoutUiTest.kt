package com.twitchplayer.phone

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.twitchplayer.phone.di.phoneModules
import com.twitchplayer.phone.feature.player.STAGE_TAG
import com.twitchplayer.phone.ui.theme.SharedPreferencesThemePreference
import com.twitchplayer.tv.demo.demoModule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.core.module.Module

/**
 * The player sheet and the chrome in a right-to-left language (Arabic) and at the largest system font sizes, through the real activity on
 * the demo data. Each test restores the app language and the font size it changed.
 */
@RunWith(AndroidJUnit4::class)
class PhoneLayoutUiTest {
    @get:Rule val rule = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null
    private var loaded: List<Module> = emptyList()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun string(id: Int) = context.getString(id)

    private fun shell(command: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).close()
        Thread.sleep(SHELL_SETTLE_MS)
    }

    private fun launch() {
        context.getSharedPreferences(SharedPreferencesThemePreference.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        loaded = listOf(demoModule)
        loadKoinModules(phoneModules() + loaded)
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After fun restore() {
        scenario?.close()
        if (loaded.isNotEmpty()) unloadKoinModules(loaded)
        context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.getEmptyLocaleList()
        shell("settings put system font_scale 1.0")
    }

    private fun arabic() {
        context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags("ar")
    }

    private fun described(description: String) = rule.onAllNodes(hasContentDescription(description)).fetchSemanticsNodes().isNotEmpty()

    private fun waitForDescription(description: String) = rule.waitUntil(WAIT_MS) { described(description) }

    private fun pressBack() = scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }

    private fun screenWidthPx() = context.resources.displayMetrics.widthPixels

    @Test fun inArabicTheStripPictureSitsAtTheRightEdgeAndItsTextAndButtonsToItsLeft() {
        arabic()
        launch()
        rule.openFirstStreamPlayer(context)
        pressBack()
        waitForDescription(string(R.string.phone_player_close))

        val picture = rule.onNodeWithTag(STAGE_TAG).fetchSemanticsNode().boundsInRoot
        val close = rule.onNodeWithContentDescription(string(R.string.phone_player_close)).fetchSemanticsNode().boundsInRoot
        assertTrue("the picture is the start of the strip: at the right edge (${picture.right} of ${screenWidthPx()})", picture.right >= screenWidthPx() - 2f)
        assertTrue("the close button is at the far end: to the left of the picture", close.right <= picture.left)
        rule.saveScreenshot("rtl-strip")
    }

    @Test fun inEnglishTheStripPictureSitsAtTheLeftEdge() {
        launch()
        rule.openFirstStreamPlayer(context)
        pressBack()
        waitForDescription(string(R.string.phone_player_close))

        val picture = rule.onNodeWithTag(STAGE_TAG).fetchSemanticsNode().boundsInRoot
        val close = rule.onNodeWithContentDescription(string(R.string.phone_player_close)).fetchSemanticsNode().boundsInRoot
        assertEquals(0f, picture.left, 2f)
        assertTrue(close.left >= picture.right)
    }

    @Test fun inArabicTheExpandedPlayerStillFillsTheWidthAndShowsItsChat() {
        arabic()
        launch()
        rule.openFirstStreamPlayer(context)

        val picture = rule.onNodeWithTag(STAGE_TAG).fetchSemanticsNode().boundsInRoot
        assertEquals(0f, picture.left, 2f)
        assertEquals(screenWidthPx().toFloat(), picture.right, 2f)
        rule.onNodeWithText(string(R.string.phone_player_chat_hint)).assertIsDisplayed()
        rule.saveScreenshot("rtl-expanded")
    }

    private fun setLocale(tag: String) {
        context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tag)
    }

    /** The hint of the header's pill and of the search field, at 1x on this 411 dp wide screen, is shown whole. */
    private fun hintsAreShownWhole() {
        val hint = string(R.string.phone_search_hint)
        rule.waitUntil(WAIT_MS) { rule.onAllNodesWithText(hint, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        fun check(where: String) = rule.onAllNodesWithText(hint, useUnmergedTree = true).fetchSemanticsNodes().forEach { node ->
            val results = mutableListOf<TextLayoutResult>()
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
            results.firstOrNull()?.let { layout -> assertTrue("the hint is cut in $where: '$hint'", (0 until layout.lineCount).none(layout::isLineEllipsized)) }
        }
        check("the header pill")
        rule.onNodeWithContentDescription(string(R.string.nav_search)).performClick()
        rule.waitUntil(WAIT_MS) { rule.onAllNodes(androidx.compose.ui.test.hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        check("the search field")
    }

    @Test fun theSearchHintIsShownWholeInEnglish() {
        launch()
        hintsAreShownWhole()
    }

    @Test fun theSearchHintIsShownWholeInFrench() {
        setLocale("fr")
        launch()
        hintsAreShownWhole()
    }

    @Test fun theSearchHintIsShownWholeInArabic() {
        arabic()
        launch()
        hintsAreShownWhole()
    }

    private fun stripTextsDoNotOverflow() {
        val strip = rule.onNodeWithTag(STAGE_TAG).fetchSemanticsNode().boundsInRoot
        val texts = rule.onAllNodesWithText("", substring = true, useUnmergedTree = true).fetchSemanticsNodes().filter { it.boundsInRoot.top >= strip.top - 1f && it.boundsInRoot.bottom <= strip.bottom + 1f }
        val withLayout = texts.mapNotNull { node ->
            val results = mutableListOf<TextLayoutResult>()
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
            results.firstOrNull()
        }
        assertTrue("the strip shows its title and subtitle", withLayout.size >= 2)
        // Ellipsis on a single line is the clean way to shorten a text; what must not happen is a line cut in half by the strip.
        withLayout.forEach { assertTrue("a strip text is cut vertically", it.size.height >= it.multiParagraph.height - 1f) }
    }

    @Test fun atTwiceTheFontTheStripGrowsAndKeepsItsTwoLinesAndItsButtons() {
        shell("settings put system font_scale 2.0")
        launch()
        rule.openFirstStreamPlayer(context)
        pressBack()
        waitForDescription(string(R.string.phone_player_close))

        val picture = rule.onNodeWithTag(STAGE_TAG).fetchSemanticsNode().boundsInRoot
        val density = context.resources.displayMetrics.density
        assertTrue("the strip grew past its normal 56 dp (${picture.height / density} dp)", picture.height > 60f * density)
        rule.onNodeWithContentDescription(string(R.string.phone_player_close)).assertIsDisplayed()
        stripTextsDoNotOverflow()
        rule.saveScreenshot("font2-strip")
    }

    @Test fun atOneAndAHalfTheFontInArabicTheStripHasNothingCut() {
        arabic()
        shell("settings put system font_scale 1.5")
        launch()
        rule.openFirstStreamPlayer(context)
        pressBack()
        waitForDescription(string(R.string.phone_player_close))

        stripTextsDoNotOverflow()
        rule.saveScreenshot("font15-rtl-strip")
    }

    @Test fun atTwiceTheFontTheChatBoxAndTheBarStillWork() {
        shell("settings put system font_scale 2.0")
        launch()
        rule.openFirstStreamPlayer(context)

        rule.onNodeWithText(string(R.string.phone_player_chat_hint)).assertIsDisplayed()
        rule.saveScreenshot("font2-chat")
        pressBack()
        waitForDescription(string(R.string.nav_home))
        rule.onNodeWithContentDescription(string(R.string.nav_browse)).assertIsDisplayed()
        rule.saveScreenshot("font2-home")
    }
}

private const val WAIT_MS = 20_000L
private const val SHELL_SETTLE_MS = 600L
