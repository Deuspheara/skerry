package com.twitchplayer.phone.feature.player

import com.twitchplayer.tv.feature.vod.data.VodProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackStartTest {
    private val saved = VodProgress(positionMs = 600_000, durationMs = 3_600_000)

    @Test fun aRetryContinuesWhereItFailedWithoutAsking() {
        assertEquals(StartDecision.Start(42_000), decideStart(saved, continueAtMs = 42_000))
    }

    @Test fun aWorthwhileSavedPositionIsOffered() {
        assertEquals(StartDecision.Ask(saved), decideStart(saved, null))
    }

    @Test fun theFirstSecondsAreNotWorthResuming() {
        assertEquals(StartDecision.Start(0), decideStart(VodProgress(5_000, 3_600_000), null))
    }

    @Test fun theCreditsAreNotWorthResuming() {
        assertEquals(StartDecision.Start(0), decideStart(VodProgress(3_595_000, 3_600_000), null))
    }

    @Test fun noSavedPositionStartsAtTheBeginning() {
        assertEquals(StartDecision.Start(0), decideStart(null, null))
    }

    @Test fun progressIsSavedClearedOrSkipped() {
        assertEquals(ProgressAction.Skip, progressAction(started = false, ended = false))
        assertEquals(ProgressAction.Skip, progressAction(started = false, ended = true))
        assertEquals(ProgressAction.Save, progressAction(started = true, ended = false))
        assertEquals(ProgressAction.Clear, progressAction(started = true, ended = true))
    }

    @Test fun seekModelMapsBetweenFractionAndPosition() {
        assertEquals(0.25f, SeekModel.fractionOf(250, 1_000), 0.0001f)
        assertEquals(0f, SeekModel.fractionOf(250, 0), 0f)
        assertEquals(1f, SeekModel.fractionOf(5_000, 1_000), 0f)
        assertEquals(500L, SeekModel.positionFor(0.5f, 1_000))
        assertEquals(1_000L, SeekModel.positionFor(7f, 1_000))
        assertTrue(SeekModel.positionFor(-1f, 1_000) == 0L)
    }
}
