package com.twitchplayer.tv.feature.auth.presentation

import com.twitchplayer.tv.core.data.repository.SessionRepository
import com.twitchplayer.tv.core.network.auth.DeviceCodeResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How a sign-in that was not cancelled ended. */
enum class SignInResult { Success, Failed }

/**
 * [code] is shown while the grant waits for the viewer; [result] is set once, when the sign-in ends, and stays until the UI has
 * shown it ([AuthEvent.ResultShown]) or a new sign-in starts: it lives here, not in a screen, so a configuration change between the
 * end of the sign-in and the message cannot lose it. A cancel by the viewer is not a result.
 */
data class SignInUiState(val code: DeviceCodeResponse? = null, val result: SignInResult? = null)

sealed interface AuthEvent {
    data object SignIn : AuthEvent
    data object Cancel : AuthEvent
    data object SignOut : AuthEvent

    /** The UI showed [SignInUiState.result]: forget it. */
    data object ResultShown : AuthEvent
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

            AuthEvent.ResultShown -> mutable.update { it.copy(result = null) }
        }
    }

    private fun signIn() {
        if (job?.isActive == true) return
        mutable.update { it.copy(result = null) }
        job = scope.launch {
            // A sign-in must end in a result, whatever went wrong inside it; only a cancel passes through.
            val signedIn = runCatching { session.signIn { code -> mutable.value = SignInUiState(code) } }
                .onFailure { if (it is CancellationException || it is Error) throw it }
                .getOrDefault(false)
            mutable.value = SignInUiState(result = if (signedIn) SignInResult.Success else SignInResult.Failed)
        }
    }
}
