package com.twitchplayer.tv.feature.feed.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope

/** Keeps a Discover page's read-only chat for as long as its store lives; the presenter itself lives in `:shared:presenters`. */
class FeedChatViewModel(create: (CoroutineScope) -> FeedChatPresenter) : ViewModel() {
    val presenter = create(viewModelScope)
}
