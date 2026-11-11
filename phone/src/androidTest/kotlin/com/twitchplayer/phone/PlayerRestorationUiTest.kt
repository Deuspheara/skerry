package com.twitchplayer.phone

import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.twitchplayer.phone.feature.player.OrientationLock
import com.twitchplayer.phone.feature.player.PlayerRestoration
import com.twitchplayer.phone.feature.player.rememberPlayerRestoration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What a player page keeps when the system kills the app's process and brings the page back, simulated with Compose's
 * [StateRestorationTester] (it saves and restores the composition's saved-instance state exactly as the framework does).
 */
@RunWith(AndroidJUnit4::class)
class PlayerRestorationUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun theChatModeTheFullscreenLockAndTheReplayPositionComeBackAfterTheProcessWasKilled() {
        val tester = StateRestorationTester(rule)
        lateinit var restoration: PlayerRestoration
        tester.setContent { restoration = rememberPlayerRestoration("replay-1") }
        rule.runOnIdle {
            restoration.chatMode = "Overlay"
            restoration.lock = OrientationLock.Landscape
            restoration.positionMs = 91_000L
        }

        tester.emulateSavedInstanceStateRestore()

        rule.runOnIdle {
            assertEquals("Overlay", restoration.chatMode)
            assertEquals(OrientationLock.Landscape, restoration.lock)
            assertEquals(91_000L, restoration.positionMs)
        }
    }

    @Test fun aPageNobodyTouchedRestoresAsUntouched() {
        val tester = StateRestorationTester(rule)
        lateinit var restoration: PlayerRestoration
        tester.setContent { restoration = rememberPlayerRestoration("live-camille") }

        tester.emulateSavedInstanceStateRestore()

        rule.runOnIdle {
            assertNull(restoration.chatMode)
            assertEquals(OrientationLock.None, restoration.lock)
            assertNull(restoration.positionMs)
        }
    }
}
