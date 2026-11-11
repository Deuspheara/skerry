package com.twitchplayer.tv.feature.settings.presentation

import com.twitchplayer.tv.core.data.model.ChatMode
import com.twitchplayer.tv.core.data.model.ChatSettings
import com.twitchplayer.tv.core.data.model.PlayerEngine
import com.twitchplayer.tv.core.data.repository.SessionRepository
import com.twitchplayer.tv.core.data.repository.SessionState
import com.twitchplayer.tv.core.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val chatMode: ChatMode = ChatMode.Compact,
    val chat: ChatSettings = ChatSettings(),
    val animationsEnabled: Boolean = true,
    val playerEngine: PlayerEngine = PlayerEngine.Fallback,
    val showStats: Boolean = false,
    val followRaids: Boolean = false,
    /** Account login, or null when signed out. */
    val account: String? = null,
)

sealed interface SettingsEvent {
    data class SetChatMode(val mode: ChatMode) : SettingsEvent
    class UpdateChat(val transform: (ChatSettings) -> ChatSettings) : SettingsEvent
    data object CycleChatMode : SettingsEvent
    data object TogglePlayerEngine : SettingsEvent
    data object ToggleStats : SettingsEvent
    data object ToggleFollowRaids : SettingsEvent
    data object ToggleAnimations : SettingsEvent
}

/** [defaultEngine] is the build's engine, what the screen shows until the stored settings arrive. */
class SettingsPresenter(
    private val settings: SettingsRepository,
    session: SessionRepository,
    private val scope: CoroutineScope,
    defaultEngine: PlayerEngine = PlayerEngine.Fallback,
) {
    val state: StateFlow<SettingsUiState> = combine(settings.settings, session.state) { s, session ->
        SettingsUiState(s.chatMode, s.chat, s.animationsEnabled, s.playerEngine, s.showStats, s.followRaids, (session as? SessionState.SignedIn)?.account?.login)
    }.stateIn(scope, SharingStarted.Lazily, SettingsUiState(playerEngine = defaultEngine))

    fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.SetChatMode -> setChatMode(event.mode)

            is SettingsEvent.UpdateChat -> scope.launch { settings.updateChat(event.transform) }

            SettingsEvent.CycleChatMode -> setChatMode(ChatMode.entries[(state.value.chatMode.ordinal + 1) % ChatMode.entries.size])

            SettingsEvent.TogglePlayerEngine -> {
                val next = if (state.value.playerEngine == PlayerEngine.Native) PlayerEngine.WebView else PlayerEngine.Native
                scope.launch { settings.setPlayerEngine(next) }
            }

            SettingsEvent.ToggleStats -> scope.launch { settings.setShowStats(!state.value.showStats) }

            SettingsEvent.ToggleFollowRaids -> scope.launch { settings.setFollowRaids(!state.value.followRaids) }

            SettingsEvent.ToggleAnimations -> scope.launch { settings.setAnimations(!state.value.animationsEnabled) }
        }
    }

    private fun setChatMode(mode: ChatMode) {
        scope.launch { settings.setChatMode(mode) }
    }
}
