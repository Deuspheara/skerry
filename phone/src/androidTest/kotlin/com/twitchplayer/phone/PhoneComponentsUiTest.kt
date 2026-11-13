package com.twitchplayer.phone

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.twitchplayer.phone.navigation.PhoneTab
import com.twitchplayer.phone.ui.components.FilterPill
import com.twitchplayer.phone.ui.components.MarqueeTitle
import com.twitchplayer.phone.ui.components.PhoneBottomBar
import com.twitchplayer.phone.ui.components.PhoneRail
import com.twitchplayer.phone.ui.theme.LocalReducedMotion
import com.twitchplayer.phone.ui.theme.PhoneTheme
import com.twitchplayer.phone.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The app's own small components, on their own: the marquee title, the filter pill, and the bar and rail with two or three tabs. */
@RunWith(AndroidJUnit4::class)
class PhoneComponentsUiTest {
    @get:Rule val rule = createComposeRule()

    private val longName = "A category with a very long name that cannot fit on one line"

    private fun layoutOf(text: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText(text).fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        return results.first()
    }

    @Test fun aLongTitleStaysOnOneLineAndScrollsInsteadOfWrapping() {
        rule.setContent { PhoneTheme(ThemeMode.Dark, reducedMotion = false) { MarqueeTitle(longName, Modifier.width(160.dp)) } }

        val layout = layoutOf(longName)
        assertEquals(1, layout.lineCount)
        assertTrue("the text is wider than its box: the marquee has something to show", layout.didOverflowWidth)
    }

    @Test fun withReducedMotionALongTitleIsCutWithAnEllipsisAndNeverMoves() {
        rule.setContent {
            PhoneTheme(ThemeMode.Dark, reducedMotion = true) {
                CompositionLocalProvider(LocalReducedMotion provides true) { MarqueeTitle(longName, Modifier.width(160.dp)) }
            }
        }

        val layout = layoutOf(longName)
        assertEquals(1, layout.lineCount)
        assertTrue(layout.isLineEllipsized(0))
    }

    @Test fun aScrollingTitleNeverKeepsTheTestFromGoingIdle() {
        rule.setContent { PhoneTheme(ThemeMode.Dark, reducedMotion = false) { MarqueeTitle(longName, Modifier.width(160.dp)) } }

        // Idling would time out if the marquee's endless animation counted as work; time moves on without the test waiting for it.
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(6_000)
        rule.waitForIdle()

        rule.onNodeWithText(longName).assertIsDisplayed()
    }

    @Test fun aShortTitleIsJustText() {
        rule.setContent { PhoneTheme(ThemeMode.Dark, reducedMotion = false) { MarqueeTitle("Valmoor", Modifier.width(160.dp)) } }

        rule.onNodeWithText("Valmoor").assertIsDisplayed()
        assertEquals(1, layoutOf("Valmoor").lineCount)
    }

    @Test fun aFilterPillIsASelectableTabWith48DpToHit() {
        var picked = false
        rule.setContent {
            PhoneTheme(ThemeMode.Dark, reducedMotion = true) {
                Row {
                    FilterPill("Channels", selected = false, onClick = { picked = true })
                    FilterPill("All", selected = true, onClick = {})
                }
            }
        }

        rule.onNode(isSelectable() and androidx.compose.ui.test.hasText("All")).assertIsSelected()
        rule.onNode(isSelectable() and androidx.compose.ui.test.hasText("Channels")).assertIsNotSelected().assertHeightIsAtLeast(48.dp)
        rule.onNodeWithText("Channels").performClick()
        assertTrue(picked)
    }

    @Test fun theBarAndTheRailListExactlyTheTabsTheyAreGiven() {
        rule.setContent {
            PhoneTheme(ThemeMode.Dark, reducedMotion = true) {
                PhoneBottomBar(PhoneTab.bar, PhoneTab.Home, onSelect = {})
                PhoneRail(PhoneTab.bar, PhoneTab.Following, onSelect = {})
            }
        }

        // Home and Browse in both; Following only in the rail (the one given a signed-in list): three descriptions of Home / Browse, one Following.
        assertEquals(2, rule.onAllNodesWithContentDescription(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().getString(R.string.nav_home)).fetchSemanticsNodes().size)
        assertEquals(1, rule.onAllNodes(hasContentDescription(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().getString(R.string.sec_following))).fetchSemanticsNodes().size)
    }
}
