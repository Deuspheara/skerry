package com.twitchplayer.tv.feature.browse.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope

/** Keeps the Browse presenter across configuration changes; the presenter itself lives in `:shared:presenters`. */
class BrowseViewModel(create: (CoroutineScope) -> BrowsePresenter) : ViewModel() {
    val presenter = create(viewModelScope)
}
