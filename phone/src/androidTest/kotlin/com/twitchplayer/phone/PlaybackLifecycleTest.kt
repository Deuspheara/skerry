package com.twitchplayer.phone

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.twitchplayer.phone.feature.player.PauseInBackground
import com.twitchplayer.phone.feature.player.PositionSaver
import com.twitchplayer.phone.feature.player.RecordedPlayback
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The replay and clip player against a real Media3 player (audio focus off: a headless emulator would deny it and pause the player):
 * what it does when the app stops and starts, and that it never leaks when Compose forgets or abandons it.
 */
@RunWith(AndroidJUnit4::class)
class PlaybackLifecycleTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var playback: RecordedPlayback
    private lateinit var registry: LifecycleRegistry

    // A LifecycleRegistry keeps its owner only weakly: the test holds it for as long as it runs.
    private lateinit var owner: LifecycleOwner
    private var inPictureInPicture = false

    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)

    private fun <T> readOnMain(block: () -> T): T {
        var value: T? = null
        onMain { value = block() }
        @Suppress("UNCHECKED_CAST")
        return value as T
    }

    @Before fun setUp() {
        onMain {
            playback = RecordedPlayback(context, handleAudioFocus = false)
            owner = object : LifecycleOwner {
                override val lifecycle: Lifecycle get() = registry
            }
            registry = LifecycleRegistry.createUnsafe(owner)
            registry.addObserver(PauseInBackground(playback.exo) { inPictureInPicture })
            registry.currentState = Lifecycle.State.RESUMED
            // The bundled demo clip: a short looping test pattern.
            playback.setSource("asset:///demo_stream.mp4", 0L, recording = false, playWhenReady = true)
        }
        awaitUntil { readOnMain { playback.exo.isPlaying } }
    }

    @After fun tearDown() = onMain { playback.release() }

    private fun awaitUntil(timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            check(System.currentTimeMillis() < end) { "condition not met within $timeoutMs ms" }
            Thread.sleep(50)
        }
    }

    private fun stopTheApp() = onMain { registry.currentState = Lifecycle.State.CREATED }

    private fun startTheApp() = onMain { registry.currentState = Lifecycle.State.RESUMED }

    @Test fun aPlayingReplayPausesWhenTheAppIsStopped() {
        stopTheApp()

        assertFalse(readOnMain { playback.exo.playWhenReady })
    }

    @Test fun aReplayThatWasPlayingResumesWhenTheAppComesBack() {
        stopTheApp()
        startTheApp()

        assertTrue(readOnMain { playback.exo.playWhenReady })
    }

    @Test fun aReplayThePersonPausedStaysPausedWhenTheAppComesBack() {
        onMain { playback.exo.pause() }

        stopTheApp()
        startTheApp()

        assertFalse(readOnMain { playback.exo.playWhenReady })
    }

    @Test fun theVideoKeepsPlayingWhileTheAppIsInThePictureInPictureWindow() {
        inPictureInPicture = true

        stopTheApp()

        assertTrue(readOnMain { playback.exo.playWhenReady })
    }

    @Test fun aPlayerNobodyReferencesAnymoreIsReleasedWhenComposeForgetsIt() {
        val forgotten = readOnMain { RecordedPlayback(context, handleAudioFocus = false) }

        onMain { forgotten.onForgotten() }

        assertTrue(readOnMain { forgotten.isReleased })
    }

    @Test fun aPlayerFromACompositionThatWasAbandonedIsReleased() {
        val abandoned = readOnMain { RecordedPlayback(context, handleAudioFocus = false) }

        onMain { abandoned.onAbandoned() }

        assertTrue(readOnMain { abandoned.isReleased })
    }

    @Test fun releasingAPlayerTwiceIsHarmless() {
        val twice = readOnMain { RecordedPlayback(context, handleAudioFocus = false) }

        onMain {
            twice.release()
            twice.onForgotten()
            twice.onAbandoned()
        }

        assertTrue(readOnMain { twice.isReleased })
    }

    @Test fun aPauseSavesWhereTheReplayWas() {
        val saved = mutableListOf<Long>()
        onMain {
            playback.exo.addListener(PositionSaver(playback.exo) { saved += it })
            playback.exo.seekTo(2_000L)
            playback.exo.pause()
        }
        awaitUntil { saved.isNotEmpty() }

        assertTrue("saved a position near the seek: $saved", saved.last() >= 1_500L)
    }

    @Test fun stoppingTheAppSavesWhereTheReplayWas() {
        val saved = mutableListOf<Long>()
        onMain {
            registry.addObserver(PositionSaver(playback.exo) { saved += it })
            playback.exo.seekTo(2_000L)
        }
        awaitUntil { readOnMain { playback.exo.currentPosition >= 2_000L } }

        stopTheApp()

        assertTrue("saved when the app stopped: $saved", saved.isNotEmpty() && saved.last() >= 2_000L)
    }

    @Test fun nothingIsSavedBeforeAPlayerHasASource() {
        val saved = mutableListOf<Long>()
        val idle = readOnMain { RecordedPlayback(context, handleAudioFocus = false) }

        onMain { PositionSaver(idle.exo) { saved += it }.saveNow() }

        assertTrue(saved.isEmpty())
        onMain { idle.release() }
    }
}
