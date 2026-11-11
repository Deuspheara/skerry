package com.twitchplayer.tv.feature.settings.presentation

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twitchplayer.tv.core.data.model.ChatMode
import com.twitchplayer.tv.core.data.model.ChatSettings
import com.twitchplayer.tv.core.data.model.PlayerEngine
import com.twitchplayer.tv.core.data.repository.SessionRepository
import com.twitchplayer.tv.core.data.repository.SessionState
import com.twitchplayer.tv.core.data.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class SettingsUiState(
    val chatMode: ChatMode = ChatMode.Compact,
    val chat: ChatSettings = ChatSettings(),
    val animationsEnabled: Boolean = true,
    val playerEngine: PlayerEngine = PlayerEngine.buildDefault(),
    val showStats: Boolean = false,
    val followRaids: Boolean = false,
    /** Account login, or null when signed out. */
    val account: String? = null,
)

class SettingsViewModel(
    private val settings: SettingsRepository,
    session: SessionRepository,
) : ViewModel() {
    val state: StateFlow<SettingsUiState> = combine(settings.settings, session.state) { s, session ->
        SettingsUiState(s.chatMode, s.chat, s.animationsEnabled, s.playerEngine, s.showStats, s.followRaids, (session as? SessionState.SignedIn)?.account?.login)
    }.stateIn(viewModelScope, SharingStarted.Lazily, SettingsUiState())

    fun setChatMode(mode: ChatMode) { viewModelScope.launch { settings.setChatMode(mode) } }

    fun updateChat(transform: (ChatSettings) -> ChatSettings) { viewModelScope.launch { settings.updateChat(transform) } }

    fun cycleChatMode() = setChatMode(ChatMode.entries[(state.value.chatMode.ordinal + 1) % ChatMode.entries.size])

    fun togglePlayerEngine() {
        val next = if (state.value.playerEngine == PlayerEngine.Native) PlayerEngine.WebView else PlayerEngine.Native
        viewModelScope.launch { settings.setPlayerEngine(next) }
    }

    fun toggleStats() { viewModelScope.launch { settings.setShowStats(!state.value.showStats) } }

    fun toggleFollowRaids() { viewModelScope.launch { settings.setFollowRaids(!state.value.followRaids) } }

    fun toggleAnimations() { viewModelScope.launch { settings.setAnimations(!state.value.animationsEnabled) } }
}
