package com.twitchplayer.phone.navigation

import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavKeyCodecTest {
    private val keys: List<NavKey> = listOf(HomeKey, BrowseKey, SearchKey, SettingsKey, CategoryKey("509658", "Just Chatting"), ChannelKey("camille"))

    @Test fun everyPageSurvivesTheRoundTrip() {
        keys.forEach { assertEquals(it, NavKeyCodec.decode(NavKeyCodec.encode(it))) }
    }

    @Test fun aSavedStackComesBackAsItWas() {
        val stack = listOf(HomeKey, CategoryKey("1", "Art"), ChannelKey("lunaria"))
        assertEquals(stack, NavKeyCodec.restore(stack.map(NavKeyCodec::encode), HomeKey))
    }

    @Test fun aPageNobodyCanReadResetsTheStackToItsRootInsteadOfCrashing() {
        val saved = listOf(NavKeyCodec.encode(HomeKey), """{"type":"com.twitchplayer.phone.navigation.GoneKey","id":"x"}""")
        assertEquals(listOf<NavKey>(HomeKey), NavKeyCodec.restore(saved, HomeKey))
    }

    @Test fun garbageResetsToTheRoot() {
        listOf(listOf("", "not json", "{"), listOf("[]"), listOf("null")).forEach { assertEquals(listOf<NavKey>(SearchKey), NavKeyCodec.restore(it, SearchKey)) }
        assertNull(NavKeyCodec.decode("{\"type\":\"nope\"}"))
    }

    @Test fun nothingSavedOrAnEmptyStackIsTheRoot() {
        assertEquals(listOf<NavKey>(HomeKey), NavKeyCodec.restore(null, HomeKey))
        assertEquals(listOf<NavKey>(HomeKey), NavKeyCodec.restore(emptyList(), HomeKey))
    }

    @Test fun aStackThatDoesNotStartAtItsTabRootIsNotTrusted() {
        assertEquals(listOf<NavKey>(HomeKey), NavKeyCodec.restore(listOf(ChannelKey("x")).map(NavKeyCodec::encode), HomeKey))
    }
}
