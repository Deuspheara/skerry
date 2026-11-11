package com.twitchplayer.tv.feature.follows.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope

/** Keeps the Follows presenter across configuration changes; the presenter itself lives in `:shared:presenters`. */
class FollowsViewModel(create: (CoroutineScope) -> FollowsPresenter) : ViewModel() {
    val presenter = create(viewModelScope)
}
