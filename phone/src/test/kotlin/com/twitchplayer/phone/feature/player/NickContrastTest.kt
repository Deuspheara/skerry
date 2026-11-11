package com.twitchplayer.phone.feature.player

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NickContrastTest {
    private val lightSurface = Color(0xFFFAF8FF)
    private val darkSurface = Color(0xFF0E0E10)

    @Test fun blackOnWhiteIsTwentyOne() {
        assertEquals(21f, contrastRatio(Color.Black, Color.White), 0.01f)
        assertEquals(1f, contrastRatio(Color.White, Color.White), 0.001f)
    }

    @Test fun aColourThatAlreadyReadsIsKeptAsItIs() {
        val purple = Color(0xFFBF94FF)
        assertEquals(purple, readableOn(darkSurface, purple))
        val deepBlue = Color(0xFF1A237E)
        assertEquals(deepBlue, readableOn(lightSurface, deepBlue))
    }

    @Test fun aPastelOnALightSurfaceIsDarkenedUntilItReads() {
        val pastel = Color(0xFFFFB6C1)
        val fixed = readableOn(lightSurface, pastel)
        assertTrue("contrast ${contrastRatio(fixed, lightSurface)}", contrastRatio(fixed, lightSurface) >= 4.5f)
        assertTrue("it stays a pinkish colour, not black", fixed != Color.Black && fixed.red > fixed.green)
    }

    @Test fun aDarkColourOnADarkSurfaceIsLightened() {
        val navy = Color(0xFF1A1A40)
        val fixed = readableOn(darkSurface, navy)
        assertTrue(contrastRatio(fixed, darkSurface) >= 4.5f)
    }

    @Test fun everyNickColourThePresenterCanProduceReadsOnBothThemes() {
        // The shared mapping keeps colours readable on dark; sample the hue wheel at several lightness levels.
        for (hue in 0 until 360 step 30) {
            for (lightness in listOf(0.55f, 0.7f, 0.85f, 0.95f)) {
                val color = Color.hsl(hue.toFloat(), 0.8f, lightness)
                assertTrue("light $hue/$lightness", contrastRatio(readableOn(lightSurface, color), lightSurface) >= 4.5f)
                assertTrue("dark $hue/$lightness", contrastRatio(readableOn(darkSurface, color), darkSurface) >= 4.5f)
            }
        }
    }
}
