package com.twitchplayer.phone.feature.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatInputSpecTest {
    @Test fun aBlankMessageIsNeverSent() {
        assertNull(ChatInputSpec.outgoing(""))
        assertNull(ChatInputSpec.outgoing("   \n "))
        assertFalse(ChatInputSpec.canSend("  "))
    }

    @Test fun aMessageIsTrimmedBeforeItIsSent() {
        assertEquals("hello", ChatInputSpec.outgoing("  hello \n"))
        assertTrue(ChatInputSpec.canSend(" a "))
    }

    @Test fun aMessageIsCutAtTwitchsLimit() {
        assertEquals(500, ChatInputSpec.limit("x".repeat(900)).length)
        assertEquals("short", ChatInputSpec.limit("short"))
    }

    @Test fun thePillIsCompactAndTheSendTargetStaysTouchSized() {
        assertEquals(40f, ChatInputSpec.PILL_HEIGHT.value, 0f)
        assertEquals(52f, ChatInputSpec.ROW_HEIGHT.value, 0f)
        assertTrue(ChatInputSpec.SEND_TARGET.value >= 48f)
        assertTrue(ChatInputSpec.SEND_CIRCLE < ChatInputSpec.SEND_TARGET)
    }
}
