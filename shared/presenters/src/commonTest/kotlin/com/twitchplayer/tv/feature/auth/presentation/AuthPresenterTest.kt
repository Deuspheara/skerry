package com.twitchplayer.tv.feature.auth.presentation

import com.twitchplayer.tv.core.data.model.Account
import com.twitchplayer.tv.core.data.repository.SessionRepository
import com.twitchplayer.tv.core.data.repository.SessionState
import com.twitchplayer.tv.core.network.auth.DeviceCodeResponse
import com.twitchplayer.tv.testing.FakeSession
import com.twitchplayer.tv.testing.FakeSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class AuthPresenterTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val code = DeviceCodeResponse("dc", "ABCD-EFGH", "https://twitch.tv/activate", 600, 5)

    /** A session whose sign-in shows [code] then waits for [finish]. */
    private class SigningIn(private val code: DeviceCodeResponse) : SessionRepository by FakeSession() {
        val finish = CompletableDeferred<Unit>()
        var calls = 0
        var signedOut = false

        /** What the sign-in answers once [finish] completes; [failure] makes it throw instead. */
        var granted = true
        var failure: Exception? = null

        /** When set, the sign-in waits for it before showing its code (the time the request to Twitch takes). */
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun signIn(onCode: (DeviceCodeResponse) -> Unit): Boolean {
            calls++
            gate?.await()
            onCode(code)
            finish.await()
            failure?.let { throw it }
            return granted
        }

        override suspend fun signOut() {
            signedOut = true
        }
    }

    @Test fun signInShowsTheCodeUntilItEnds() = runTest(dispatcher) {
        val session = SigningIn(code)
        val auth = AuthPresenter(session, FakeSettings(), backgroundScope)
        assertNull(auth.state.value.code)

        auth.onEvent(AuthEvent.SignIn)
        assertEquals(code, auth.state.value.code)
        session.finish.complete(Unit)
        advanceUntilIdle()
        assertNull(auth.state.value.code)
    }

    @Test fun aSecondSignInWhileOneRunsIsIgnored() = runTest(dispatcher) {
        val session = SigningIn(code)
        val auth = AuthPresenter(session, FakeSettings(), backgroundScope)
        auth.onEvent(AuthEvent.SignIn)
        auth.onEvent(AuthEvent.SignIn)
        assertEquals(1, session.calls)
    }

    @Test fun cancelStopsTheSignInAndClearsTheCode() = runTest(dispatcher) {
        val session = SigningIn(code)
        val auth = AuthPresenter(session, FakeSettings(), backgroundScope)
        auth.onEvent(AuthEvent.SignIn)
        auth.onEvent(AuthEvent.Cancel)
        assertNull(auth.state.value.code)
        // The job is gone: a new sign-in may start.
        auth.onEvent(AuthEvent.SignIn)
        assertEquals(2, session.calls)
    }

    @Test fun signOutAsksTheSession() = runTest(dispatcher) {
        val session = SigningIn(code)
        AuthPresenter(session, FakeSettings(), backgroundScope).onEvent(AuthEvent.SignOut)
        advanceUntilIdle()
        assertEquals(true, session.signedOut)
    }

    @Test fun signOutWithTheFakeSessionEndsTheSession() = runTest(dispatcher) {
        val session = FakeSession(SessionState.SignedIn(Account("me", "camille")))
        AuthPresenter(session, FakeSettings(), backgroundScope).onEvent(AuthEvent.SignOut)
        advanceUntilIdle()
        assertEquals(SessionState.SignedOut, session.state.value)
    }

    @Test fun aGrantedSignInEndsWithASuccessResultAndNoCode() = runTest(dispatcher) {
        val session = SigningIn(code)
        val auth = AuthPresenter(session, FakeSettings(), backgroundScope)
        auth.onEvent(AuthEvent.SignIn)

        session.finish.complete(Unit)
        advanceUntilIdle()

        assertEquals(SignInUiState(code = null, result = SignInResult.Success), auth.state.value)
    }

    @Test fun aRefusedExpiredOrFailedSignInEndsWithAFailedResult() = runTest(dispatcher) {
        val session = SigningIn(code).apply { granted = false }
        val auth = AuthPresenter(session, FakeSettings(), backgroundScope)
        auth.onEvent(AuthEvent.SignIn)

        session.finish.complete(Unit)
        advanceUntilIdle()

        assertEquals(SignInResult.Failed, auth.state.value.result)
        assertNull(auth.state.value.code)
    }

    @Test fun anExceptionInsideTheSignInIsAFailureNotACrash() = runTest(dispatcher) {
        val session = SigningIn(code).apply { failure = IllegalStateException("disk full") }
        val auth = AuthPresenter(session, FakeSettings(), backgroundScope)
        auth.onEvent(AuthEvent.SignIn)

        session.finish.complete(Unit)
        advanceUntilIdle()

        assertEquals(SignInResult.Failed, auth.state.value.result)
    }

    @Test fun theResultStaysUntilTheUiHasShownIt() = runTest(dispatcher) {
        val session = SigningIn(code).apply { granted = false }
        val auth = AuthPresenter(session, FakeSettings(), backgroundScope)
        auth.onEvent(AuthEvent.SignIn)
        session.finish.complete(Unit)
        advanceUntilIdle()

        // Nothing but the UI's acknowledgement removes it: a screen recreated in between still finds it.
        assertEquals(SignInResult.Failed, auth.state.value.result)
        assertEquals(SignInResult.Failed, auth.state.value.result)
        auth.onEvent(AuthEvent.ResultShown)
        assertNull(auth.state.value.result)
    }

    @Test fun cancellingIsNotAResult() = runTest(dispatcher) {
        val session = SigningIn(code)
        val auth = AuthPresenter(session, FakeSettings(), backgroundScope)
        auth.onEvent(AuthEvent.SignIn)

        auth.onEvent(AuthEvent.Cancel)
        advanceUntilIdle()

        assertEquals(SignInUiState(), auth.state.value)
    }

    @Test fun aNewSignInClearsTheLastResultBeforeItsCodeArrives() = runTest(dispatcher) {
        val session = SigningIn(code).apply { granted = false }
        val auth = AuthPresenter(session, FakeSettings(), backgroundScope)
        auth.onEvent(AuthEvent.SignIn)
        session.finish.complete(Unit)
        advanceUntilIdle()
        assertEquals(SignInResult.Failed, auth.state.value.result)

        session.gate = CompletableDeferred()
        auth.onEvent(AuthEvent.SignIn)

        assertNull(auth.state.value.result)
        assertNull(auth.state.value.code)
        session.gate?.complete(Unit)
        advanceUntilIdle()
        assertEquals(SignInResult.Failed, auth.state.value.result)
        assertEquals(2, session.calls)
    }

    @Test fun signingOutForgetsTheRecentSearches() = runTest(dispatcher) {
        val settings = FakeSettings(com.twitchplayer.tv.core.data.model.AppSettings(recentSearches = listOf("a", "b")))
        val session = FakeSession(SessionState.SignedIn(Account("me", "camille")))

        AuthPresenter(session, settings, backgroundScope).onEvent(AuthEvent.SignOut)
        advanceUntilIdle()

        assertEquals(emptyList(), settings.settings.value.recentSearches)
    }
}
