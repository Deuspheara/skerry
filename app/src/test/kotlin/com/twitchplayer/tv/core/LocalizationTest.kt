package com.twitchplayer.tv.core

import com.twitchplayer.tv.feature.category.presentation.twitchLanguage
import com.twitchplayer.tv.feature.search.ui.keyboardRows
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class LocalizationTest {
    @Test fun keyboardFollowsLanguage() {
        assertEquals("a", keyboardRows("fr")[1][0])
        assertEquals("z", keyboardRows("de")[1][5])
        assertEquals("q", keyboardRows("en")[1][0])
        assertEquals("ñ", keyboardRows("es")[3][7])
        // Every layout keeps 10 columns so the grid and its focus paths never change shape.
        listOf("fr", "de", "en", "es", "sv", "ja", "ru").forEach { lang -> keyboardRows(lang).forEach { assertEquals(lang, 10, it.size) } }
    }

    @Test fun twitchLanguageCodes() {
        assertEquals("fr", twitchLanguage(Locale.FRANCE))
        assertEquals("no", twitchLanguage(Locale.forLanguageTag("nb-NO")))
        assertEquals("zh-hk", twitchLanguage(Locale.forLanguageTag("zh-HK")))
        assertEquals("tl", twitchLanguage(Locale.forLanguageTag("fil")))
    }
}
