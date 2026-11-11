package com.twitchplayer.tv.feature.category.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope

/** Keeps the category presenter across configuration changes; the presenter itself lives in `:shared:presenters`. */
class CategoryViewModel(create: (CoroutineScope) -> CategoryPresenter) : ViewModel() {
    val presenter = create(viewModelScope)
}
