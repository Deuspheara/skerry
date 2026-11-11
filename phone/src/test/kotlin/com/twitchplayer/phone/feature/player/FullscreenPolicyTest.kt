package com.twitchplayer.phone.feature.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FullscreenPolicyTest {
    @Test fun portraitToggleLocksLandscape() {
        val policy = FullscreenPolicy()
        policy.toggle(isLandscape = false)
        assertEquals(OrientationLock.Landscape, policy.lock)
    }

    @Test fun landscapeToggleLocksPortrait() {
        val policy = FullscreenPolicy()
        policy.toggle(isLandscape = true)
        assertEquals(OrientationLock.Portrait, policy.lock)
    }

    @Test fun theLandscapeLockIsReleasedOnlyWhenThePhoneIsHeldSideways() {
        val policy = FullscreenPolicy().apply { toggle(isLandscape = false) }
        assertFalse("still upright: the lock must hold or the screen flips straight back", policy.onSensor(0))
        assertEquals(OrientationLock.Landscape, policy.lock)
        assertTrue(policy.onSensor(90))
        assertEquals(OrientationLock.None, policy.lock)
    }

    @Test fun bothLandscapeSidesRelease() {
        listOf(60, 90, 120, 240, 270, 300).forEach { degrees ->
            val policy = FullscreenPolicy().apply { toggle(isLandscape = false) }
            assertTrue("$degrees", policy.onSensor(degrees))
        }
    }

    @Test fun thePortraitLockIsReleasedWhenTheyHoldItUpright() {
        listOf(0, 20, 340, 359, 180, 160, 200).forEach { degrees ->
            val policy = FullscreenPolicy().apply { toggle(isLandscape = true) }
            assertTrue("$degrees", policy.onSensor(degrees))
        }
        val policy = FullscreenPolicy().apply { toggle(isLandscape = true) }
        assertFalse("still sideways", policy.onSensor(90))
    }

    @Test fun unknownSensorValuesNeverRelease() {
        val policy = FullscreenPolicy().apply { toggle(isLandscape = false) }
        assertFalse(policy.onSensor(FullscreenPolicy.UNKNOWN))
        assertEquals(OrientationLock.Landscape, policy.lock)
    }

    @Test fun withoutALockTheSensorChangesNothing() {
        val policy = FullscreenPolicy()
        assertFalse(policy.onSensor(90))
        assertEquals(OrientationLock.None, policy.lock)
    }

    @Test fun resetGivesTheOrientationBackToTheSystem() {
        val policy = FullscreenPolicy().apply { toggle(isLandscape = false) }
        policy.reset()
        assertEquals(OrientationLock.None, policy.lock)
    }
}
