package com.twitchplayer.phone.feature.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundResumeTest {
    @Test fun aPlayingReplayResumesWhenTheAppComesBack() {
        val policy = BackgroundResume()
        policy.onStop(playing = true)
        assertTrue(policy.onStart())
    }

    @Test fun aReplayThePersonPausedStaysPausedWhenTheAppComesBack() {
        val policy = BackgroundResume()
        policy.onStop(playing = false)
        assertFalse(policy.onStart())
    }

    @Test fun comingBackResumesOnlyOnceNotOnEveryLaterStart() {
        val policy = BackgroundResume()
        policy.onStop(playing = true)
        assertTrue(policy.onStart())
        assertFalse("a second start without a stop in between", policy.onStart())
    }

    @Test fun aStartWithoutAnyStopNeverStartsPlayback() {
        assertFalse(BackgroundResume().onStart())
    }

    @Test fun eachStopReplacesWhatTheLastOneRemembered() {
        val policy = BackgroundResume()
        policy.onStop(playing = true)
        policy.onStop(playing = false)
        assertFalse(policy.onStart())
    }

    @Test fun lockingTheScreenAndUnlockingResumesAPlayingReplay() {
        val policy = BackgroundResume()
        policy.onStop(playing = true)
        assertTrue(policy.onStart())
    }
}
