package com.twitchplayer.tv.feature.player.ui

import com.twitchplayer.tv.feature.player.data.emotes.Emote
import com.twitchplayer.tv.feature.player.presentation.ChatLine
import com.twitchplayer.tv.feature.player.presentation.ChatLineKind
import com.twitchplayer.tv.feature.player.presentation.ChatPiece
import kotlin.test.Test
import kotlin.test.assertEquals

class ChatAnimationTest {
    private fun line(id: String, animated: Int): ChatLine {
        val emote = Emote("e", "s", "a")
        return ChatLine(id, ChatLineKind.Normal, "n", 0, emptyList(), listOf(ChatPiece.Emote(List(animated) { emote })), "00:00")
    }

    @Test fun animationBudgetGoesToTheNewestVisibleLinesFirst() {
        val visibleNewestFirst = listOf(line("new", 2), line("mid", 3), line("old", 4), line("none", 0))
        assertEquals(mapOf("new" to 2, "mid" to 3, "old" to 1), animationAllowance(visibleNewestFirst, limit = 6))
        assertEquals(emptyMap(), animationAllowance(visibleNewestFirst, limit = 0))
        assertEquals(mapOf("new" to 1), animationAllowance(visibleNewestFirst.take(1), limit = 1), "lines outside the list given never hold budget")
    }

    @Test fun aStillEmoteCostsNothing() {
        val still = ChatLine("s", ChatLineKind.Normal, "n", 0, emptyList(), listOf(ChatPiece.Emote(listOf(Emote("e", "s")))), "00:00")
        assertEquals(0, still.animatedEmoteCount())
    }
}
