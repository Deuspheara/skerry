package com.twitchplayer.phone.feature.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MicPermissionFlowTest {
    private val flow = MicPermissionFlow()

    @Test fun aTapWithThePermissionStartsListening() {
        assertEquals(MicStep.StartVoice, flow.onMicTapped(permissionGranted = true))
        assertFalse(flow.denied)
    }

    @Test fun aTapWithoutThePermissionAsksForIt() {
        assertEquals(MicStep.AskPermission, flow.onMicTapped(permissionGranted = false))
        assertFalse(flow.denied)
    }

    @Test fun grantingAfterTheAskStartsListening() {
        flow.onMicTapped(permissionGranted = false)

        assertEquals(MicStep.StartVoice, flow.onPermissionResult(granted = true))
        assertFalse(flow.denied)
    }

    @Test fun refusingStartsNothingAndIsRemembered() {
        flow.onMicTapped(permissionGranted = false)

        assertEquals(MicStep.Nothing, flow.onPermissionResult(granted = false))
        assertTrue(flow.denied)
    }

    @Test fun theNextTapForgetsTheRefusalAndTriesAgain() {
        flow.onPermissionResult(granted = false)
        assertTrue(flow.denied)

        assertEquals(MicStep.AskPermission, flow.onMicTapped(permissionGranted = false))
        assertFalse(flow.denied)
    }

    @Test fun grantedInTheSystemSettingsLaterWorksOnTheNextTap() {
        flow.onPermissionResult(granted = false)

        assertEquals(MicStep.StartVoice, flow.onMicTapped(permissionGranted = true))
        assertFalse(flow.denied)
    }
}
