package com.twitchplayer.tv.feature.player

import com.twitchplayer.tv.feature.player.data.ChatAssets
import com.twitchplayer.tv.feature.player.data.chat.ChatBadge
import com.twitchplayer.tv.feature.player.data.chat.ChatCheer
import com.twitchplayer.tv.feature.player.data.chat.ChatEmote
import com.twitchplayer.tv.feature.player.data.chat.ChatEvent
import com.twitchplayer.tv.feature.player.data.chat.ChatFragment
import com.twitchplayer.tv.feature.player.data.chat.ChatMention
import com.twitchplayer.tv.feature.player.data.chat.ChatMessage
import com.twitchplayer.tv.feature.player.data.chat.ChatMessageContent
import com.twitchplayer.tv.feature.player.data.emotes.Emote
import com.twitchplayer.tv.feature.player.presentation.ChatLineKind
import com.twitchplayer.tv.feature.player.presentation.ChatPiece
import com.twitchplayer.tv.feature.player.presentation.clockText
import com.twitchplayer.tv.feature.player.presentation.contrastRatio
import com.twitchplayer.tv.feature.player.presentation.cycleIndex
import com.twitchplayer.tv.feature.player.presentation.defaultNickColor
import com.twitchplayer.tv.feature.player.presentation.parseColor
import com.twitchplayer.tv.feature.player.presentation.parsePieces
import com.twitchplayer.tv.feature.player.presentation.readableColor
import com.twitchplayer.tv.feature.player.presentation.toLine
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatLineTest {
    private fun e(name: String, animated: Boolean = false, zeroWidth: Boolean = false) = Emote(name, "https://x/$name", if (animated) "https://x/$name.anim" else null, zeroWidth)
    private val assets = ChatAssets(
        badges = mapOf("subscriber:1" to "https://cdn/sub.png"),
        emotes = listOf(e("catJam", animated = true), e("PogU"), e("Rain", animated = true, zeroWidth = true), e("Hat", zeroWidth = true)).associateBy { it.name },
    )
    private fun text(s: String) = ChatFragment("text", s)
    private fun message(vararg fragments: ChatFragment, type: String = "text", color: String? = "#BF94FF", login: String = "nick", cheer: Int = 0) = ChatMessage(
        "id", "b", "u", login.replaceFirstChar(Char::uppercase), login,
        ChatMessageContent(fragments.joinToString("") { it.text }, fragments.toList()),
        messageType = type, badges = listOf(ChatBadge("subscriber", "1"), ChatBadge("mystery", "9")), color = color, cheer = if (cheer > 0) ChatCheer(cheer) else null,
    )

    @Test fun messageResolvesBadgesColorAndTwitchEmotes() {
        val line = message(
            text("hi "),
            ChatFragment("emote", "Kappa", ChatEmote("25", format = listOf("static"))),
            text(" "),
            ChatFragment("emote", "Moving", ChatEmote("77", format = listOf("static", "animated"))),
        ).toLine(assets, "me", 0)
        assertEquals(listOf("https://cdn/sub.png"), line.badgeUrls)
        assertEquals(0xFFBF94FFL, line.color)
        val kappa = (line.pieces[1] as ChatPiece.Emote).layers.single()
        assertEquals("https://static-cdn.jtvnw.net/emoticons/v2/25/static/dark/2.0", kappa.url)
        assertEquals(null, kappa.animatedUrl)
        val moving = (line.pieces[3] as ChatPiece.Emote).layers.single()
        assertEquals("https://static-cdn.jtvnw.net/emoticons/v2/77/animated/dark/2.0", moving.animatedUrl, "a Twitch emote only animates when the message says it has the animated format")
    }

    @Test fun thirdPartyEmotesAreMatchedAsWholeWordsKeepingTheSpaces() {
        val pieces = parsePieces(listOf(text("so  PogU  much catJam, catJamX PogU")), assets.emotes)
        assertEquals(
            listOf("so  ", "PogU", "  much catJam, catJamX ", "PogU"),
            pieces.map { if (it is ChatPiece.Text) it.text else (it as ChatPiece.Emote).name },
        )
    }

    @Test fun emoteNamesAreCaseSensitive() {
        assertTrue(parsePieces(listOf(text("pogu POGU")), assets.emotes).all { it is ChatPiece.Text })
    }

    @Test fun twitchEmotesAreNeverRelookedUpButTheirNameIsKept() {
        val pieces = parsePieces(listOf(ChatFragment("emote", "PogU", ChatEmote("1"))), assets.emotes)
        assertTrue((pieces.single() as ChatPiece.Emote).layers.single().url.startsWith("https://static-cdn"), "the fragment's own Twitch emote wins over a third-party word of the same name")
    }

    @Test fun zeroWidthEmoteStacksOnThePreviousEmote() {
        val pieces = parsePieces(listOf(text("a PogU Rain Hat b")), assets.emotes)
        assertEquals(3, pieces.size)
        val stack = pieces[1] as ChatPiece.Emote
        assertEquals(listOf("PogU", "Rain", "Hat"), stack.layers.map { it.name })
        assertEquals(ChatPiece.Text("a "), pieces[0])
        assertEquals(ChatPiece.Text(" b"), pieces[2])
    }

    @Test fun zeroWidthEmoteWithoutAnEmoteBeforeItIsAnOrdinaryEmote() {
        val first = parsePieces(listOf(text("Rain PogU")), assets.emotes)
        assertEquals(listOf("Rain", "PogU"), first.filterIsInstance<ChatPiece.Emote>().map { it.name })
        assertEquals(1, first.filterIsInstance<ChatPiece.Emote>().first().layers.size, "not merged into the one after it")
        val afterText = parsePieces(listOf(text("hello Rain")), assets.emotes)
        assertEquals(1, (afterText.last() as ChatPiece.Emote).layers.size)
    }

    @Test fun zeroWidthAfterATwitchEmoteStacksToo() {
        val pieces = parsePieces(listOf(ChatFragment("emote", "Kappa", ChatEmote("25")), text(" Hat")), assets.emotes)
        assertEquals(listOf("Kappa", "Hat"), (pieces.single() as ChatPiece.Emote).layers.map { it.name })
    }

    @Test fun messageWithoutFragmentsStillGetsThirdPartyEmotes() {
        val line = ChatMessage("id", "b", "u", "N", "n", ChatMessageContent("go PogU")).toLine(assets, null, 0)
        assertEquals(listOf("go ", "PogU"), line.pieces.map { if (it is ChatPiece.Text) it.text else (it as ChatPiece.Emote).name })
    }

    @Test fun mentionsHighlightsAndCheers() {
        val mention = message(ChatFragment("mention", "@Camille", mention = ChatMention("camille")), text(" salut"), login = "other").toLine(assets, "Camille", 0)
        assertEquals(ChatLineKind.Mention, mention.kind)
        assertEquals(ChatPiece.Strong("@Camille"), mention.pieces.first())
        assertEquals(ChatLineKind.Mention, message(text("yo @camille!"), login = "other").toLine(assets, "Camille", 0).kind, "typed @ without a fragment counts too")
        assertEquals(ChatLineKind.Normal, message(text("@camille"), login = "camille").toLine(assets, "camille", 0).kind, "your own message never highlights you")
        assertEquals(ChatLineKind.Highlight, message(text("big"), type = "channel_points_highlighted").toLine(assets, "x", 0).kind)
        assertEquals(100, message(text("cheer100"), cheer = 100).toLine(assets, "x", 0).bits)
        assertEquals(ChatPiece.Strong("Cheer100"), parsePieces(listOf(ChatFragment("cheermote", "Cheer100")), assets.emotes).single())
    }

    @Test fun noticesBecomeSystemLines() {
        val line = ChatEvent.Notice("n1", "Marlou subscribed").toLine(assets, null, 0)
        assertEquals(ChatLineKind.System, line.kind)
        assertEquals(listOf<ChatPiece>(ChatPiece.Text("Marlou subscribed")), line.pieces)
    }

    @Test fun colorParsingIsDefensive() {
        assertNull(parseColor(null))
        assertNull(parseColor(""))
        assertNull(parseColor("red"))
        assertEquals(0xFF112233L, parseColor("112233"))
        assertEquals(0xFF112233L, parseColor("#112233"))
    }

    @Test fun darkUsernameColorsAreLightenedUntilReadableAndGoodOnesStayUntouched() {
        val background = 0xFF18181BL
        val blue = readableColor(0xFF0000FFL)
        assertTrue(contrastRatio(blue, background) >= 4.5)
        assertEquals(0xFFBF94FFL, readableColor(0xFFBF94FFL))
        assertTrue(contrastRatio(readableColor(0xFF000000L), background) >= 4.5)
    }

    @Test fun usersWithoutAColorGetAStableReadableOne() {
        assertEquals(defaultNickColor("Camille"), defaultNickColor("camille"))
        assertTrue(contrastRatio(defaultNickColor("anyone"), 0xFF18181BL) >= 4.5)
        assertEquals(defaultNickColor("zed"), message(text("x"), color = "", login = "zed").toLine(assets, null, 0).color)
    }

    @Test fun channelCyclingWrapsAndHandlesUnknownCurrent() {
        assertEquals(0, cycleIndex(2, 1, 3))
        assertEquals(2, cycleIndex(0, -1, 3))
        assertEquals(0, cycleIndex(-1, 1, 3))
        assertEquals(2, cycleIndex(-1, -1, 3))
    }

    @Test fun theArrivalTimeIsHoursAndMinutesInTheGivenZoneAlwaysTwoDigits() {
        assertEquals("00:00", clockText(0, TimeZone.UTC))
        assertEquals("22:13", clockText(1_700_000_000_000, TimeZone.UTC)) // 2023-11-14T22:13:20Z
        assertEquals("03:43", clockText(1_700_000_000_000, TimeZone.of("Asia/Kolkata"))) // +05:30, next day
        assertEquals("14:13", clockText(1_700_000_000_000, TimeZone.of("America/Los_Angeles"))) // -08:00
        assertEquals("09:05", clockText(1_699_952_700_000, TimeZone.UTC)) // single-digit hour and minute are padded
    }

    @Test fun linesCarryTheArrivalTimeOfTheirBatch() {
        val line = message(text("x")).toLine(assets, null, 1_700_000_000_000)
        assertEquals(clockText(1_700_000_000_000), line.time)
        assertEquals(5, line.time.length)
    }
}
