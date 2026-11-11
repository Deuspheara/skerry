package com.twitchplayer.phone.feature.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatScrollPolicyTest {
    private val ids = listOf("a", "b", "c", "d", "e")

    @Test fun countsLinesAfterTheAnchor() {
        assertEquals(2, ChatScrollPolicy.unseen(ids, "c"))
        assertEquals(0, ChatScrollPolicy.unseen(ids, "e"))
    }

    @Test fun noAnchorMeansNothingToCatchUpOn() {
        assertEquals(0, ChatScrollPolicy.unseen(ids, null))
    }

    @Test fun anAnchorThatLeftTheBufferMeansEverythingIsNew() {
        assertEquals(5, ChatScrollPolicy.unseen(ids, "gone"))
    }

    @Test fun thePillNeedsAViewerAwayFromTheNewestLineAndNewLines() {
        assertTrue(ChatScrollPolicy.showJumpPill(atNewest = false, unseen = 3))
        assertFalse("following the chat", ChatScrollPolicy.showJumpPill(atNewest = true, unseen = 3))
        assertFalse("nothing new yet", ChatScrollPolicy.showJumpPill(atNewest = false, unseen = 0))
    }

    @Test fun theBadgeCapsAtNinetyNine() {
        assertEquals("7", ChatScrollPolicy.badge(7))
        assertEquals("99", ChatScrollPolicy.badge(99))
        assertEquals("99+", ChatScrollPolicy.badge(100))
    }
}
