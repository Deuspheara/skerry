package com.twitchplayer.phone.ui.theme

import androidx.compose.ui.graphics.Color
import com.twitchplayer.phone.feature.player.contrastRatio
import com.twitchplayer.phone.feature.player.readableOn
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WCAG contrast of the colour pairs the screens actually use, computed from the theme's own tokens (no estimates from screenshots):
 * 4.5:1 for text and small glyphs, 3:1 for outlines and other meaningful graphics. A token change that drops a pair fails here.
 */
class PhoneContrastTest {
    private val schemes = mapOf("dark" to DarkScheme, "light" to LightScheme)

    private fun check(name: String, minimum: Float, vararg pairs: Pair<Color, Color>) = pairs.forEachIndexed { index, (foreground, background) ->
        val ratio = contrastRatio(foreground, background)
        assertTrue("$name pair #$index is $ratio:1, needs $minimum:1", ratio >= minimum)
    }

    @Test fun bodyTextReadsOnThePageAndOnTonalSurfaces() {
        schemes.forEach { (name, s) ->
            check(
                name,
                4.5f,
                s.onBackground to s.background,
                s.onSurface to s.surface,
                s.onSurfaceVariant to s.background,
                s.onSurfaceVariant to s.surfaceContainer,
                s.onSurfaceVariant to s.surfaceContainerHigh,
                s.onSurface to s.surfaceContainerHigh,
            )
        }
    }

    @Test fun accentTextAndFilledButtonsReachAA() {
        schemes.forEach { (name, s) ->
            check(
                name,
                4.5f,
                s.primary to s.background,
                s.primary to s.surfaceContainer,
                s.onPrimary to s.primary,
                s.onPrimaryContainer to s.primaryContainer,
                s.error to s.background,
                s.onError to s.error,
            )
        }
    }

    @Test fun outlinesAndDividersAreVisibleEnoughToBeGraphics() {
        schemes.forEach { (name, s) -> check(name, 3f, s.outline to s.background) }
    }

    @Test fun theLiveBadgeKeepsItsWhiteTextAtAA() {
        assertTrue(contrastRatio(PhoneColors.OnLive, PhoneColors.Live) >= 4.5f)
    }

    @Test fun nicknamesAreMovedUntilTheyReachAAOnBothPages() {
        val samples = listOf(Color(0xFFFF69B4), Color(0xFF00CED1), Color(0xFFADFF2F), Color(0xFFFFD700), Color(0xFF9ACD32), Color(0xFFB8860B), Color(0xFFDA70D6))
        mapOf("dark" to DarkScheme.background, "light" to LightScheme.background, "over video" to Color.Black).forEach { (name, surface) ->
            samples.forEach { nick ->
                val ratio = contrastRatio(readableOn(surface, nick), surface)
                assertTrue("a nickname on $name is $ratio:1", ratio >= 4.5f)
            }
        }
    }
}
