package com.twitchplayer.tv.feature.player.presentation

import com.twitchplayer.tv.core.data.model.ChatMode
import com.twitchplayer.tv.core.data.model.ChatSettings
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

/** Everything the viewer or the player screen can do (see [PlayerPresenter.onEvent]). */
sealed interface PlayerEvent {
    /** The player screen is on top for [login] (new session, resume from the mini player, or replace). */
    data class Attach(val login: String) : PlayerEvent

    /** No player screen is left in the back stack. */
    data object Detach : PlayerEvent
    data class SetMinimized(val minimized: Boolean) : PlayerEvent

    /** Ends the session: the embed is released and chat disconnects. */
    data object Close : PlayerEvent

    /** Any key: show the bar and restart the hide timer. */
    data object Wake : PlayerEvent
    data object TogglePause : PlayerEvent
    data object ToggleMute : PlayerEvent
    data object ToggleFullscreen : PlayerEvent

    /** Writing a message shows the chat first. */
    data object EnsureChatVisible : PlayerEvent
    data object CycleChat : PlayerEvent

    /** Shows chat in exactly [mode] for this session (the phone picks hidden / side panel / overlay directly; same rules as the chat button). */
    data class SetChatMode(val mode: ChatMode) : PlayerEvent
    data class UpdateChatSettings(val transform: (ChatSettings) -> ChatSettings) : PlayerEvent
    data object OpenChatSettings : PlayerEvent
    data object CloseChatSettings : PlayerEvent
    data object OpenZapper : PlayerEvent
    data object CloseZapper : PlayerEvent

    /** Back to the video alone: suggestions and the bottom overlay both close. */
    data object HideOverlay : PlayerEvent
    data object Previous : PlayerEvent
    data object Next : PlayerEvent
    data class Zap(val stream: LiveStream) : PlayerEvent
    data class SendChat(val text: String) : PlayerEvent

    /** An ad break is on screen (a notice's visible time only counts once it is over). */
    data class SetAdActive(val active: Boolean) : PlayerEvent
    data class SetPreferredQuality(val id: String) : PlayerEvent
}

/** What a row under the player shows: followed live channels, the current stream's category, or recommendations. */
sealed interface ZapRowKind {
    data object Following : ZapRowKind
    data class Category(val name: String) : ZapRowKind
    data object ForYou : ZapRowKind
}

data class ZapRow(val kind: ZapRowKind, val streams: ImmutableList<LiveStream>)

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
internal fun cycleIndex(index: Int, delta: Int, size: Int): Int = if (index < 0) (if (delta > 0) 0 else size - 1) else (index + delta).mod(size)
