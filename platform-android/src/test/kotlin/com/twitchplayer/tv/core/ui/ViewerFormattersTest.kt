package com.twitchplayer.tv.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/** On the JVM `android.icu` is a stub, so these cover the plain-digit path and the documented fallback; ICU output is seen on devices. */
class ViewerFormattersTest {
    @Test fun belowAThousandIsPlainDigits() {
        assertEquals("0", formatViewers(0, Locale.US))
        assertEquals("950", formatViewers(950, Locale.US))
        assertEquals("999", formatViewers(999, Locale.FRANCE))
    }

    @Test fun withoutIcuTheFallbackUsesTheLocaleDecimalAndAKSuffix() {
        assertEquals("14.8 k", formatViewers(14_820, Locale.US))
        assertEquals("14,8 k", formatViewers(14_820, Locale.FRANCE))
        assertEquals("1.0 k", formatViewers(1_000, Locale.US))
    }
}
