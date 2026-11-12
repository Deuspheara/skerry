package com.twitchplayer.tv.core.data

import com.twitchplayer.tv.core.FakeSessionRepository
import com.twitchplayer.tv.core.MemoryTokenStore
import com.twitchplayer.tv.core.await
import com.twitchplayer.tv.core.data.model.Account
import com.twitchplayer.tv.core.data.repository.SessionState
import com.twitchplayer.tv.core.data.repository.TwitchSessionRepository
import com.twitchplayer.tv.core.json
import com.twitchplayer.tv.core.mockClient
import com.twitchplayer.tv.core.network.auth.DeviceCodeAuth
import com.twitchplayer.tv.core.network.auth.TokenValidator
import com.twitchplayer.tv.core.network.auth.TwitchTokens
import com.twitchplayer.tv.core.realTime
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionRepositoryTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val store = MemoryTokenStore()
    private var signedOutCalls = 0
    private val validatedTokens = mutableListOf<String>()

    /** [validToken]: the access token the "server" accepts; [refreshWorks]: whether the refresh grant succeeds. */
    private fun repo(validToken: String, refreshWorks: Boolean = true): TwitchSessionRepository {
        val http = mockClient { request ->
            when (request.url.encodedPath) {
                "/oauth2/validate" -> {
                    val token = request.headers["Authorization"].orEmpty().removePrefix("OAuth ")
                    validatedTokens += token
                    if (token == validToken) {
                        json("""{"client_id":"cid","login":"camille","user_id":"42","scopes":["user:read:follows"],"expires_in":3600}""")
                    } else {
                        json("{}", HttpStatusCode.Unauthorized)
                    }
                }

                "/oauth2/token" -> if (refreshWorks) {
                    json("""{"access_token":"fresh","refresh_token":"r2","expires_in":3600,"scope":["user:read:follows"]}""")
                } else {
                    json("""{"message":"Invalid refresh token"}""", HttpStatusCode.BadRequest)
                }

                else -> json("{}", HttpStatusCode.NotFound)
            }
        }
        return TwitchSessionRepository(store, DeviceCodeAuth(http, "cid"), TokenValidator(http, "cid"), scope, onSignedOut = { signedOutCalls++ })
    }

    @AfterTest fun stop() = scope.cancel()

    private fun tokens(access: String = "old") = TwitchTokens(access, "r1", 9_999_999_999L, setOf("user:read:follows"))

    @Test fun startsSignedOutWithoutStoredTokens() = runTest {
        val repo = repo("any")
        repo.start()
        assertEquals(SessionState.SignedOut, repo.state.await { it !is SessionState.Loading })
        assertNull(repo.current())
    }

    @Test fun theStoredAccountIsShownBeforeTheTokensAreDecryptedAndRequestsWaitForThem() = runTest {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val slow = object : com.twitchplayer.tv.core.network.auth.TokenStore by store {
            override suspend fun read(): TwitchTokens? {
                gate.await()
                return store.read()
            }
        }
        store.tokens = tokens()
        store.account = Account("42", "camille")
        val http = mockClient { json("{}", HttpStatusCode.NotFound) }
        val repo = TwitchSessionRepository(slow, DeviceCodeAuth(http, "cid"), TokenValidator(http, "cid"), scope)
        repo.start()

        // The Keystore is still working: the account is known, the token is not, and a request must wait.
        assertEquals(Account("42", "camille"), (repo.state.await { it is SessionState.SignedIn } as SessionState.SignedIn).account)
        assertNull(repo.current())
        val waiting = scope.launch { repo.awaitReady() }
        realTime { kotlinx.coroutines.yield() }
        assertTrue(waiting.isActive, "the request must not be sent before the tokens are loaded")

        gate.complete(Unit)
        realTime { waiting.join() }
        assertEquals("old", repo.current())
    }

    @Test fun withoutStoredTokensTheSessionIsReadyAndSignedOut() = runTest {
        val repo = repo("any")
        repo.start()
        repo.state.await { it !is SessionState.Loading }
        realTime { repo.awaitReady() }
        assertEquals(SessionState.SignedOut, repo.state.value)
    }

    @Test fun validStoredTokensSignTheAccountIn() = runTest {
        store.tokens = tokens()
        val repo = repo(validToken = "old")
        repo.start()
        val state = repo.state.await { it is SessionState.SignedIn && it.account.login == "camille" }
        assertEquals(Account("42", "camille"), (state as SessionState.SignedIn).account)
        assertEquals(Account("42", "camille"), store.account)
        assertEquals("old", repo.current())
    }

    @Test fun anExpiredTokenIsRefreshedThenValidated() = runTest {
        store.tokens = tokens("stale")
        val repo = repo(validToken = "fresh")
        repo.start()
        repo.state.await { it is SessionState.SignedIn && it.account.login == "camille" }
        assertEquals("fresh", repo.current())
        assertEquals("fresh", store.tokens?.accessToken)
        assertEquals("r2", store.tokens?.refreshToken)
        assertEquals(listOf("stale", "fresh"), validatedTokens.take(2))
    }

    @Test fun aFailedRefreshEndsTheSessionAndClearsEverything() = runTest {
        store.tokens = tokens("stale")
        val repo = repo(validToken = "never", refreshWorks = false)
        repo.start()
        repo.state.await { it == SessionState.SignedOut }
        realTime { while (signedOutCalls == 0) kotlinx.coroutines.yield() }
        assertNull(store.tokens)
        assertTrue(store.clears >= 1)
        assertEquals(1, signedOutCalls)
        assertNull(repo.current())
    }

    @Test fun signOutClearsTheStoreAndNotifies() = runTest {
        store.tokens = tokens()
        val repo = repo(validToken = "old")
        repo.start()
        repo.state.await { it is SessionState.SignedIn && it.account.login == "camille" }
        repo.signOut()
        assertEquals(SessionState.SignedOut, repo.state.value)
        assertNull(store.tokens)
        assertEquals(1, signedOutCalls)
    }

    @Test fun hasScopeReflectsTheStoredGrant() = runTest {
        store.tokens = tokens()
        val repo = repo(validToken = "old")
        repo.start()
        repo.state.await { it is SessionState.SignedIn && it.account.login == "camille" }
        assertTrue(repo.hasScope("user:read:follows"))
        assertEquals(false, repo.hasScope("user:write:chat"))
        assertTrue(FakeSessionRepository().hasScope("anything")) // the interface default
    }
}
