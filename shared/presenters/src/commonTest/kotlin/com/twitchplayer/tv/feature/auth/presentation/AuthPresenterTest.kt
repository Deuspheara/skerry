package com.twitchplayer.tv.feature.auth.presentation

import com.twitchplayer.tv.core.data.model.Account
import com.twitchplayer.tv.core.data.repository.SessionRepository
import com.twitchplayer.tv.core.data.repository.SessionState
import com.twitchplayer.tv.core.network.auth.DeviceCodeResponse
import com.twitchplayer.tv.testing.FakeSession
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

        override suspend fun signIn(onCode: (DeviceCodeResponse) -> Unit): Boolean {
            calls++
            onCode(code)
            finish.await()
            return true
        }

        override suspend fun signOut() {
            signedOut = true
        }
    }

    @Test fun signInShowsTheCodeUntilItEnds() = runTest(dispatcher) {
        val session = SigningIn(code)
        val auth = AuthPresenter(session, backgroundScope)
        assertNull(auth.state.value.code)

        auth.onEvent(AuthEvent.SignIn)
        assertEquals(code, auth.state.value.code)
        session.finish.complete(Unit)
        advanceUntilIdle()
        assertNull(auth.state.value.code)
    }

    @Test fun aSecondSignInWhileOneRunsIsIgnored() = runTest(dispatcher) {
        val session = SigningIn(code)
        val auth = AuthPresenter(session, backgroundScope)
        auth.onEvent(AuthEvent.SignIn)
        auth.onEvent(AuthEvent.SignIn)
        assertEquals(1, session.calls)
    }

    @Test fun cancelStopsTheSignInAndClearsTheCode() = runTest(dispatcher) {
        val session = SigningIn(code)
        val auth = AuthPresenter(session, backgroundScope)
        auth.onEvent(AuthEvent.SignIn)
        auth.onEvent(AuthEvent.Cancel)
        assertNull(auth.state.value.code)
        // The job is gone: a new sign-in may start.
        auth.onEvent(AuthEvent.SignIn)
        assertEquals(2, session.calls)
    }

    @Test fun signOutAsksTheSession() = runTest(dispatcher) {
        val session = SigningIn(code)
        AuthPresenter(session, backgroundScope).onEvent(AuthEvent.SignOut)
        advanceUntilIdle()
        assertEquals(true, session.signedOut)
    }

    @Test fun signOutWithTheFakeSessionEndsTheSession() = runTest(dispatcher) {
        val session = FakeSession(SessionState.SignedIn(Account("me", "camille")))
        AuthPresenter(session, backgroundScope).onEvent(AuthEvent.SignOut)
        advanceUntilIdle()
        assertEquals(SessionState.SignedOut, session.state.value)
    }
}
