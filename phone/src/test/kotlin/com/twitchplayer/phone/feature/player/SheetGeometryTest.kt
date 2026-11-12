package com.twitchplayer.phone.feature.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetGeometryTest {
    @Test fun progressIsZeroExpandedOneAsTheStripAndClampedBeyond() {
        assertEquals(0f, SheetGeometry.progress(0f, 1_800f), 0f)
        assertEquals(0.5f, SheetGeometry.progress(900f, 1_800f), 0f)
        assertEquals(1f, SheetGeometry.progress(1_800f, 1_800f), 0f)
        // Sliding off the bottom (closing) and a bounce above the top never leave 0..1.
        assertEquals(1f, SheetGeometry.progress(2_400f, 1_800f), 0f)
        assertEquals(0f, SheetGeometry.progress(-30f, 1_800f), 0f)
    }

    @Test fun aScreenTooShortForAStripIsTreatedAsTheStrip() {
        assertEquals(1f, SheetGeometry.progress(0f, 0f), 0f)
    }

    @Test fun theStripRestsAboveTheTabBarAndTheGestureBar() {
        // 2400 px screen, a 56 dp bar (+ inset) of 210 px, a strip of 168 px.
        assertEquals(2_022, SheetGeometry.collapsedOffset(2_400, 210, 168))
        assertEquals(0, SheetGeometry.collapsedOffset(100, 210, 168))
    }

    @Test fun theSheetShrinksFromTheWholeScreenToTheStrip() {
        assertEquals(2_400, SheetGeometry.sheetHeight(2_400, 168, 0f))
        assertEquals(168, SheetGeometry.sheetHeight(2_400, 168, 1f))
        assertEquals(1_284, SheetGeometry.sheetHeight(2_400, 168, 0.5f))
    }

    @Test fun theTextUnderThePictureIsGoneBeforeTheStripIsHalfWay() {
        assertEquals(1f, SheetGeometry.bodyAlpha(0f), 0f)
        assertEquals(0f, SheetGeometry.bodyAlpha(0.5f), 0f)
        assertEquals(0f, SheetGeometry.bodyAlpha(1f), 0f)
        assertTrue(SheetGeometry.bodyAlpha(0.25f) in 0.01f..0.99f)
    }

    @Test fun theStripFadesInOnlyAfterTheHalfWayPoint() {
        assertEquals(0f, SheetGeometry.stripAlpha(0f), 0f)
        assertEquals(0f, SheetGeometry.stripAlpha(0.5f), 0f)
        assertEquals(1f, SheetGeometry.stripAlpha(1f), 0f)
        assertEquals(0.5f, SheetGeometry.stripAlpha(0.75f), 0.001f)
    }

    @Test fun theControlsOverThePictureVanishAtTheVeryStartOfADrag() {
        assertEquals(1f, SheetGeometry.controlsAlpha(0f), 0f)
        assertEquals(0f, SheetGeometry.controlsAlpha(0.15f), 0f)
        assertEquals(0f, SheetGeometry.controlsAlpha(1f), 0f)
    }

    @Test fun theBodyAndTheStripAreNeverBothOpaque() {
        for (step in 0..100) {
            val p = step / 100f
            assertTrue("at $p", SheetGeometry.bodyAlpha(p) == 0f || SheetGeometry.stripAlpha(p) == 0f)
        }
    }

    @Test fun lerpIsExactAtTheEnds() {
        assertEquals(1_080, SheetGeometry.lerp(1_080, 300, 0f))
        assertEquals(300, SheetGeometry.lerp(1_080, 300, 1f))
    }

    @Test fun theStripGrowsWithTheFontUpToAPointAndNeverShrinks() {
        assertEquals(56f, SheetGeometry.stripHeight(0.85f).value, 0.01f)
        assertEquals(56f, SheetGeometry.stripHeight(1f).value, 0.01f)
        assertEquals(84f, SheetGeometry.stripHeight(1.5f).value, 0.01f)
        assertEquals(89.6f, SheetGeometry.stripHeight(2f).value, 0.01f)
        assertEquals(89.6f, SheetGeometry.stripHeight(3f).value, 0.01f)
    }

    @Test fun theStripPictureIsSixteenByNine() {
        assertEquals(99.5f, SheetGeometry.stripPictureWidth(SheetGeometry.stripHeight(1f)).value, 0.1f)
    }
}

class DismissPolicyTest {
    private val density = 2.75f

    @Test fun aSlowDownwardFlickOfTheStripIsNotAFling() {
        listOf(100f, 800f, 1_500f, 2_000f * density - 1f).forEach { assertEquals("$it", 0f, DismissPolicy.settleVelocity(true, it, density), 0f) }
    }

    @Test fun aFastFlickOfTheStripClosesIt() {
        val fast = 2_000f * density + 1f
        assertEquals(fast, DismissPolicy.settleVelocity(true, fast, density), 0f)
    }

    @Test fun anUpwardFlickOfTheStripAlwaysExpandsItAndTheExpandedSheetIsNeverHeld() {
        assertEquals(-300f, DismissPolicy.settleVelocity(true, -300f, density), 0f)
        assertEquals(900f, DismissPolicy.settleVelocity(false, 900f, density), 0f)
    }

    @Test fun theStripHasToBeDraggedMostOfTheWayOffScreenToCloseByDistance() {
        // 2400 px container: the strip's way to the edge is short (a 380 px stretch) and needs 65 %; the way up is long and needs 40 %.
        assertEquals(247f, DismissPolicy.positionalThreshold(380f, 2_400f, 0.4f), 0.5f)
        assertEquals(760f, DismissPolicy.positionalThreshold(1_900f, 2_400f, 0.4f), 0.5f)
    }
}
