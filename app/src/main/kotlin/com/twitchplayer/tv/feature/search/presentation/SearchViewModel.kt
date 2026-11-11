package com.twitchplayer.tv.feature.search.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope

/** Keeps the search presenter across configuration changes; the presenter itself lives in `:shared:presenters`. */
class SearchViewModel(create: (CoroutineScope) -> SearchPresenter) : ViewModel() {
    val presenter = create(viewModelScope)
}
