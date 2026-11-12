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

    @Test fun theLandscapeLockHoldsWhileThePhoneTurnsSideways() {
        val policy = FullscreenPolicy().apply { toggle(isLandscape = false) }
        assertFalse("still upright: the lock must hold or the screen flips straight back", policy.onSensor(0))
        // The turn passes through the angles where the app's reading used to release the lock too early.
        listOf(30, 45, 60, 75, 90, 270, 90).forEach { degrees -> assertFalse("$degrees", policy.onSensor(degrees)) }
        assertEquals(OrientationLock.Landscape, policy.lock)
    }

    @Test fun theLandscapeLockIsReleasedWhenThePhoneIsTurnedBackUpright() {
        listOf(0, 20, 340, 359, 180, 160, 200).forEach { upright ->
            val policy = FullscreenPolicy().apply { toggle(isLandscape = false) }
            assertFalse(policy.onSensor(90))
            assertTrue("$upright", policy.onSensor(upright))
            assertEquals(OrientationLock.None, policy.lock)
        }
    }

    @Test fun bothLandscapeSidesArmTheLandscapeLock() {
        listOf(70, 90, 110, 250, 270, 290).forEach { degrees ->
            val policy = FullscreenPolicy().apply { toggle(isLandscape = false) }
            assertFalse(policy.onSensor(degrees))
            assertTrue("$degrees", policy.onSensor(0))
        }
    }

    @Test fun anUprightPhoneNeverReleasesAFreshLandscapeLock() {
        val policy = FullscreenPolicy().apply { toggle(isLandscape = false) }
        listOf(0, 10, 350, 180).forEach { assertFalse("$it", policy.onSensor(it)) }
        assertEquals(OrientationLock.Landscape, policy.lock)
    }

    @Test fun thePortraitLockIsReleasedWhenTheyHoldItUpright() {
        listOf(0, 20, 340, 359, 180, 160, 200).forEach { degrees ->
            val policy = FullscreenPolicy().apply { toggle(isLandscape = true) }
            assertTrue("$degrees", policy.onSensor(degrees))
        }
        val policy = FullscreenPolicy().apply { toggle(isLandscape = true) }
        assertFalse("still sideways", policy.onSensor(90))
        assertFalse("turning, not upright yet", policy.onSensor(45))
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
