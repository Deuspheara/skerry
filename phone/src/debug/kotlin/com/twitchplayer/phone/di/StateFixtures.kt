package com.twitchplayer.phone.di

import com.twitchplayer.tv.core.data.model.Category
import com.twitchplayer.tv.core.data.model.ChannelDetails
import com.twitchplayer.tv.core.data.model.Follows
import com.twitchplayer.tv.core.data.model.LiveStream
import com.twitchplayer.tv.core.data.model.SearchResults
import com.twitchplayer.tv.core.data.repository.SnapshotCache
import com.twitchplayer.tv.core.data.repository.TwitchRepository
import com.twitchplayer.tv.core.platform.SnapshotStorage
import com.twitchplayer.tv.feature.player.data.stream.ChannelOfflineException
import com.twitchplayer.tv.feature.player.data.stream.ResolvedStream
import com.twitchplayer.tv.feature.player.data.stream.TwitchPlaybackResolver
import org.koin.dsl.module
import java.io.IOException

/**
 * Debug-only fixtures for the states the demo data never shows, used on top of the demo module (`files/demo-offline` or
 * `files/demo-empty` next to `files/demo`, then restart) and by the instrumented tests. They are in the debug source set: release
 * contains none of this.
 */
private class OfflineRepository : TwitchRepository {
    private fun fail(): Nothing = throw IOException("offline (debug fixture)")

    override suspend fun follows(force: Boolean): Follows = fail()
    override suspend fun recommended(): List<LiveStream> = fail()
    override suspend fun topCategories(): List<Category> = fail()
    override suspend fun categoryStreams(categoryId: String, language: String?): List<LiveStream> = fail()
    override suspend fun search(query: String): SearchResults = fail()
    override suspend fun channel(login: String): ChannelDetails = fail()
    override suspend fun liveStream(login: String): LiveStream? = fail()
}

private class EmptyRepository : TwitchRepository {
    override suspend fun follows(force: Boolean): Follows = Follows(emptyList(), emptyList())
    override suspend fun recommended(): List<LiveStream> = emptyList()
    override suspend fun topCategories(): List<Category> = emptyList()
    override suspend fun categoryStreams(categoryId: String, language: String?): List<LiveStream> = emptyList()
    override suspend fun search(query: String): SearchResults = SearchResults(emptyList(), emptyList())
    override suspend fun channel(login: String): ChannelDetails = throw IOException("no channel (debug fixture)")
    override suspend fun liveStream(login: String): LiveStream? = null
}

/**
 * No screen snapshot is read or kept, so the fixture shows what the repository answers, not what an earlier run cached. The cache
 * itself is replaced too: a [SnapshotCache] singleton that already exists keeps the storage it was built with.
 */
private val noSnapshots = object : SnapshotStorage {
    override suspend fun read(name: String): String? = null
    override suspend fun write(name: String, text: String) = Unit
    override suspend fun clear() = Unit
}

/** Every request fails: the error state with its retry. */
val offlineModule = module {
    single<TwitchRepository> { OfflineRepository() }
    single<SnapshotStorage> { noSnapshots }
    single { SnapshotCache(noSnapshots) }
}

/** Nothing is followed, nothing is recommended, nothing is live: the empty states. */
val emptyModule = module {
    single<TwitchRepository> { EmptyRepository() }
    single<SnapshotStorage> { noSnapshots }
    single { SnapshotCache(noSnapshots) }
}

/** The channels are listed as usual but no stream can be played: the player's offline page (`files/demo-stream-offline`). */
val streamOfflineModule = module {
    single<TwitchPlaybackResolver> {
        object : TwitchPlaybackResolver {
            override suspend fun resolve(channel: String): ResolvedStream = throw ChannelOfflineException(channel)
        }
    }
}
