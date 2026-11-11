package com.twitchplayer.phone.feature.player

import com.twitchplayer.tv.core.data.model.ChatMode

/**
 * The chat button. In portrait chat is a panel under the video, so the button toggles it; in landscape it cycles hidden, side panel
 * and over the video.
 */
fun nextChatMode(current: ChatMode, landscape: Boolean): ChatMode = if (!landscape) {
    if (current == ChatMode.Hidden) ChatMode.Compact else ChatMode.Hidden
} else {
    when (current) {
        ChatMode.Hidden -> ChatMode.Compact
        ChatMode.Compact -> ChatMode.Overlay
        ChatMode.Overlay -> ChatMode.Hidden
    }
}

/** Chat laid over the picture: only in landscape, in the "over the video" mode, for a viewer who can read chat, outside Picture-in-Picture. */
fun overlayChatVisible(mode: ChatMode, landscape: Boolean, signedIn: Boolean, inPip: Boolean): Boolean = landscape && signedIn && !inPip && layoutChatMode(mode, landscape = true) == ChatMode.Overlay

/** What is drawn: "over the video" has no meaning in portrait, where it shows as the panel. */
fun layoutChatMode(mode: ChatMode, landscape: Boolean): ChatMode = if (!landscape && mode == ChatMode.Overlay) ChatMode.Compact else mode
