package com.twitchplayer.phone.navigation

import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNavigatorTest {
    private val changes = mutableListOf<PhoneTab>()

    private fun navigator(selected: PhoneTab = PhoneTab.Home) = PhoneNavigator(
        stacks = PhoneTab.entries.associateWith { mutableListOf<NavKey>(it.root) },
        selected = selected,
        onSelectedChanged = { changes += it },
    )

    @Test fun startsOnTheTabRootWithTheBarVisible() {
        val nav = navigator()

        assertEquals(HomeKey, nav.top)
        assertTrue(nav.showsNavigation)
        assertFalse(nav.canGoBack)
    }

    @Test fun detailPagesHideTheBarAndBackPopsThem() {
        val nav = navigator()
        nav.push(ChannelKey("camille"))

        assertEquals(ChannelKey("camille"), nav.top)
        assertFalse(nav.showsNavigation)
        assertTrue(nav.back())
        assertEquals(HomeKey, nav.top)
        assertTrue(nav.showsNavigation)
    }

    @Test fun pushingTheSamePageTwiceDoesNotStackIt() {
        val nav = navigator()
        nav.push(ChannelKey("camille"))
        nav.push(ChannelKey("camille"))
        nav.pop()

        assertEquals(HomeKey, nav.top)
    }

    @Test fun eachTabKeepsItsOwnStack() {
        val nav = navigator()
        nav.selectTab(PhoneTab.Browse)
        nav.push(CategoryKey("1", "Valmoor"))
        nav.selectTab(PhoneTab.Following)
        nav.push(ChannelKey("camille"))

        nav.selectTab(PhoneTab.Browse)
        assertEquals(CategoryKey("1", "Valmoor"), nav.top)
        nav.selectTab(PhoneTab.Following)
        assertEquals(ChannelKey("camille"), nav.top)
        assertEquals(listOf(PhoneTab.Browse, PhoneTab.Following, PhoneTab.Browse, PhoneTab.Following), changes)
    }

    @Test fun selectingTheCurrentTabPopsItToItsRoot() {
        val nav = navigator()
        nav.push(ChannelKey("a"))
        nav.push(CategoryKey("1", "Valmoor"))

        nav.selectTab(PhoneTab.Home)

        assertEquals(HomeKey, nav.top)
        assertTrue(changes.isEmpty())
    }

    @Test fun backPopsThenReturnsToHomeThenLeaves() {
        val nav = navigator(PhoneTab.Browse)
        nav.push(ChannelKey("a"))

        assertTrue(nav.back())
        assertEquals(BrowseKey, nav.top)
        assertTrue(nav.canGoBack)
        assertTrue(nav.back())
        assertEquals(PhoneTab.Home, nav.selected)
        assertFalse(nav.canGoBack)
        assertFalse(nav.back())
    }

    @Test fun popNeverRemovesTheRoot() {
        val nav = navigator()
        nav.pop()

        assertEquals(HomeKey, nav.top)
    }

    @Test fun everyTabRootBelongsToItsTab() {
        PhoneTab.entries.forEach { assertEquals(it, PhoneTab.of(it.root)) }
        assertEquals(null, PhoneTab.of(ChannelKey("a")))
    }

    @Test fun aChannelOpenedFromThePlayerStacksOnTheTabTheViewerIsIn() {
        val nav = navigator(selected = PhoneTab.Browse)
        nav.push(ChannelKey("camille"))

        assertEquals(PhoneTab.Browse, nav.selected)
        assertEquals(ChannelKey("camille"), nav.top)
        assertTrue(changes.isEmpty())
    }

    @Test fun searchAndSettingsAreStackedPagesNotTabs() {
        val nav = navigator()
        nav.push(SearchKey)
        assertFalse(nav.showsNavigation)
        assertEquals(PhoneTab.Home, nav.selected)
        assertTrue(nav.back())
        nav.push(SettingsKey)
        assertEquals(SettingsKey, nav.top)
        assertTrue(nav.back())
        assertEquals(HomeKey, nav.top)
        assertEquals(null, PhoneTab.of(SearchKey))
        assertEquals(null, PhoneTab.of(SettingsKey))
    }

    @Test fun openingTheChannelThatIsAlreadyOnTopDoesNotStackItAgain() {
        val nav = navigator()
        nav.push(ChannelKey("camille"))
        nav.push(ChannelKey("camille"))

        assertEquals(2, nav.currentStack.size)
        assertTrue(nav.back())
        assertEquals(HomeKey, nav.top)
    }

    @Test fun anotherChannelStacksOnTopOfTheFirstOne() {
        val nav = navigator()
        nav.push(ChannelKey("camille"))
        nav.push(ChannelKey("lunaria"))

        assertEquals(3, nav.currentStack.size)
    }

    @Test fun theBarListsFollowingOnlyForASignedInViewer() {
        assertEquals(listOf(PhoneTab.Home, PhoneTab.Following, PhoneTab.Browse), PhoneTab.visible(signedIn = true))
        assertEquals(listOf(PhoneTab.Home, PhoneTab.Browse), PhoneTab.visible(signedIn = false))
    }

    @Test fun openingAPageThatIsAlreadyFurtherDownTheStackReturnsToItInsteadOfStackingItAgain() {
        val nav = navigator()
        nav.push(ChannelKey("a"))
        nav.push(CategoryKey("1", "Valmoor"))
        nav.push(ChannelKey("b"))

        nav.push(ChannelKey("a"))

        assertEquals(listOf(HomeKey, ChannelKey("a")), nav.currentStack.toList())
        assertTrue(nav.back())
        assertEquals(HomeKey, nav.top)
    }

    @Test fun aChannelThatIsOnAnotherTabStacksOnTheCurrentOneAsUsual() {
        val nav = navigator()
        nav.selectTab(PhoneTab.Browse)
        nav.push(ChannelKey("a"))
        nav.selectTab(PhoneTab.Home)

        nav.push(ChannelKey("a"))

        assertEquals(listOf(HomeKey, ChannelKey("a")), nav.currentStack.toList())
    }

    @Test fun signingOutDropsFollowingsPagesAndLeavesItForHome() {
        val nav = navigator()
        nav.selectTab(PhoneTab.Following)
        nav.push(ChannelKey("a"))
        nav.push(SettingsKey)

        nav.onSignedOut()

        assertEquals(PhoneTab.Home, nav.selected)
        assertEquals(HomeKey, nav.top)
        // A later sign-in finds Following at its root, not on a page from before.
        nav.selectTab(PhoneTab.Following)
        assertEquals(listOf<NavKey>(FollowingKey), nav.currentStack.toList())
    }

    @Test fun signingOutFromAnotherTabClearsFollowingAndStaysWhereTheViewerIs() {
        val nav = navigator()
        nav.selectTab(PhoneTab.Following)
        nav.push(ChannelKey("a"))
        nav.selectTab(PhoneTab.Browse)
        nav.push(CategoryKey("1", "Valmoor"))

        nav.onSignedOut()

        assertEquals(PhoneTab.Browse, nav.selected)
        assertEquals(CategoryKey("1", "Valmoor"), nav.top)
        nav.selectTab(PhoneTab.Following)
        assertEquals(FollowingKey, nav.top)
        assertEquals(1, nav.currentStack.size)
    }

    @Test fun signingOutTwiceChangesNothingMore() {
        val nav = navigator()
        nav.onSignedOut()
        nav.onSignedOut()

        assertEquals(PhoneTab.Home, nav.selected)
        assertTrue(changes.isEmpty())
    }

    @Test fun whileTheSessionLoadsTheBarKeepsLastTimesShapeAndAFollowingSelectionIsKept() {
        val two = listOf(PhoneTab.Home, PhoneTab.Browse)
        val three = PhoneTab.entries.toList()
        assertEquals(three, PhoneTab.bar(signedIn = false, sessionLoading = true, hintSignedIn = true))
        assertEquals(two, PhoneTab.bar(signedIn = false, sessionLoading = true, hintSignedIn = false))
        assertEquals(two, PhoneTab.bar(signedIn = false, sessionLoading = false, hintSignedIn = true))
        assertEquals(three, PhoneTab.bar(signedIn = true, sessionLoading = false, hintSignedIn = false))

        assertFalse("a restored Following is not forced to Home while the session loads", PhoneTab.mustLeave(PhoneTab.Following, two, sessionLoading = true))
        assertTrue("once signed out is known it must go", PhoneTab.mustLeave(PhoneTab.Following, two, sessionLoading = false))
        assertFalse(PhoneTab.mustLeave(PhoneTab.Following, three, sessionLoading = false))
        assertFalse(PhoneTab.mustLeave(PhoneTab.Home, two, sessionLoading = false))
    }
}
