package com.twitchplayer.tv.feature.player.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope

/**
 * Keeps the player session across configuration changes (and, as the activity-scoped "player" instance, across
 * screens: the mini player); the presenter itself lives in `:shared:presenters`.
 */
class PlayerViewModel(create: (CoroutineScope) -> PlayerPresenter) : ViewModel() {
    val presenter = create(viewModelScope)
}
