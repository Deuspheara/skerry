package com.twitchplayer.phone.feature.player

/**
 * The chat list shows the newest line at the bottom and follows it. When the viewer scrolls up it stops following and a pill
 * offers "jump to latest" with the number of lines that arrived since.
 */
object ChatScrollPolicy {
    /**
     * Lines that arrived after [anchorId] (the newest line the viewer had seen when they scrolled up). [ids] are oldest first.
     * When the anchor already left the buffer every line is new.
     */
    fun unseen(ids: List<String>, anchorId: String?): Int {
        if (anchorId == null) return 0
        val index = ids.lastIndexOf(anchorId)
        return if (index < 0) ids.size else ids.size - index - 1
    }

    /** The pill shows only while the viewer is away from the newest line and something new arrived. */
    fun showJumpPill(atNewest: Boolean, unseen: Int): Boolean = !atNewest && unseen > 0

    /** "99+" instead of a long number once the buffer (200 lines) turns over. */
    fun badge(unseen: Int): String = if (unseen > MAX_BADGE) "$MAX_BADGE+" else unseen.toString()

    private const val MAX_BADGE = 99
}
