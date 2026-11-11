package com.twitchplayer.phone.feature.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundResumeTest {
    @Test fun aPlayingReplayPausesWhenTheAppLeavesTheScreen() {
        assertTrue(BackgroundResume().onStop(playing = true, inPictureInPicture = false))
    }

    @Test fun aPlayingReplayResumesWhenTheAppComesBack() {
        val policy = BackgroundResume()
        policy.onStop(playing = true, inPictureInPicture = false)
        assertTrue(policy.onStart())
    }

    @Test fun aReplayThePersonPausedStaysPausedWhenTheAppComesBack() {
        val policy = BackgroundResume()
        assertTrue("it still pauses (it is already paused: harmless)", policy.onStop(playing = false, inPictureInPicture = false))
        assertFalse(policy.onStart())
    }

    @Test fun comingBackResumesOnlyOnceNotOnEveryLaterStart() {
        val policy = BackgroundResume()
        policy.onStop(playing = true, inPictureInPicture = false)
        assertTrue(policy.onStart())
        assertFalse("a second start without a stop in between", policy.onStart())
    }

    @Test fun theVideoKeepsPlayingInThePictureInPictureWindow() {
        val policy = BackgroundResume()
        assertFalse(policy.onStop(playing = true, inPictureInPicture = true))
        assertFalse("nothing was paused, so nothing resumes", policy.onStart())
    }

    @Test fun aStartWithoutAnyStopNeverStartsPlayback() {
        assertFalse(BackgroundResume().onStart())
    }

    @Test fun eachStopReplacesWhatTheLastOneRemembered() {
        val policy = BackgroundResume()
        policy.onStop(playing = true, inPictureInPicture = false)
        policy.onStop(playing = false, inPictureInPicture = false)
        assertFalse(policy.onStart())
    }
}
