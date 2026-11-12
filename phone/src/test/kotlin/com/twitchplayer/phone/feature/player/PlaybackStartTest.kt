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

    @Test fun aRestoredPositionInTheMiddleIsKept() {
        assertEquals(1_200_000L, resumableRestoredPosition(1_200_000L, 3_600_000L))
    }

    @Test fun aRestoredPositionInTheLastFifteenSecondsRestartsFromTheBeginning() {
        assertEquals(null, resumableRestoredPosition(3_590_000L, 3_600_000L))
        assertEquals(StartDecision.Start(0), decideStart(null, resumableRestoredPosition(3_590_000L, 3_600_000L)))
    }

    @Test fun theExactEndMarginStillResumes() {
        assertEquals(3_585_000L, resumableRestoredPosition(3_585_000L, 3_600_000L))
    }

    @Test fun aRestoredPositionNearTheEndFallsBackToTheSavedProgressOffer() {
        assertEquals(StartDecision.Ask(saved), decideStart(saved, resumableRestoredPosition(3_599_000L, 3_600_000L)))
    }

    @Test fun anUnknownLengthKeepsTheRestoredPosition() {
        assertEquals(42_000L, resumableRestoredPosition(42_000L, null))
        assertEquals(42_000L, resumableRestoredPosition(42_000L, 0L))
    }

    @Test fun noRestoredPositionStaysNone() {
        assertEquals(null, resumableRestoredPosition(null, 3_600_000L))
    }

    @Test fun aShortClipRestoredNearItsEndRestartsToo() {
        assertEquals(null, resumableRestoredPosition(25_000L, 30_000L))
        assertEquals(10_000L, resumableRestoredPosition(10_000L, 30_000L))
    }
}
