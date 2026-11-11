package com.twitchplayer.tv.feature.player.presentation

import androidx.compose.runtime.Immutable
import com.twitchplayer.tv.core.data.model.ChatMode
import com.twitchplayer.tv.core.data.model.LiveStream
import com.twitchplayer.tv.feature.player.data.chat.ChannelNotice
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** One-shot commands for the embedded player page (the page owns playback state). */
sealed interface PlayerCommand {
    data object Play : PlayerCommand
    data object Pause : PlayerCommand
    data object ToggleMute : PlayerCommand
}

/** What a row under the player shows: followed live channels, the current stream's category, or recommendations. */
sealed interface ZapRowKind {
    data object Following : ZapRowKind
    data class Category(val name: String) : ZapRowKind
    data object ForYou : ZapRowKind
}

@Immutable
data class ZapRow(val kind: ZapRowKind, val streams: ImmutableList<LiveStream>)

@Immutable
data class PlayerUiState(
    val current: LiveStream? = null,
    val login: String = "",
    val zapList: ImmutableList<LiveStream> = persistentListOf(),
    val chatMode: ChatMode = ChatMode.Hidden,
    val paused: Boolean = false,
    val muted: Boolean = false,
    val fullscreen: Boolean = false,
    /** False after ~4 s without input: the control bar fades out. */
    val controlsVisible: Boolean = true,
    /** The rows under the player are showing (Down from the controls; Up from the first row closes them). */
    val zapperOpen: Boolean = false,
    /** Rows after "Following", loaded when the rows first open for a channel. */
    val moreRows: ImmutableList<ZapRow> = persistentListOf(),
    /** A session exists (full screen or mini). False once the viewer closed it: the embed is released. */
    val active: Boolean = false,
    /** The stream keeps playing in the bottom-right mini player while another screen is shown. */
    val minimized: Boolean = false,
    /** Chat options are showing over the messages. */
    val chatSettingsOpen: Boolean = false,
    /** What just happened on the channel (raid, new title or category, stream ended); cleared after a while, never for "ended". */
    val notice: ChannelNotice? = null,
) {
    private val index: Int get() = zapList.indexOfFirst { it.login.equals(login, ignoreCase = true) }
    val canPrevious: Boolean get() = index > 0
    val canNext: Boolean get() = index >= 0 && index < zapList.lastIndex

    /** Chat mode actually drawn: full screen and the mini player suppress it without losing the user's choice. */
    val effectiveChat: ChatMode get() = if (fullscreen || minimized || !active) ChatMode.Hidden else chatMode
    val chatVisible: Boolean get() = effectiveChat != ChatMode.Hidden

    /** Every row under the player, top to bottom; empty rows are left out. */
    val zapRows: List<ZapRow> get() =
        (listOf(ZapRow(ZapRowKind.Following, zapList)) + moreRows).filter { it.streams.isNotEmpty() }
}

/** Wrap-around index for CH+/CH-; an unknown current channel (-1) starts from the first/last entry. */
internal fun cycleIndex(index: Int, delta: Int, size: Int): Int =
    if (index < 0) (if (delta > 0) 0 else size - 1) else Math.floorMod(index + delta, size)
