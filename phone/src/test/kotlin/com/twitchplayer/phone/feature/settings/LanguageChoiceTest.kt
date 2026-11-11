package com.twitchplayer.phone.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageChoiceTest {
    private class FakeSwitcher(var tag: String? = null) : LanguageSwitcher {
        val calls = mutableListOf<String?>()

        override fun current(): String? = tag

        override fun set(tag: String?) {
            calls += tag
            this.tag = tag
        }
    }

    @Test fun theSystemChoiceComesFirstThenEveryLanguageInOrder() {
        val tags = languageTags(listOf("en", "fr", "pt-BR"))

        assertEquals(listOf(null, "en", "fr", "pt-BR"), tags)
        assertNull(tags.first())
    }

    @Test fun pickingAnotherLanguageSwitchesToIt() {
        val switcher = FakeSwitcher(tag = null)

        assertTrue(chooseLanguage(switcher, "fr"))

        assertEquals(listOf<String?>("fr"), switcher.calls)
        assertEquals("fr", switcher.current())
    }

    @Test fun pickingTheSystemLanguageClearsTheChoice() {
        val switcher = FakeSwitcher(tag = "de")

        assertTrue(chooseLanguage(switcher, null))

        assertEquals(listOf<String?>(null), switcher.calls)
        assertNull(switcher.current())
    }

    @Test fun pickingTheLanguageAlreadyInUseDoesNothing() {
        val switcher = FakeSwitcher(tag = "fr")

        assertFalse(chooseLanguage(switcher, "fr"))
        assertTrue(switcher.calls.isEmpty())
    }

    @Test fun theAppsOwnLanguageListHasNoDuplicatesAndStartsWithEnglish() {
        val supported = com.twitchplayer.tv.core.ui.AppLocales.supported

        assertEquals(supported.distinct(), supported)
        assertEquals("en", supported.first())
    }
}
