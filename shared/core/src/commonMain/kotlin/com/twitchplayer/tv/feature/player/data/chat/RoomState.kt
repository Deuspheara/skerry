package com.twitchplayer.tv.feature.player.data.chat

/** The room's modes (slow, followers-only, subscribers-only, emote-only, unique chat); `null` / false = off. */
data class RoomState(
    val emoteOnly: Boolean = false,
    /** Minutes a viewer must have followed; 0 = any follower; null = off. */
    val followersOnlyMinutes: Int? = null,
    /** Seconds between two messages of one viewer; null = off. */
    val slowSeconds: Int? = null,
    val subscribersOnly: Boolean = false,
    val uniqueOnly: Boolean = false,
) {
    val any: Boolean get() = emoteOnly || followersOnlyMinutes != null || slowSeconds != null || subscribersOnly || uniqueOnly
}
