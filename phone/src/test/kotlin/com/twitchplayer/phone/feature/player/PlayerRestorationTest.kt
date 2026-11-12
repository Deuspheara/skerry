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

    @Test fun aRestoredLockBehavesLikeAFreshOne() {
        val policy = FullscreenPolicy(initial = OrientationLock.Landscape)

        assertEquals(OrientationLock.Landscape, policy.lock)
        assertEquals(false, policy.onSensor(90))
        assertEquals(true, policy.onSensor(0))
        assertEquals(OrientationLock.None, policy.lock)
    }

    @Test fun aChoiceIsSavedAndSentToThePresenterOnce() {
        val restoration = PlayerRestoration()
        val sent = mutableListOf<ChatMode>()

        restoration.choose(ChatMode.Hidden, sent::add)

        assertEquals("Hidden", restoration.chatMode)
        assertEquals(listOf(ChatMode.Hidden), sent)
    }

    @Test fun everyChosenModeIsHandedBackWhateverThePresenterStartedOn() {
        // Including a choice equal to the presenter's placeholder (Hidden, until the saved default has loaded): comparing would drop it.
        for (chosen in ChatMode.entries) {
            assertEquals(chosen, PlayerRestoration(chatMode = chosen.name).toRestore())
        }
    }

    @Test fun aChoiceOfHiddenSurvivesAProcessDeathEvenThoughHiddenIsThePlaceholder() {
        val restored = roundTrip(PlayerRestoration().also { it.choose(ChatMode.Hidden) { } })

        assertEquals(ChatMode.Hidden, restored.toRestore())
    }

    @Test fun nothingIsHandedBackForAPageTheViewerNeverTouched() {
        assertNull(PlayerRestoration().toRestore())
    }

    @Test fun anUnknownSavedModeIsIgnored() {
        assertNull(PlayerRestoration(chatMode = "FromAnOlderBuild").toRestore())
    }

    @Test fun readingTheSavedModeNeverChangesIt() {
        val restoration = PlayerRestoration(chatMode = "Overlay")

        restoration.toRestore()

        assertEquals("Overlay", restoration.chatMode)
    }

    @Test fun theClipLengthSurvivesASaveAndRestoreAndAnOlderSaveWithoutOneStillLoads() {
        assertEquals(30_000L, roundTrip(PlayerRestoration(positionMs = 10_000L, durationMs = 30_000L)).durationMs)
        val older = with(PlayerRestoration.Saver) { restore(listOf("Overlay", "Landscape", 5_000L))!! }
        assertEquals(5_000L, older.positionMs)
        assertNull(older.durationMs)
    }
}
