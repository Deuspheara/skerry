package com.twitchplayer.phone.feature.player

import com.twitchplayer.tv.core.data.model.AnimatedEmoteLimit
import com.twitchplayer.tv.core.data.model.ChatDensity
import com.twitchplayer.tv.core.data.model.ChatSettings
import com.twitchplayer.tv.core.data.model.ChatTextSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatStyleTest {
    @Test fun settingsBecomeMeasurements() {
        val style = ChatSettings(textSize = ChatTextSize.Large, density = ChatDensity.Relaxed, backgroundOpacity = 40, animatedLimit = AnimatedEmoteLimit.Many).toPhoneStyle()
        assertEquals(17f, style.textSp, 0f)
        assertEquals(22f, style.lineHeightSp, 0f)
        assertEquals(30f, style.emoteDp, 0f)
        assertEquals(6f, style.gapDp, 0f)
        assertEquals(16, style.animatedLimit)
        assertEquals(0.4f, style.backgroundAlpha, 0.0001f)
    }

    @Test fun badgesStayLegibleAtEverySize() {
        ChatTextSize.entries.forEach { size ->
            val badge = ChatSettings(textSize = size).toPhoneStyle().badgeDp
            assertTrue("badge $badge for $size", badge in 15f..16f)
        }
    }

    @Test fun emotesGrowWithText() {
        val sizes = ChatTextSize.entries.map { ChatSettings(textSize = it).toPhoneStyle().emoteDp }
        assertEquals(sizes.sorted(), sizes)
    }
}
