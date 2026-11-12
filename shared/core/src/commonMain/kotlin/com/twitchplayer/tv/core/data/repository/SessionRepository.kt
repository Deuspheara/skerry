package com.twitchplayer.tv.core.data.repository

import com.twitchplayer.tv.core.data.model.Account
import com.twitchplayer.tv.core.network.api.TokenSource
import com.twitchplayer.tv.core.network.auth.DeviceCodeAuth
import com.twitchplayer.tv.core.network.auth.DeviceCodeResponse
import com.twitchplayer.tv.core.network.auth.TokenStore
import com.twitchplayer.tv.core.network.auth.TokenValidator
import com.twitchplayer.tv.core.network.auth.TwitchTokens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile

const val SCOPE_WRITE_CHAT = "user:write:chat"
const val SCOPE_READ_EMOTES = "user:read:emotes"

sealed interface SessionState {
    data object Loading : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val account: Account) : SessionState
}

/** The Twitch session as the rest of the app sees it. */
interface SessionRepository : TokenSource {
    val state: StateFlow<SessionState>

    /** Loads the stored session and keeps it valid while the app runs. */
    fun start()

    /** Device-code sign-in; [onCode] shows the verification code while polling. */
    suspend fun signIn(onCode: (DeviceCodeResponse) -> Unit): Boolean
    suspend fun signOut()

    /**
     * Suspends until the stored tokens have been read (they are decrypted off the main thread and can take a while on a cold start), so a
     * request is never sent before the session can sign it. [state] may already say [SessionState.SignedIn] before this returns: the account
     * is plain data, read first, so screens show their stored copy while the tokens load.
     */
    suspend fun awaitReady() = Unit

    /** The stored token was granted [scope] (sessions from before a scope was added lack it until the viewer signs in again). */
    fun hasScope(scope: String): Boolean = true
}

/** Owns the Twitch session: token storage, rotation, hourly validation (required by Twitch) and sign-in. */
class TwitchSessionRepository(
    private val tokenStore: TokenStore,
    private val auth: DeviceCodeAuth,
    private val validator: TokenValidator,
    private val scope: CoroutineScope,
    /** Clears what was kept for the account (screen snapshots) when the session ends. */
    private val onSignedOut: suspend () -> Unit = {},
) : SessionRepository {
    private val mutableState = MutableStateFlow<SessionState>(SessionState.Loading)
    override val state: StateFlow<SessionState> = mutableState.asStateFlow()
    private val lock = Mutex()
    private val ready = CompletableDeferred<Unit>()

    @Volatile private var tokens: TwitchTokens? = null

    override fun current(): String? = tokens?.accessToken

    override suspend fun awaitReady() = ready.await()

    override fun hasScope(scope: String): Boolean = tokens?.scopes?.contains(scope) == true

    override suspend fun refresh(rejected: String?): String? = lock.withLock {
        val stored = tokens ?: return null
        if (rejected != null && stored.accessToken != rejected) return stored.accessToken
        runCatching { auth.refresh(stored) }
            .onSuccess {
                tokens = it
                tokenStore.save(it)
            }
            .onFailure {
                if (it is CancellationException) throw it
                endSession()
            }
            .getOrNull()?.accessToken
    }

    override fun start() {
        scope.launch {
            // The last validated account is plain data, read before the tokens: screens show their stored copy at once instead of waiting for
            // the Keystore to decrypt the tokens (their requests wait for [awaitReady]). The startup validation Twitch requires runs right
            // after, in parallel with them, and ends the session if the token was revoked (an expired token is refreshed by the first
            // request that gets a 401).
            tokenStore.readAccount()?.let { if (mutableState.value is SessionState.Loading) mutableState.value = SessionState.SignedIn(it) }
            val stored = tokenStore.read()
            if (stored == null) {
                mutableState.value = SessionState.SignedOut
                ready.complete(Unit)
                return@launch
            }
            tokens = stored
            ready.complete(Unit)
            while (tokens != null) {
                validateOnce()
                delay(VALIDATE_INTERVAL_MS)
            }
        }
    }

    private suspend fun validateOnce() {
        val current = tokens ?: return
        val result = runCatching { validator.validate(current.accessToken) }
        if (result.isFailure) {
            // Network trouble: keep the session, retry at the next interval.
            if (mutableState.value is SessionState.Loading) mutableState.value = SessionState.SignedOut
            return
        }
        val validated = result.getOrNull() ?: refresh()?.let { runCatching { validator.validate(it) }.getOrNull() }
        if (validated?.userId == null) {
            if (tokens != null) endSession()
            return
        }
        val account = Account(validated.userId, validated.login.orEmpty())
        if (account != tokenStore.readAccount()) tokenStore.saveAccount(account)
        mutableState.value = SessionState.SignedIn(account)
    }

    override suspend fun signIn(onCode: (DeviceCodeResponse) -> Unit): Boolean {
        val code = runCatching { auth.begin(SCOPES) }.getOrNull() ?: return false
        onCode(code)
        val granted = runCatching { auth.awaitAuthorization(code, SCOPES) }
            .onFailure { if (it is CancellationException) throw it }
            .getOrNull() ?: return false
        tokenStore.save(granted)
        tokens = granted
        ready.complete(Unit)
        validateOnce()
        start()
        return mutableState.value is SessionState.SignedIn
    }

    override suspend fun signOut() = lock.withLock { endSession() }

    private suspend fun endSession() {
        tokens = null
        tokenStore.clear()
        runCatching { onSignedOut() }
        mutableState.value = SessionState.SignedOut
    }

    private companion object {
        /** [SCOPE_WRITE_CHAT] and [SCOPE_READ_EMOTES] are newer: a token from before they were added keeps working for everything else. */
        val SCOPES = setOf("user:read:follows", "user:read:chat", SCOPE_WRITE_CHAT, SCOPE_READ_EMOTES)
        const val VALIDATE_INTERVAL_MS = 60 * 60 * 1000L
    }
}
