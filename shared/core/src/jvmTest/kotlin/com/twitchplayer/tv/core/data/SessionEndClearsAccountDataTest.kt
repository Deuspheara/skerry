package com.twitchplayer.tv.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.twitchplayer.tv.core.MemoryTokenStore
import com.twitchplayer.tv.core.data.repository.DataStoreSettingsRepository
import com.twitchplayer.tv.core.data.repository.SessionState
import com.twitchplayer.tv.core.data.repository.SnapshotCache
import com.twitchplayer.tv.core.data.repository.TwitchSessionRepository
import com.twitchplayer.tv.core.data.repository.forgetAccountData
import com.twitchplayer.tv.core.json
import com.twitchplayer.tv.core.mockClient
import com.twitchplayer.tv.core.network.auth.DeviceCodeAuth
import com.twitchplayer.tv.core.network.auth.TokenValidator
import com.twitchplayer.tv.core.network.auth.TwitchTokens
import com.twitchplayer.tv.core.platform.SnapshotStorage
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The recent searches and the screen snapshots go with the account, however the session ends: signed out by the viewer, or refused by Twitch. */
class SessionEndClearsAccountDataTest {
    private val folder = createTempDirectory("session").toFile()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tokens = TwitchTokens("old", "r1", 9_999_999_999L, setOf("user:read:follows"))

    @AfterTest fun stop() {
        scope.cancel()
        folder.deleteRecursively()
    }

    private val files = mutableMapOf<String, String>()
    private val storage = object : SnapshotStorage {
        override suspend fun read(name: String): String? = files[name]
        override suspend fun write(name: String, text: String) {
            files[name] = text
        }
        override suspend fun clear() = files.clear()
    }

    private fun settings(): DataStoreSettingsRepository {
        val store: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) { File(folder, "settings.preferences_pb") }
        return DataStoreSettingsRepository(store)
    }

    private suspend fun seed(settings: DataStoreSettingsRepository) {
        settings.addRecentSearch("lunaria")
        settings.addRecentSearch("chess")
        files["snapshot"] = "kept for the account"
    }

    @Test fun aRefusedRefreshTokenEndsTheSessionAndForgetsTheRecentSearchesAndSnapshots() = runBlocking {
        val settings = settings()
        seed(settings)
        // The stored token is rejected, and so is the refresh token: Twitch ended the session, not the viewer.
        val repo = TwitchSessionRepository(
            MemoryTokenStore(tokens),
            DeviceCodeAuth(mockClient { json("""{"message":"Invalid refresh token"}""", HttpStatusCode.BadRequest) }, "cid"),
            TokenValidator(mockClient { json("{}", HttpStatusCode.Unauthorized) }, "cid"),
            scope,
            onSignedOut = forgetAccountData(SnapshotCache(storage), settings),
        )

        repo.start()

        withTimeout(5_000) { repo.state.first { it is SessionState.SignedOut } }
        assertEquals(emptyList(), settings.settings.first().recentSearches)
        assertEquals(emptyMap(), files)
    }

    @Test fun signingOutForgetsThemToo() = runBlocking {
        val settings = settings()
        val repo = TwitchSessionRepository(
            MemoryTokenStore(tokens),
            DeviceCodeAuth(mockClient { json("{}") }, "cid"),
            TokenValidator(mockClient { json("{}", HttpStatusCode.Unauthorized) }, "cid"),
            scope,
            onSignedOut = forgetAccountData(SnapshotCache(storage), settings),
        )
        seed(settings)

        repo.signOut()

        assertEquals(emptyList(), settings.settings.first().recentSearches)
        assertEquals(emptyMap(), files)
    }

    @Test fun aSessionThatKeepsWorkingKeepsTheRecentSearches() = runBlocking {
        val settings = settings()
        seed(settings)
        val http = mockClient { request ->
            when (request.url.encodedPath) {
                "/oauth2/validate" -> json("""{"client_id":"cid","login":"camille","user_id":"42","scopes":["user:read:follows"],"expires_in":3600}""")
                else -> json("""{"access_token":"fresh","refresh_token":"r2","expires_in":3600,"scope":["user:read:follows"]}""")
            }
        }
        val repo = TwitchSessionRepository(
            MemoryTokenStore(tokens),
            DeviceCodeAuth(http, "cid"),
            TokenValidator(http, "cid"),
            scope,
            onSignedOut = forgetAccountData(SnapshotCache(storage), settings),
        )
        repo.start()
        withTimeout(5_000) { repo.state.first { it is SessionState.SignedIn } }

        assertEquals("fresh", repo.refresh(rejected = "old"))

        assertEquals(listOf("chess", "lunaria"), settings.settings.first().recentSearches)
    }
}
