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
        nav.selectTab(PhoneTab.Search)
        nav.push(ChannelKey("camille"))

        nav.selectTab(PhoneTab.Browse)
        assertEquals(CategoryKey("1", "Valmoor"), nav.top)
        nav.selectTab(PhoneTab.Search)
        assertEquals(ChannelKey("camille"), nav.top)
        assertEquals(listOf(PhoneTab.Browse, PhoneTab.Search, PhoneTab.Browse, PhoneTab.Search), changes)
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
        val nav = navigator(PhoneTab.Settings)
        nav.push(ChannelKey("a"))

        assertTrue(nav.back())
        assertEquals(SettingsKey, nav.top)
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
}
