package com.twitchplayer.tv.feature.player

import com.twitchplayer.tv.feature.player.data.ChatAssets
import com.twitchplayer.tv.feature.player.data.chat.ChatEvent
import com.twitchplayer.tv.feature.player.data.chat.ChatFragment
import com.twitchplayer.tv.feature.player.data.chat.ChatMessage
import com.twitchplayer.tv.feature.player.data.chat.ChatMessageContent
import com.twitchplayer.tv.feature.player.data.chat.ChatReply
import com.twitchplayer.tv.feature.player.data.chat.NoticeKind
import com.twitchplayer.tv.feature.player.presentation.ChatLine
import com.twitchplayer.tv.feature.player.presentation.ChatLineKind
import com.twitchplayer.tv.feature.player.presentation.ChatPiece
import com.twitchplayer.tv.feature.player.presentation.Chatter
import com.twitchplayer.tv.feature.player.presentation.MentionQuery
import com.twitchplayer.tv.feature.player.presentation.chatterSuggestions
import com.twitchplayer.tv.feature.player.presentation.completeMention
import com.twitchplayer.tv.feature.player.presentation.mentionQueryAt
import com.twitchplayer.tv.feature.player.presentation.replyPrefix
import com.twitchplayer.tv.feature.player.presentation.toLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RepliesAndMentionsTest {
    private fun message(login: String, text: String, reply: ChatReply? = null) = ChatMessage(
        "id-$login",
        "b",
        "u",
        login.replaceFirstChar(Char::uppercase),
        login,
        ChatMessageContent(text, listOf(ChatFragment("text", text))),
        reply = reply,
    )

    private fun line(login: String, text: String = "hi", reply: ChatReply? = null): ChatLine = message(login, text, reply).toLine(ChatAssets.Empty, "me", 0)

    @Test fun aReplyKeepsTheMessageItAnswers() {
        val reply = ChatReply("p1", "the parent text", "Ana", "ANA")
        val l = line("bob", "agreed", reply)
        assertEquals("p1", l.reply?.parentId)
        assertEquals("Ana", l.reply?.name)
        assertEquals("ana", l.reply?.login, "logins are compared in lower case")
        assertEquals("the parent text", l.reply?.body)
    }

    @Test fun anEmptyParentIdMeansNoReply() {
        assertNull(line("bob", "x", ChatReply()).reply)
        assertNull(line("bob").reply)
    }

    @Test fun anAnswerToTheViewerIsAMentionEvenWithoutTheirName() {
        assertEquals(ChatLineKind.Mention, line("bob", "thanks", ChatReply("p", "q", "Me", "me")).kind)
        assertEquals(ChatLineKind.Normal, line("bob", "thanks", ChatReply("p", "q", "Ana", "ana")).kind)
    }

    @Test fun theViewersOwnAnswerIsNotAMention() {
        assertEquals(ChatLineKind.Normal, line("me", "self reply", ChatReply("p", "q", "Me", "me")).kind)
    }

    @Test fun onlyRealMessagesCanBeAnswered() {
        assertTrue(line("bob").canReply)
        val notice = ChatEvent.Notice("n", "Ana subscribed", NoticeKind.Sub).toLine(ChatAssets.Empty, "me", 0)
        assertFalse(notice.canReply)
    }

    @Test fun aNoticeKeepsItsKindAndAccent() {
        val sub = ChatEvent.Notice("n", "Ana subscribed", NoticeKind.Resub, "BLUE").toLine(ChatAssets.Empty, "me", 0)
        assertEquals(NoticeKind.Resub, sub.notice)
        assertEquals("BLUE", sub.accent)
        assertEquals(ChatLineKind.System, sub.kind)
        assertEquals(listOf<ChatPiece>(ChatPiece.Text("Ana subscribed")), sub.pieces)
        assertNull(ChatEvent.Notice("n", "x").toLine(ChatAssets.Empty, "me", 0).notice, "an unknown type stays a plain system line")
    }

    @Test fun noticeTypesMapToTheirKind() {
        assertEquals(NoticeKind.Sub, NoticeKind.of("sub"))
        assertEquals(NoticeKind.Resub, NoticeKind.of("resub"))
        assertEquals(NoticeKind.Gift, NoticeKind.of("sub_gift"))
        assertEquals(NoticeKind.GiftCommunity, NoticeKind.of("community_sub_gift"))
        assertEquals(NoticeKind.Raid, NoticeKind.of("raid"))
        assertEquals(NoticeKind.Announcement, NoticeKind.of("announcement"))
        assertEquals(NoticeKind.Bits, NoticeKind.of("bits_badge_tier"))
        assertEquals(NoticeKind.Other, NoticeKind.of("something_new"))
    }

    @Test fun aMentionQueryNeedsAnAtAtTheStartOfAWord() {
        assertEquals(MentionQuery(0, 3, "an"), mentionQueryAt("@an", 3))
        assertEquals(MentionQuery(6, 9, "bo"), mentionQueryAt("hello @bo", 9))
        assertNull(mentionQueryAt("mail@bo", 7), "an @ inside a word is not a mention")
        assertNull(mentionQueryAt("@", 1), "nothing typed after the @ yet")
        assertNull(mentionQueryAt("hello", 5))
        assertNull(mentionQueryAt("@an x", 5), "the cursor is past the name")
    }

    @Test fun suggestionsAreTheMostRecentChattersMatchingThePrefix() {
        val lines = listOf(line("anna"), line("andy"), line("bob"), line("anna"), line("me"), line("anders"))
        assertEquals(listOf(Chatter("anders", "Anders"), Chatter("anna", "Anna"), Chatter("andy", "Andy")), chatterSuggestions(lines, "an", "me"))
        assertEquals(listOf(Chatter("bob", "Bob")), chatterSuggestions(lines, "B", "me"), "case does not matter")
        assertTrue(chatterSuggestions(lines, "me", "me").isEmpty(), "the viewer is never suggested to themselves")
        assertTrue(chatterSuggestions(lines, "", "me").isEmpty())
        assertEquals(1, chatterSuggestions(lines, "an", "me", limit = 1).size)
    }

    @Test fun systemLinesAreNotChatters() {
        val notice = ChatEvent.Notice("n", "Ana subscribed", NoticeKind.Sub).toLine(ChatAssets.Empty, "me", 0)
        assertTrue(chatterSuggestions(listOf(notice), "a", "me").isEmpty())
    }

    @Test fun completingAMentionKeepsTheRestOfTheText() {
        val query = mentionQueryAt("hi @an there", 6)!!
        assertEquals("hi @anna there" to 9, completeMention("hi @an there", query, "anna"))
        val atEnd = mentionQueryAt("hi @an", 6)!!
        assertEquals("hi @anna " to 9, completeMention("hi @an", atEnd, "anna"))
    }

    @Test fun replyingStartsTheMessageWithTheMention() {
        assertEquals("@anna ", replyPrefix("anna"))
    }
}
