package com.twitchplayer.tv.feature.player

import com.twitchplayer.tv.feature.player.data.emotes.Emote
import com.twitchplayer.tv.feature.player.data.emotes.EmoteGroup
import com.twitchplayer.tv.feature.player.data.emotes.EmoteSection
import com.twitchplayer.tv.feature.player.data.emotes.EmoteSource
import com.twitchplayer.tv.feature.player.presentation.EmotePickerState
import com.twitchplayer.tv.feature.player.presentation.EmoteQuery
import com.twitchplayer.tv.feature.player.presentation.completeEmote
import com.twitchplayer.tv.feature.player.presentation.emoteQueryAt
import com.twitchplayer.tv.feature.player.presentation.insertEmote
import kotlinx.collections.immutable.toImmutableList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private fun e(name: String) = Emote(name, "u/$name")

class EmotePickerTest {
    private val picker = EmotePickerState(
        groups = listOf(
            EmoteGroup(EmoteSource.Twitch, EmoteSection.Channel, listOf(e("chanHype"), e("Kappa"))),
            EmoteGroup(EmoteSource.Twitch, EmoteSection.Locked, listOf(e("lockedHype"))),
            EmoteGroup(EmoteSource.SevenTv, EmoteSection.Channel, listOf(e("Kappa"), e("sevenHype"), e("PogHype"))),
            EmoteGroup(EmoteSource.Ffz, EmoteSection.Global, listOf(e("hypeFfz"))),
        ).toImmutableList(),
        recent = listOf(e("sevenHype")).toImmutableList(),
    )

    @Test fun searchIsCaseInsensitivePrefixFirstRecentsFirstAndSkipsLockedAndDuplicates() {
        // Prefix matches first, then names that only contain the query, each in the lists' order.
        assertEquals(listOf("hypeFfz", "chanHype", "sevenHype", "PogHype"), picker.search("hype").map { it.name })
        assertEquals(listOf("Kappa"), picker.search("kap").map { it.name }, "a name in two lists comes back once")
        assertEquals(emptyList(), picker.search("locked").map { it.name }, "locked emotes are never suggested")
        assertEquals(emptyList(), picker.search("   ").map { it.name })
    }

    @Test fun aPrefixMatchBeatsAContainedOneAndARecentBeatsOtherPrefixMatches() {
        val state = EmotePickerState(
            groups = listOf(EmoteGroup(EmoteSource.Bttv, EmoteSection.Global, listOf(e("xpog"), e("pogA"), e("pogB")))).toImmutableList(),
            recent = listOf(e("pogB")).toImmutableList(),
        )
        assertEquals(listOf("pogB", "pogA", "xpog"), state.search("pog").map { it.name })
        assertEquals(1, state.search("pog", limit = 1).size)
    }

    @Test fun sourcesFollowThePickersTabOrderAndOnlyListSourcesThatHaveEmotes() {
        assertEquals(listOf(EmoteSource.Twitch, EmoteSource.SevenTv, EmoteSource.Ffz), picker.sources)
        assertEquals(2, picker.groupsOf(EmoteSource.Twitch).size)
    }

    @Test fun aColonQueryNeedsTheColonAtAWordStartAndTwoNameCharacters() {
        assertEquals(EmoteQuery(0, 3, "ka"), emoteQueryAt(":ka", 3))
        assertEquals(EmoteQuery(6, 12, "kappa"), emoteQueryAt("hello :kappa", 12))
        assertNull(emoteQueryAt(":k", 2), "one character is too short")
        assertNull(emoteQueryAt("a:ka", 4), "a colon inside a word is not a query")
        assertNull(emoteQueryAt("hello kappa", 11), "no colon")
        assertNull(emoteQueryAt("", 0))
        assertEquals(EmoteQuery(0, 3, "ka"), emoteQueryAt(":ka and more", 3), "only the text before the cursor counts")
    }

    @Test fun insertingAnEmoteAddsSpacesOnlyWhereTheyAreNeeded() {
        assertEquals("Kappa " to 6, insertEmote("", 0, 0, "Kappa"))
        assertEquals("hi Kappa " to 9, insertEmote("hi ", 3, 3, "Kappa"))
        assertEquals("hi Kappa " to 9, insertEmote("hi", 2, 2, "Kappa"))
        assertEquals("a Kappa b" to 8, insertEmote("a b", 2, 2, "Kappa"), "no second space when one follows")
        assertEquals("a Kappa " to 8, insertEmote("a xyz", 2, 5, "Kappa"), "a selection is replaced")
    }

    @Test fun completingASuggestionReplacesTheQueryAndKeepsWhatFollows() {
        val text = "go :kap now"
        val query = emoteQueryAt(text, 7)!!
        assertEquals("go Kappa now" to 9, completeEmote(text, query, "Kappa"))
        val end = emoteQueryAt("go :kap", 7)!!
        assertEquals("go Kappa " to 9, completeEmote("go :kap", end, "Kappa"))
    }
}
