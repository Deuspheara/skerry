package com.twitchplayer.tv.feature.feed.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope

/** Keeps the clips feed presenter across configuration changes; the presenter itself lives in `:shared:presenters`. */
class ClipsFeedViewModel(create: (CoroutineScope) -> ClipsFeedPresenter) : ViewModel() {
    val presenter = create(viewModelScope)
}
