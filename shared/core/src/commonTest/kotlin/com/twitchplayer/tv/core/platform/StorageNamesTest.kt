package com.twitchplayer.tv.core.platform

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The literals below are deliberate copies: these names are the on-disk format. If this test fails because a constant
 * changed, existing installs lose their data; add a migration instead of editing the test.
 */
class StorageNamesTest {
    @Test fun persistedNamesAreStable() {
        assertEquals("settings", StorageNames.SETTINGS_DATASTORE)
        assertEquals("vod_progress", StorageNames.VOD_PROGRESS_DATASTORE)
        assertEquals("twitch_secure_tokens", StorageNames.TOKEN_PREFERENCES)
        assertEquals("snapshots", StorageNames.SNAPSHOT_DIRECTORY)
        assertEquals("vod_", StorageNames.VOD_PROGRESS_KEY_PREFIX)
    }
}
