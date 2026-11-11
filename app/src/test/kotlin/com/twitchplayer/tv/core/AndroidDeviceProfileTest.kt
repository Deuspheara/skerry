package com.twitchplayer.tv.core

import com.twitchplayer.tv.core.di.AndroidDeviceProfile
import com.twitchplayer.tv.feature.category.presentation.twitchLanguage
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class AndroidDeviceProfileTest {
    @Test fun theTagIsTheLocalesBcp47Tag() {
        assertEquals("fr-FR", AndroidDeviceProfile { Locale.FRANCE }.languageTag)
        assertEquals("zh-HK", AndroidDeviceProfile { Locale.forLanguageTag("zh-HK") }.languageTag)
        assertEquals("fil", AndroidDeviceProfile { Locale.forLanguageTag("fil") }.languageTag)
    }

    @Test fun theRootLocaleGivesAnEmptyTagLikeLocaleLanguageDid() {
        assertEquals("", AndroidDeviceProfile { Locale.ROOT }.languageTag)
        assertEquals("same filter value as the old `locale.language`", "", twitchLanguage(AndroidDeviceProfile { Locale.ROOT }.languageTag))
        assertEquals("the pitfall this guards against", "und", Locale.ROOT.toLanguageTag())
    }

    @Test fun theProfileFollowsTheCurrentLocale() {
        var current = Locale.ENGLISH
        val profile = AndroidDeviceProfile { current }
        assertEquals("en", profile.languageTag)
        current = Locale.GERMANY
        assertEquals("de-DE", profile.languageTag)
    }
}
