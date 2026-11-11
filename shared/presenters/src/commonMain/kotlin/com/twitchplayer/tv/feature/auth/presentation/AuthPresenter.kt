package com.twitchplayer.tv.feature.auth.presentation

import com.twitchplayer.tv.core.data.repository.SessionRepository
import com.twitchplayer.tv.core.network.auth.DeviceCodeResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SignInUiState(val code: DeviceCodeResponse? = null)

sealed interface AuthEvent {
    data object SignIn : AuthEvent
    data object Cancel : AuthEvent
    data object SignOut : AuthEvent
}

/** Device-code sign-in shared by every screen that needs a session. */
class AuthPresenter(private val session: SessionRepository, private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow(SignInUiState())
    val state: StateFlow<SignInUiState> = mutable.asStateFlow()
    private var job: Job? = null

    fun onEvent(event: AuthEvent) {
        when (event) {
            AuthEvent.SignIn -> signIn()

            AuthEvent.Cancel -> {
                job?.cancel()
                mutable.value = SignInUiState()
            }

            AuthEvent.SignOut -> {
                scope.launch { session.signOut() }
            }
        }
    }

    private fun signIn() {
        if (job?.isActive == true) return
        job = scope.launch {
            session.signIn { code -> mutable.value = SignInUiState(code) }
            mutable.value = SignInUiState()
        }
    }
}
