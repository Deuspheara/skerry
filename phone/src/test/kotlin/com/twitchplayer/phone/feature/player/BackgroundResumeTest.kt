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

    // The decision uses what the activity says at the moment of stopping, never a remembered flag. The cases below are the four ways
    // the app stops after a replay played in the Picture-in-Picture window.
    @Test fun dismissingThePictureInPictureWindowWithTheClosePausesAPlayingReplay() {
        val policy = BackgroundResume()
        // The window is gone when `onStop` runs, whatever an earlier "entered PiP" flag still says.
        assertTrue(policy.onStop(playing = true, inPictureInPicture = false))
        assertTrue("and coming back resumes it", policy.onStart())
    }

    @Test fun expandingThePictureInPictureWindowBackToTheAppPausesNothing() {
        val policy = BackgroundResume()
        assertFalse(policy.onStop(playing = true, inPictureInPicture = true))
    }

    @Test fun aNormalHomePressWithoutAnyWindowPausesAPlayingReplay() {
        assertTrue(BackgroundResume().onStop(playing = true, inPictureInPicture = false))
    }

    @Test fun lockingTheScreenPausesAPlayingReplayAndResumesItOnUnlock() {
        val policy = BackgroundResume()
        assertTrue(policy.onStop(playing = true, inPictureInPicture = false))
        assertTrue(policy.onStart())
    }
}
