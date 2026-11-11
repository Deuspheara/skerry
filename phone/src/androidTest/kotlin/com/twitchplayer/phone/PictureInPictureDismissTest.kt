package com.twitchplayer.phone

import android.content.Context
import android.graphics.Rect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.twitchplayer.phone.feature.player.LocalPlayerHost
import com.twitchplayer.phone.feature.player.OrientationLock
import com.twitchplayer.phone.feature.player.PauseWhenInBackground
import com.twitchplayer.phone.feature.player.PlayerHost
import com.twitchplayer.phone.feature.player.RecordedPlayback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The real composable (`PauseWhenInBackground`) against a host whose "in Picture-in-Picture" flow is stale, as it is when the window
 * is dismissed with its X: Android stops the activity without telling it the window mode changed. What the activity answers right
 * now is what counts. (Driving the real X needs a window the emulator's shell cannot tap, so the host is the test seam.)
 */
@RunWith(AndroidJUnit4::class)
class PictureInPictureDismissTest {
    @get:Rule val rule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var playback: RecordedPlayback
    private lateinit var registry: LifecycleRegistry
    private lateinit var owner: LifecycleOwner
    private val host = StaleFlagHost()

    private class StaleFlagHost : PlayerHost {
        /** What the callback last reported: still true after the window was dismissed. */
        override val inPictureInPicture: StateFlow<Boolean> = MutableStateFlow(true)

        /** What the activity answers when asked now. */
        @Volatile var inWindowNow = true
        override fun isInPictureInPictureNow(): Boolean = inWindowNow
        override val canPictureInPicture: Boolean = true
        override fun updatePictureInPicture(eligible: Boolean, playing: Boolean, videoWidth: Int, videoHeight: Int, sourceBounds: Rect?, onTogglePlay: () -> Unit) = Unit
        override fun clearPictureInPicture() = Unit
        override fun enterPictureInPicture() = Unit
        override fun lockOrientation(lock: OrientationLock) = Unit
        override fun setVideoSystemBars(enabled: Boolean) = Unit
        override fun setImmersive(enabled: Boolean) = Unit
    }

    @Before fun setUp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            playback = RecordedPlayback(context, handleAudioFocus = false)
            owner = object : LifecycleOwner {
                override val lifecycle: Lifecycle get() = registry
            }
            registry = LifecycleRegistry.createUnsafe(owner)
            registry.currentState = Lifecycle.State.RESUMED
            playback.setSource("asset:///demo_stream.mp4", 0L, recording = false, playWhenReady = true)
        }
        rule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner, LocalPlayerHost provides host) {
                PauseWhenInBackground(playback.exo)
            }
        }
        rule.waitUntil(10_000) { onMain { playback.exo.isPlaying } }
    }

    @After fun tearDown() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { playback.release() }
    }

    private fun <T> onMain(block: () -> T): T {
        var value: T? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { value = block() }
        @Suppress("UNCHECKED_CAST")
        return value as T
    }

    private fun stopTheApp() = onMain { registry.currentState = Lifecycle.State.CREATED }

    @Test fun aReplayKeepsPlayingWhileTheWindowIsReallyThere() {
        host.inWindowNow = true

        stopTheApp()

        assertTrue(onMain { playback.exo.playWhenReady })
    }

    @Test fun dismissingTheWindowWithTheClosePausesEvenThoughTheFlagStillSaysPictureInPicture() {
        host.inWindowNow = false

        stopTheApp()

        assertFalse(onMain { playback.exo.playWhenReady })
    }
}
