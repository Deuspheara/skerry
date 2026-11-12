package com.twitchplayer.tv.core.data.repository

import com.twitchplayer.tv.feature.vod.data.VodProgressStore

/**
 * What the app kept for the account and forgets whenever the session ends, by the viewer signing out or by Twitch refusing the stored
 * token for good: the screen snapshots (which hold the stored profile pictures and last-seen times), the recent searches and emotes, the
 * replay positions behind "continue watching", and what the [scoped] objects hold in memory. Passed as [TwitchSessionRepository]'s
 * `onSignedOut`; [scoped] is read when the session ends, not when this is built (the repository needs the session, the session needs this).
 */
fun forgetAccountData(
    snapshots: SnapshotCache,
    settings: SettingsRepository,
    vodProgress: VodProgressStore,
    scoped: () -> List<AccountScoped> = { emptyList() },
): suspend () -> Unit = {
    snapshots.clear()
    settings.clearRecentSearches()
    settings.clearRecentEmotes()
    vodProgress.clearAll()
    scoped().forEach { it.forgetAccount() }
}
