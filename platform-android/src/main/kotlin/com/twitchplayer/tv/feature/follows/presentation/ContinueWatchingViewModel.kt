package com.twitchplayer.tv.feature.follows.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope

/** Keeps the continue-watching presenter across configuration changes; the presenter itself lives in `:shared:presenters`. */
class ContinueWatchingViewModel(create: (CoroutineScope) -> ContinueWatchingPresenter) : ViewModel() {
    val presenter = create(viewModelScope)
}
