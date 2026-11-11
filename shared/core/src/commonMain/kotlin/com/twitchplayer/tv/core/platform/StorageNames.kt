package com.twitchplayer.tv.core.platform

/**
 * Names of what the app persists. They are part of the on-disk format: changing one orphans the user's data (settings,
 * replay positions, session, cached screens), so each is pinned by a test.
 */
object StorageNames {
    /** Preferences file of `SettingsRepository`. */
    const val SETTINGS_DATASTORE = "settings"

    /** Preferences file of `VodProgressStore`. */
    const val VOD_PROGRESS_DATASTORE = "vod_progress"

    /** Android: private preferences holding the encrypted OAuth tokens. */
    const val TOKEN_PREFERENCES = "twitch_secure_tokens"

    /** Directory (or key prefix) of the screen snapshots in the cache area. */
    const val SNAPSHOT_DIRECTORY = "snapshots"

    /** Key prefix of one replay's progress inside the VOD progress file ("vod_<id>" = "position:duration:savedAt"). */
    const val VOD_PROGRESS_KEY_PREFIX = "vod_"
}
