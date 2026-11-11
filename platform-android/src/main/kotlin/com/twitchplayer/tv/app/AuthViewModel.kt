package com.twitchplayer.tv.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twitchplayer.tv.feature.auth.presentation.AuthPresenter
import kotlinx.coroutines.CoroutineScope

/** Keeps the sign-in presenter across configuration changes; the presenter itself lives in `:shared:presenters`. */
class AuthViewModel(create: (CoroutineScope) -> AuthPresenter) : ViewModel() {
    val presenter = create(viewModelScope)
}
