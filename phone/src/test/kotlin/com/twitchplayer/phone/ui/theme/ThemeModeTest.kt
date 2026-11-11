package com.twitchplayer.phone.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {
    @Test fun darkAndLightIgnoreTheSystem() {
        assertTrue(ThemeMode.Dark.isDark(systemDark = false))
        assertTrue(ThemeMode.Dark.isDark(systemDark = true))
        assertFalse(ThemeMode.Light.isDark(systemDark = true))
        assertFalse(ThemeMode.Light.isDark(systemDark = false))
    }

    @Test fun followTheSystemFollowsIt() {
        assertTrue(ThemeMode.System.isDark(systemDark = true))
        assertFalse(ThemeMode.System.isDark(systemDark = false))
    }

    @Test fun aFreshInstallIsDark() {
        assertEquals(ThemeMode.Dark, ThemeMode.Default)
        assertEquals(ThemeMode.Dark, ThemeMode.fromStored(null))
    }

    @Test fun storedValuesRoundTripAndGarbageFallsBackToTheDefault() {
        ThemeMode.entries.forEach { assertEquals(it, ThemeMode.fromStored(it.name)) }
        assertEquals(ThemeMode.Default, ThemeMode.fromStored("sepia"))
        assertEquals(ThemeMode.Default, ThemeMode.fromStored(""))
    }
}
