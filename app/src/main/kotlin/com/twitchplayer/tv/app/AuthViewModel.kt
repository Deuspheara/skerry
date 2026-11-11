package com.twitchplayer.tv.app

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twitchplayer.tv.core.data.repository.SessionRepository
import com.twitchplayer.tv.core.network.auth.DeviceCodeResponse
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Immutable
data class SignInUiState(val code: DeviceCodeResponse? = null)

/** Device-code sign-in shared by every screen that needs a session. */
class AuthViewModel(private val session: SessionRepository) : ViewModel() {
    private val mutable = MutableStateFlow(SignInUiState())
    val state: StateFlow<SignInUiState> = mutable.asStateFlow()
    private var job: Job? = null

    fun signIn() {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            session.signIn { code -> mutable.value = SignInUiState(code) }
            mutable.value = SignInUiState()
        }
    }

    fun cancel() {
        job?.cancel()
        mutable.value = SignInUiState()
    }

    fun signOut() {
        viewModelScope.launch { session.signOut() }
    }
}
