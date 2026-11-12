package com.twitchplayer.tv.core.data.repository

/**
 * What the app kept for the account and forgets whenever the session ends, by the viewer signing out or by Twitch refusing the stored
 * token for good: the screen snapshots, the recent searches and the recent emotes. Passed as [TwitchSessionRepository]'s `onSignedOut`.
 */
fun forgetAccountData(snapshots: SnapshotCache, settings: SettingsRepository): suspend () -> Unit = {
    snapshots.clear()
    settings.clearRecentSearches()
    settings.clearRecentEmotes()
}
