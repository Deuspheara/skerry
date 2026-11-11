package com.twitchplayer.phone.feature.browse

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class LanguageLabelTest {
    @Test fun showsTheLanguageNameInTheDisplayLanguage() {
        assertEquals("English", languageLabel("en", Locale.ENGLISH))
        assertEquals("French", languageLabel("fr", Locale.ENGLISH))
        assertEquals("Anglais", languageLabel("en", Locale.FRENCH))
        assertEquals("Français", languageLabel("fr", Locale.FRENCH))
    }

    @Test fun anUnknownCodeIsShownUpperCasedNotBlank() {
        assertEquals("XX", languageLabel("xx", Locale.ENGLISH))
    }
}
