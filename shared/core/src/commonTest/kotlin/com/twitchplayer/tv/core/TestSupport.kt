package com.twitchplayer.tv.core

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.twitchplayer.tv.core.data.model.Account
import com.twitchplayer.tv.core.data.repository.SessionRepository
import com.twitchplayer.tv.core.data.repository.SessionState
import com.twitchplayer.tv.core.network.api.TwitchJson
import com.twitchplayer.tv.core.network.auth.DeviceCodeResponse
import com.twitchplayer.tv.core.network.auth.TokenStore
import com.twitchplayer.tv.core.network.auth.TwitchTokens
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

/** A Ktor client on a [MockEngine] with the same JSON setup as the app's. */
fun mockClient(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): HttpClient = HttpClient(MockEngine(handler)) {
    install(ContentNegotiation) { json(TwitchJson) }
}

fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK): HttpResponseData = respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

/** Runs [block] on a real dispatcher with a real timeout (runTest's virtual time cannot wait for other threads). */
suspend fun <T> realTime(block: suspend () -> T): T = withContext(Dispatchers.Default) { withTimeout(10.seconds) { block() } }

suspend fun StateFlow<SessionState>.await(predicate: (SessionState) -> Boolean): SessionState = realTime { first(predicate) }

class MemoryTokenStore(var tokens: TwitchTokens? = null, var account: Account? = null) : TokenStore {
    var clears = 0
    override suspend fun save(tokens: TwitchTokens) {
        this.tokens = tokens
    }
    override suspend fun read(): TwitchTokens? = tokens
    override suspend fun saveAccount(account: Account) {
        this.account = account
    }
    override suspend fun readAccount(): Account? = account
    override suspend fun clear() {
        clears++
        tokens = null
        account = null
    }
}

class FakeSessionRepository(initial: SessionState = SessionState.SignedIn(Account("me", "camille"))) : SessionRepository {
    override val state: StateFlow<SessionState> = MutableStateFlow(initial)
    override fun current(): String? = "token"
    override suspend fun refresh(rejected: String?): String? = "token"
    override fun start() = Unit
    override suspend fun signIn(onCode: (DeviceCodeResponse) -> Unit): Boolean = false
    override suspend fun signOut() = Unit
}

/** In-memory DataStore: enough for bindings that are only constructed, not exercised. */
object NoopPreferencesStore : DataStore<Preferences> {
    override val data = MutableStateFlow(emptyPreferences())
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = transform(data.value).also { data.value = it }
}
