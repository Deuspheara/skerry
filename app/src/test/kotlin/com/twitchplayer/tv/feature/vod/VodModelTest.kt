package com.twitchplayer.tv.feature.vod

import com.twitchplayer.tv.core.di.AndroidNumberFormatter
import com.twitchplayer.tv.feature.player.data.stream.VodAccessException
import com.twitchplayer.tv.feature.player.data.stream.VodFailure
import com.twitchplayer.tv.feature.vod.ui.PLAYBACK_SPEEDS
import com.twitchplayer.tv.feature.vod.ui.VodError
import com.twitchplayer.tv.feature.vod.ui.speedLabel
import com.twitchplayer.tv.feature.vod.ui.toVodError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.Locale

class VodModelTest {
    @Test fun errorsMapToTheScreensMessages() {
        assertEquals(VodError.Gone, VodAccessException(VodFailure.NOT_FOUND, "gql").toVodError())
        assertEquals(VodError.SubscribersOnly, VodAccessException(VodFailure.SUBSCRIBER_ONLY, "usher", 403).toVodError())
        assertEquals(VodError.Refused, VodAccessException(VodFailure.TOKEN, "gql", integrity = true).toVodError())
        assertEquals(VodError.Refused, VodAccessException(VodFailure.UNAVAILABLE, "usher", 503).toVodError())
        assertEquals(VodError.Network, IOException("x").toVodError())
        assertFalse(VodError.Gone.retryable)
        assertFalse(VodError.SubscribersOnly.retryable)
        assertTrue(VodError.Network.retryable)
    }

    @Test fun speedLabelsKeepTheirHistoricalTextUnderTheDeviceLocale() {
        val english = AndroidNumberFormatter { Locale.US }
        assertEquals(listOf("0.5×", "0.75×", "1×", "1.25×", "1.5×", "2×"), PLAYBACK_SPEEDS.map { speedLabel(it, english) })
        val french = AndroidNumberFormatter { Locale.FRANCE }
        assertEquals(listOf("0,5×", "0,75×", "1×", "1,25×", "1,5×", "2×"), PLAYBACK_SPEEDS.map { speedLabel(it, french) })
    }

    @Test fun theFormatterRoundsToTheAskedDecimalsAndFollowsTheCurrentLocale() {
        var locale = Locale.US
        val formatter = AndroidNumberFormatter { locale }
        assertEquals("1.33", formatter.decimal(1.3333f, 2))
        assertEquals("1.3", formatter.decimal(1.3333f, 1))
        assertEquals("2", formatter.decimal(2f, 2))
        locale = Locale.GERMANY
        assertEquals("1,33", formatter.decimal(1.3333f, 2))
    }

    @Test fun theOutputEqualsTheOldInlineDecimalFormat() {
        for (locale in listOf(Locale.US, Locale.FRANCE, Locale.GERMANY, Locale("ar", "EG"))) {
            val old = { speed: Float -> java.text.DecimalFormat("0.##", java.text.DecimalFormatSymbols(locale)).format(speed) + "×" }
            val formatter = AndroidNumberFormatter { locale }
            for (speed in PLAYBACK_SPEEDS + listOf(1.1f, 0.333f, 3f)) assertEquals("$locale $speed", old(speed), speedLabel(speed, formatter))
        }
    }
}
