package com.twitchplayer.tv.core.data

import com.twitchplayer.tv.core.FakeSessionRepository
import com.twitchplayer.tv.core.data.repository.SnapshotCache
import com.twitchplayer.tv.core.data.repository.TwitchRepositoryImpl
import com.twitchplayer.tv.core.json
import com.twitchplayer.tv.core.mockClient
import com.twitchplayer.tv.core.network.api.TokenSource
import com.twitchplayer.tv.core.network.api.TwitchApi
import com.twitchplayer.tv.core.platform.SnapshotStorage
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TwitchRepositoryAvatarTest {
    /** Ids asked from `users`, one entry per request. */
    private val userRequests = mutableListOf<List<String>>()

    private fun stream(id: String) = """{"id":"s$id","user_id":"$id","user_login":"l$id","user_name":"N$id","title":"t","viewer_count":${id.toInt() * 10},"thumbnail_url":"https://x/{width}x{height}.jpg","started_at":"2026-11-07T09:12:00Z"}"""

    private class Memory : SnapshotStorage {
        val files = mutableMapOf<String, String>()
        override suspend fun read(name: String): String? = files[name]
        override suspend fun write(name: String, text: String) {
            files[name] = text
        }
        override suspend fun clear() = files.clear()
    }

    private val storage = Memory()
    private fun newRepository() = TwitchRepositoryImpl(
        TwitchApi(
            mockClient { request ->
                when (request.url.encodedPath) {
                    "/helix/streams/followed" -> json("""{"data":[${stream("1")},${stream("2")}]}""")

                    "/helix/channels/followed" -> json("""{"data":[{"broadcaster_id":"3","broadcaster_login":"l3","broadcaster_name":"N3"}]}""")

                    "/helix/streams" -> json("""{"data":[${stream("1")},${stream("4")}]}""")

                    "/helix/users" -> {
                        val ids = request.url.parameters.getAll("id").orEmpty()
                        userRequests += ids
                        json("""{"data":[${ids.joinToString(",") { """{"id":"$it","login":"l$it","display_name":"N$it","profile_image_url":"https://x/avatar$it.png"}""" }}]}""")
                    }

                    else -> json("{}", HttpStatusCode.NotFound)
                }
            },
            "cid",
            object : TokenSource {
                override fun current() = "t"
                override suspend fun refresh(rejected: String?): String? = null
            },
        ),
        FakeSessionRepository(),
        SnapshotCache(storage, io = Dispatchers.Unconfined),
    )

    private val repository = newRepository()

    @Test fun followsLoadsEachAvatarOnceAndKeepsItAcrossRefreshes() = runTest {
        val first = repository.follows(force = true)
        assertEquals(listOf("N2", "N1"), first.live.map { it.name }) // most viewers first
        assertEquals("https://x/avatar1.png", first.live.first { it.channelId == "1" }.avatarUrl)
        assertEquals("https://x/avatar3.png", first.offline.single().avatarUrl)
        assertEquals(1, userRequests.size)
        assertEquals(setOf("1", "2", "3"), userRequests.single().toSet())

        repository.follows(force = true)
        assertEquals(1, userRequests.size, "a refresh must not ask for avatars it already has")
    }

    @Test fun onlyUnknownAvatarsAreRequestedLater() = runTest {
        repository.follows(force = true)
        val category = repository.categoryStreams("77")
        assertEquals(listOf("https://x/avatar1.png", "https://x/avatar4.png"), category.map { it.avatarUrl })
        assertEquals(2, userRequests.size)
        assertEquals(listOf("4"), userRequests.last())
    }

    @Test fun theAvatarsComeFromDiskAfterARestartWithNoRequest() = runTest {
        repository.follows(force = true)
        assertEquals(1, userRequests.size)

        val restarted = newRepository()
        val again = restarted.follows(force = true)
        assertEquals(1, userRequests.size, "a cold start must not ask Twitch for pictures it saved")
        assertEquals("https://x/avatar1.png", again.live.first { it.channelId == "1" }.avatarUrl)
    }
}
