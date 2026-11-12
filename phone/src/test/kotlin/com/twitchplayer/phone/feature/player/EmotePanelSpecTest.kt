package com.twitchplayer.phone.feature.player

import androidx.compose.ui.unit.dp
import com.twitchplayer.tv.feature.player.data.emotes.Emote
import com.twitchplayer.tv.feature.player.data.emotes.EmoteGroup
import com.twitchplayer.tv.feature.player.data.emotes.EmoteSection
import com.twitchplayer.tv.feature.player.data.emotes.EmoteSource
import com.twitchplayer.tv.feature.player.presentation.EmotePickerState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EmotePanelSpecTest {
    private fun picker(vararg sources: EmoteSource, recent: List<String> = emptyList()) = EmotePickerState(
        groups = sources.map { EmoteGroup(it, EmoteSection.Channel, listOf(Emote("e-${it.name}", "u"))) }.toImmutableList(),
        recent = recent.map { Emote(it, "u") }.toImmutableList(),
    )

    @Test fun theFirstTabIsTheRecentsWhenThereAreSomeOtherwiseTheFirstSourceWithEmotes() {
        assertEquals(EmotePanelSpec.RECENT_TAB, EmotePanelSpec.defaultTab(picker(EmoteSource.SevenTv, recent = listOf("a"))))
        assertEquals("Twitch", EmotePanelSpec.defaultTab(picker(EmoteSource.Ffz, EmoteSource.Twitch)))
        assertEquals("SevenTv", EmotePanelSpec.defaultTab(picker(EmoteSource.SevenTv, EmoteSource.Bttv)))
        assertNull(EmotePanelSpec.defaultTab(EmotePickerState(persistentListOf())))
    }

    @Test fun aChosenTabIsKeptWhileItExistsAndFallsBackWhenItDisappears() {
        val state = picker(EmoteSource.Twitch, EmoteSource.Bttv)
        assertEquals("Bttv", EmotePanelSpec.effectiveTab("Bttv", state))
        assertEquals("that provider failed on this channel", "Twitch", EmotePanelSpec.effectiveTab("Ffz", state))
        assertEquals("no recents any more", "Twitch", EmotePanelSpec.effectiveTab(EmotePanelSpec.RECENT_TAB, state))
        assertEquals(EmotePanelSpec.RECENT_TAB, EmotePanelSpec.effectiveTab(EmotePanelSpec.RECENT_TAB, picker(EmoteSource.Twitch, recent = listOf("a"))))
    }

    @Test fun thePanelIsAsTallAsTheKeyboardWasWithinBounds() {
        assertEquals(EmotePanelSpec.DEFAULT_HEIGHT, EmotePanelSpec.panelHeight(null))
        assertEquals(EmotePanelSpec.MIN_HEIGHT, EmotePanelSpec.panelHeight(120.dp))
        assertEquals(EmotePanelSpec.MAX_HEIGHT, EmotePanelSpec.panelHeight(900.dp))
        assertEquals(310.dp, EmotePanelSpec.panelHeight(310.dp))
    }

    @Test fun everyTargetIsAtLeastFortyDp() {
        listOf(EmotePanelSpec.CELL, EmotePanelSpec.TAB_HEIGHT, EmotePanelSpec.SUGGESTION_HEIGHT).forEach { assert(it >= 40.dp) { "$it" } }
    }
}
