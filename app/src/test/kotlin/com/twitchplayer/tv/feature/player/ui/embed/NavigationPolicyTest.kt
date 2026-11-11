package com.twitchplayer.tv.feature.player.ui.embed

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationPolicyTest {
    @Test fun allowsOnlyHttpsOnConfiguredHost() {
        assertTrue(isAllowedNavigation("https", "zavori.app", "zavori.app"))
        assertFalse(isAllowedNavigation("http", "zavori.app", "zavori.app"))
        assertFalse(isAllowedNavigation("https", "evil.example", "zavori.app"))
        assertFalse(isAllowedNavigation(null, null, "zavori.app"))
    }
}
