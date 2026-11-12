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
}
