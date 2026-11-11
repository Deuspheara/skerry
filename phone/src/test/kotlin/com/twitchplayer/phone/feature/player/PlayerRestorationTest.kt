package com.twitchplayer.phone.feature.player

import androidx.compose.runtime.saveable.SaverScope
import com.twitchplayer.tv.core.data.model.ChatMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerRestorationTest {
    private val everythingSaveable = SaverScope { true }

    private fun roundTrip(state: PlayerRestoration): PlayerRestoration = with(PlayerRestoration.Saver) {
        val saved = everythingSaveable.save(state)!!
        restore(saved)!!
    }

    @Test fun theChatModeTheLockAndThePositionSurviveASaveAndRestore() {
        val restored = roundTrip(PlayerRestoration(chatMode = "Overlay", lock = OrientationLock.Landscape, positionMs = 123_456L))

        assertEquals("Overlay", restored.chatMode)
        assertEquals(OrientationLock.Landscape, restored.lock)
        assertEquals(123_456L, restored.positionMs)
    }

    @Test fun anUntouchedPageRestoresAsUntouched() {
        val restored = roundTrip(PlayerRestoration())

        assertNull(restored.chatMode)
        assertEquals(OrientationLock.None, restored.lock)
        assertNull(restored.positionMs)
    }

    @Test fun anUnknownLockNameFromAnotherBuildFallsBackToNone() {
        val restored = with(PlayerRestoration.Saver) { restore(listOf("Compact", "Sideways", 5L))!! }

        assertEquals(OrientationLock.None, restored.lock)
        assertEquals(5L, restored.positionMs)
    }

    @Test fun aSavedChatModeNameBecomesTheModeAndAnythingElseIsIgnored() {
        assertEquals(ChatMode.Hidden, restoredChatMode("Hidden"))
        assertEquals(ChatMode.Overlay, restoredChatMode("Overlay"))
        assertNull(restoredChatMode(null))
        assertNull(restoredChatMode("Fullscreen"))
    }

    @Test fun aRestoredLockIsReleasedBySensorLikeAFreshOne() {
        val policy = FullscreenPolicy(initial = OrientationLock.Landscape)

        assertEquals(OrientationLock.Landscape, policy.lock)
        assertEquals(true, policy.onSensor(90))
        assertEquals(OrientationLock.None, policy.lock)
    }
}
