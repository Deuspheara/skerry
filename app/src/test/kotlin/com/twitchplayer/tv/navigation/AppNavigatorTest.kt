package com.twitchplayer.tv.navigation

import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavigatorTest {
    private fun navigator(vararg keys: NavKey) = mutableListOf(*keys).let { it to AppNavigator(it) }

    @Test fun railSelectionReplacesTheWholeStack() {
        val (stack, nav) = navigator(HomeKey, CategoryKey("a", "A", ""), ChannelKey("x"))
        nav.openTopLevel(SearchKey)
        assertEquals(listOf<NavKey>(SearchKey), stack)
    }

    @Test fun backPopsDeepStacksFirst() {
        val (stack, nav) = navigator(HomeKey, ChannelKey("x"))
        assertFalse(nav.back())
        assertEquals(listOf<NavKey>(HomeKey), stack)
    }

    @Test fun backFromAnotherTabReturnsHomeThenAsksToQuit() {
        val (stack, nav) = navigator(BrowseKey)
        assertFalse(nav.back())
        assertEquals(listOf<NavKey>(HomeKey), stack)
        assertTrue("Home asks for quit confirmation", nav.back())
        assertEquals(listOf<NavKey>(HomeKey), stack)
    }

    @Test fun watchingThePlayerWeCameFromPopsInsteadOfStacking() {
        val (stack, nav) = navigator(HomeKey, PlayerKey("lunaria"), ChannelKey("lunaria"))
        nav.watch("lunaria")
        assertEquals(listOf<NavKey>(HomeKey, PlayerKey("lunaria")), stack)
    }

    @Test fun watchingAnotherChannelPushesAPlayer() {
        val (stack, nav) = navigator(HomeKey, ChannelKey("zero"))
        nav.watch("zero")
        assertEquals(PlayerKey("zero"), stack.last())
        assertEquals(3, stack.size)
    }

    @Test fun categoryKeepsBrowseSelectedAndPlayerHidesTheRail() {
        assertEquals(2, railIndexOf(CategoryKey("a", "A", "")))
        assertEquals(0, railIndexOf(HomeKey))
        assertNull(railIndexOf(PlayerKey("x")))
        assertNull(railIndexOf(ChannelKey("x")))
    }
}
