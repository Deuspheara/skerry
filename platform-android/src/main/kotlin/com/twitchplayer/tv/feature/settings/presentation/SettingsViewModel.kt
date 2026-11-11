package com.twitchplayer.tv.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope

/** Keeps the settings presenter across configuration changes; the presenter itself lives in `:shared:presenters`. */
class SettingsViewModel(create: (CoroutineScope) -> SettingsPresenter) : ViewModel() {
    val presenter = create(viewModelScope)
}
