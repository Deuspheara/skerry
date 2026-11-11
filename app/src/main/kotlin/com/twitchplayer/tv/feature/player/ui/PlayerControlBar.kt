package com.twitchplayer.tv.feature.player.ui

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.twitchplayer.tv.R
import com.twitchplayer.tv.core.data.model.ChatMode
import com.twitchplayer.tv.core.designsystem.component.ControlBarButton
import com.twitchplayer.tv.core.designsystem.icon.TvIcons
import com.twitchplayer.tv.core.designsystem.theme.TvDimens
import com.twitchplayer.tv.feature.player.presentation.PlayerUiState

/** Identity of each control, used to remember which one was focused last. */
enum class PlayerControl { Back, Pause, Sound, Fullscreen, Up, Down, Chat, Write, Quality }

/**
 * Fixed-position control bar (left-anchored at the safe margin; the chat mode never moves buttons).
 * [focus] holds a requester per control so the screen can return to the last used one.
 */
@Composable
fun PlayerControlBar(
    state: PlayerUiState,
    focus: Map<PlayerControl, FocusRequester>,
    onFocused: (PlayerControl) -> Unit,
    onBack: () -> Unit,
    onPause: () -> Unit,
    onPauseLong: () -> Unit,
    onMute: () -> Unit,
    onFullscreen: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onChat: () -> Unit,
    onQuality: () -> Unit,
    onWrite: () -> Unit,
    /** The chat column, when it is showing: Right from the last button goes there (and nowhere else moves focus to chat). */
    chatTarget: FocusRequester?,
    onBackLeftTarget: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    val controls = stringResource(R.string.player_controls)
    fun Modifier.control(which: PlayerControl) = this
        .focusRequester(focus.getValue(which))
        .onFocusChanged { if (it.hasFocus) onFocused(which) }

    Row(
        modifier.height(TvDimens.ControlButton).focusGroup().semantics { contentDescription = controls },
        horizontalArrangement = Arrangement.spacedBy(TvDimens.ControlGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ControlBarButton(
            TvIcons.ChevronLeft,
            stringResource(R.string.player_back),
            onBack,
            Modifier.control(PlayerControl.Back).then(if (onBackLeftTarget != null) Modifier.focusProperties { left = onBackLeftTarget } else Modifier),
        )
        Spacer(Modifier.width(TvDimens.ControlGap * 2))
        ControlBarButton(
            if (state.paused) TvIcons.Play else TvIcons.Pause,
            stringResource(if (state.paused) R.string.player_play else R.string.player_pause),
            onPause,
            Modifier.control(PlayerControl.Pause),
            onLongClick = onPauseLong,
        )
        ControlBarButton(
            if (state.muted) TvIcons.Mute else TvIcons.Volume,
            stringResource(if (state.muted) R.string.player_muted else R.string.player_sound),
            onMute,
            Modifier.control(PlayerControl.Sound),
        )
        ControlBarButton(
            if (state.fullscreen) TvIcons.Minimize else TvIcons.Maximize,
            stringResource(if (state.fullscreen) R.string.player_exit_fullscreen else R.string.player_fullscreen),
            onFullscreen,
            Modifier.control(PlayerControl.Fullscreen),
        )
        Spacer(Modifier.width(TvDimens.ControlGap * 2))
        ControlBarButton(TvIcons.ArrowUp, stringResource(R.string.player_next_up), onPrevious, Modifier.control(PlayerControl.Up), enabled = state.canPrevious)
        ControlBarButton(TvIcons.ArrowDown, stringResource(R.string.player_next_down), onNext, Modifier.control(PlayerControl.Down), enabled = state.canNext)
        Spacer(Modifier.width(TvDimens.ControlGap * 2))
        ControlBarButton(
            when (state.chatMode) {
                ChatMode.Hidden -> TvIcons.ChatOff
                ChatMode.Compact -> TvIcons.ChatCompact
                ChatMode.Overlay -> TvIcons.ChatOverlay
            },
            stringResource(R.string.player_chat),
            onChat,
            Modifier.control(PlayerControl.Chat),
        )
        ControlBarButton(TvIcons.Edit, stringResource(R.string.chat_write), onWrite, Modifier.control(PlayerControl.Write))
        ControlBarButton(
            TvIcons.Quality,
            stringResource(R.string.player_quality),
            onQuality,
            Modifier.control(PlayerControl.Quality).then(if (chatTarget != null) Modifier.focusProperties { right = chatTarget } else Modifier),
        )
    }
}
