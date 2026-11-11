package com.twitchplayer.tv.feature.vod

import com.twitchplayer.tv.core.ui.parseTwitchDurationSeconds
import com.twitchplayer.tv.feature.vod.data.VodProgress
import com.twitchplayer.tv.feature.vod.ui.JUMP_MS
import com.twitchplayer.tv.feature.vod.ui.PLAYBACK_SPEEDS
import com.twitchplayer.tv.feature.vod.ui.nextSpeed
import com.twitchplayer.tv.feature.vod.ui.seekTarget
import com.twitchplayer.tv.feature.vod.ui.vodSeekStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VodModelTest {
    @Test fun aHeldKeySeeksFurtherAtEachStageUpToFiveMinutes() {
        val steps = listOf(0, 3, 4, 8, 12, 16, 20, 23, 24, 39, 40, 80).map { vodSeekStep(it) / 1000 }
        assertEquals(listOf(10L, 10L, 20L, 30L, 40L, 50L, 60L, 60L, 120L, 120L, 300L, 300L), steps)
        // Never shrinks while the key stays down.
        assertEquals(steps, steps.sorted())
    }

    @Test fun seeksStayInsideTheReplay() {
        assertEquals(0L, seekTarget(5_000, -10_000, 600_000))
        assertEquals(600_000L, seekTarget(595_000, 10_000, 600_000))
        assertEquals(70_000L, seekTarget(10_000, JUMP_MS, 600_000))
        // Unknown length: only the start is a limit.
        assertEquals(1_010_000L, seekTarget(1_000_000, 10_000, 0))
    }

    @Test fun speedCyclesThroughTheStepsAndWrapsToTheSlowest() {
        var speed = 1f
        val seen = mutableListOf<Float>()
        repeat(PLAYBACK_SPEEDS.size) {
            speed = nextSpeed(speed)
            seen += speed
        }
        assertEquals(listOf(1.25f, 1.5f, 2f, 0.5f, 0.75f, 1f), seen)
        assertEquals(0.5f, nextSpeed(2f), 0f)
        // A speed that is not a step (set elsewhere) goes to the next step above it.
        assertEquals(1.25f, nextSpeed(1.1f), 0f)
    }

    @Test fun progressIsOfferedOnlyAwayFromTheEnds() {
        assertFalse(VodProgress(5_000, 600_000).resumable)
        assertTrue(VodProgress(15_000, 600_000).resumable)
        assertTrue(VodProgress(300_000, 600_000).resumable)
        assertFalse(VodProgress(590_000, 600_000).resumable)
        // Length unknown: any position past the first seconds counts.
        assertTrue(VodProgress(120_000, 0).resumable)
        assertEquals(0.5f, VodProgress(300_000, 600_000).fraction, 0f)
        assertEquals(0f, VodProgress(300_000, 0).fraction, 0f)
        assertEquals(1f, VodProgress(900_000, 600_000).fraction, 0f)
    }

    @Test fun helixDurationsBecomeSeconds() {
        assertEquals(11_574, parseTwitchDurationSeconds("3h12m54s"))
        assertEquals(725, parseTwitchDurationSeconds("12m5s"))
        assertEquals(42, parseTwitchDurationSeconds("42s"))
        assertEquals(7_200, parseTwitchDurationSeconds("2h"))
        assertEquals(0, parseTwitchDurationSeconds(""))
        assertEquals(0, parseTwitchDurationSeconds("soon"))
    }
}
