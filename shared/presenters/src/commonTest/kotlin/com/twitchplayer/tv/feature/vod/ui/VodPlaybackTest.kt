package com.twitchplayer.tv.feature.vod.ui

import com.twitchplayer.tv.core.platform.NumberFormatter
import kotlin.test.Test
import kotlin.test.assertEquals

class VodPlaybackTest {
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

    @Test fun aBackwardSeekNeverGoesBelowTheStart() {
        assertEquals(0L, seekTarget(0, -10_000, 600_000))
        assertEquals(0L, seekTarget(3_000, -1_000_000, 600_000))
    }

    @Test fun aSeekFromBeyondTheEndLandsOnTheEnd() {
        assertEquals(600_000L, seekTarget(900_000, 10_000, 600_000))
        assertEquals(600_000L, seekTarget(900_000, -10_000, 600_000))
    }

    @Test fun aSeekThatLandsExactlyOnTheEndStaysThere() {
        assertEquals(600_000L, seekTarget(590_000, 10_000, 600_000))
    }

    @Test fun anUnknownOrNegativeLengthMeansNoUpperLimit() {
        assertEquals(0L, seekTarget(2_000, -10_000, 0))
        assertEquals(50_000L, seekTarget(40_000, 10_000, -1))
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

    @Test fun theSpeedLabelAsksTheFormatterForTwoDecimalsAndAddsTheTimesSign() {
        val asked = mutableListOf<Pair<Float, Int>>()
        val formatter = NumberFormatter { value, digits ->
            asked += value to digits
            "<$value>"
        }
        assertEquals("<1.25>×", speedLabel(1.25f, formatter))
        assertEquals(listOf(1.25f to 2), asked)
    }
}
