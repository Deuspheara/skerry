package com.twitchplayer.phone.feature.player

import com.twitchplayer.tv.core.data.model.ChatMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatModeCycleTest {
    @Test fun portraitToggleIsHiddenOrThePanel() {
        assertEquals(ChatMode.Compact, nextChatMode(ChatMode.Hidden, landscape = false))
        assertEquals(ChatMode.Hidden, nextChatMode(ChatMode.Compact, landscape = false))
        assertEquals("a saved overlay choice closes the panel", ChatMode.Hidden, nextChatMode(ChatMode.Overlay, landscape = false))
    }

    @Test fun landscapeCyclesThroughAllThree() {
        assertEquals(ChatMode.Compact, nextChatMode(ChatMode.Hidden, landscape = true))
        assertEquals(ChatMode.Overlay, nextChatMode(ChatMode.Compact, landscape = true))
        assertEquals(ChatMode.Hidden, nextChatMode(ChatMode.Overlay, landscape = true))
    }

    @Test fun overTheVideoOnlyExistsInLandscape() {
        assertEquals(ChatMode.Compact, layoutChatMode(ChatMode.Overlay, landscape = false))
        assertEquals(ChatMode.Overlay, layoutChatMode(ChatMode.Overlay, landscape = true))
        assertEquals(ChatMode.Hidden, layoutChatMode(ChatMode.Hidden, landscape = false))
        assertEquals(ChatMode.Compact, layoutChatMode(ChatMode.Compact, landscape = true))
    }

    @Test fun chatOverTheVideoNeedsLandscapeTheOverlayModeASignedInViewerAndNoPip() {
        assertTrue(overlayChatVisible(ChatMode.Overlay, landscape = true, signedIn = true, inPip = false))
        assertFalse("portrait draws the panel instead", overlayChatVisible(ChatMode.Overlay, landscape = false, signedIn = true, inPip = false))
        assertFalse("side panel mode", overlayChatVisible(ChatMode.Compact, landscape = true, signedIn = true, inPip = false))
        assertFalse("signed out", overlayChatVisible(ChatMode.Overlay, landscape = true, signedIn = false, inPip = false))
        assertFalse("the Picture-in-Picture window shows the picture alone", overlayChatVisible(ChatMode.Overlay, landscape = true, signedIn = true, inPip = true))
    }
}
