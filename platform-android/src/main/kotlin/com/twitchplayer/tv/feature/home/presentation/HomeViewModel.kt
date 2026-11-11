package com.twitchplayer.tv.feature.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope

/** Keeps the Home presenter across configuration changes; the presenter itself lives in `:shared:presenters`. */
class HomeViewModel(create: (CoroutineScope) -> HomePresenter) : ViewModel() {
    val presenter = create(viewModelScope)
}
