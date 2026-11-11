package com.twitchplayer.tv.core.data.repository

import com.twitchplayer.tv.core.data.model.Category
import com.twitchplayer.tv.core.data.model.ChannelClip
import com.twitchplayer.tv.core.data.model.ChannelDetails
import com.twitchplayer.tv.core.data.model.ChannelRef
import com.twitchplayer.tv.core.data.model.Follows
import com.twitchplayer.tv.core.data.model.LiveStream
import com.twitchplayer.tv.core.data.model.Replay
import com.twitchplayer.tv.core.data.model.ReplayPage
import com.twitchplayer.tv.core.data.model.ScheduledStream
import com.twitchplayer.tv.core.data.model.SearchResults
import com.twitchplayer.tv.core.network.api.HelixPage
import com.twitchplayer.tv.core.network.api.TwitchApi
import com.twitchplayer.tv.core.network.api.TwitchApiException
import com.twitchplayer.tv.core.network.api.TwitchVideo
import com.twitchplayer.tv.core.platform.WallClock
import com.twitchplayer.tv.core.ui.formatTwitchDuration
import com.twitchplayer.tv.core.ui.parseTwitchDurationSeconds
import com.twitchplayer.tv.feature.player.data.chat.RoomState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

interface TwitchRepository {
    suspend fun follows(force: Boolean = false): Follows
    suspend fun recommended(): List<LiveStream>
    suspend fun topCategories(): List<Category>

    /** Live streams in a category, most viewers first; [language] (Twitch code, e.g. "fr") narrows to one language. */
    suspend fun categoryStreams(categoryId: String, language: String? = null): List<LiveStream>
    suspend fun search(query: String): SearchResults
    suspend fun channel(login: String): ChannelDetails

    /** The next page of past broadcasts of [channelId] after [cursor] (see [ChannelDetails.replaysCursor]). */
    suspend fun replays(channelId: String, cursor: String): ReplayPage = ReplayPage(emptyList())

    /** The live stream of [login], or null when offline. */
    suspend fun liveStream(login: String): LiveStream?

    /** The channel's chat modes, or null when they cannot be read. */
    suspend fun roomState(channelId: String): RoomState? = null

    /** Sends [text] to the channel's chat as the signed-in viewer. */
    suspend fun sendChat(channelId: String, text: String): SendResult = SendResult.Failed
}

/** What happened to a chat message the viewer sent. */
sealed interface SendResult {
    data object Sent : SendResult

    /** Twitch accepted the call but did not post the message (rules of the room, banned words...); [reason] is its text. */
    data class Dropped(val reason: String) : SendResult

    /** The session was created before sending existed: signing in again grants it. */
    data object NeedsSignIn : SendResult
    data object Failed : SendResult
}

class TwitchRepositoryImpl(
    private val api: TwitchApi,
    private val session: SessionRepository,
) : TwitchRepository {
    /** Immutable snapshot swapped atomically: safe to read and extend from any dispatcher. */
    private val avatarState = MutableStateFlow<Map<String, String>>(emptyMap())
    private val avatars: Map<String, String> get() = avatarState.value
    private val followsCache = TtlCache<Follows>(60_000)
    private val categoriesCache = TtlCache<List<Category>>(10 * 60_000)

    private fun userId(): String = (session.state.value as? SessionState.SignedIn)?.account?.userId ?: throw IllegalStateException("signed out")

    private suspend fun loadAvatars(ids: Collection<String>) {
        val missing = ids.filter { !avatars.containsKey(it) }.distinct().chunked(100)
        for (chunk in missing) {
            val users = api.usersByIds(chunk)
            avatarState.update { current -> current + users.associate { it.id to it.avatarUrl } }
        }
    }

    override suspend fun follows(force: Boolean): Follows = followsCache.get(force) {
        coroutineScope {
            val me = userId()
            val liveJob = async { api.followedStreams(me).data }
            val followed = api.followedChannels(me).data
            val live = liveJob.await()
            val liveIds = live.mapTo(HashSet()) { it.userId }
            val offlineRaw = followed.filter { it.broadcasterId !in liveIds }.take(40)
            loadAvatars(live.map { it.userId } + offlineRaw.map { it.broadcasterId })
            Follows(
                live = live.sortedByDescending { it.viewerCount }.map { it.toLive(avatars) },
                offline = offlineRaw.map { ChannelRef(it.broadcasterId, it.login, it.name, avatars[it.broadcasterId], isLive = false) },
            )
        }
    }

    override suspend fun recommended(): List<LiveStream> = coroutineScope {
        // Both requests start together: the top streams do not wait for the follows they are filtered against.
        val top = async { api.streams(first = 24).data }
        val followedIds = runCatching { follows().live.mapTo(HashSet()) { it.channelId } }.getOrDefault(emptySet())
        val streams = top.await().filter { it.userId !in followedIds }
        loadAvatars(streams.map { it.userId })
        streams.map { it.toLive(avatars) }
    }

    override suspend fun topCategories(): List<Category> = categoriesCache.get {
        api.topCategories(first = 100).data.map { it.toCategory() }
    }

    override suspend fun categoryStreams(categoryId: String, language: String?): List<LiveStream> {
        val streams = api.streams(first = 40, categoryId = categoryId, language = language).data
        loadAvatars(streams.map { it.userId })
        return streams.map { it.toLive(avatars) }
    }

    override suspend fun search(query: String): SearchResults = coroutineScope {
        val channels = async { api.searchChannels(query) }
        val categories = async { api.searchCategories(query) }
        SearchResults(
            channels = channels.await().map {
                ChannelRef(it.id, it.login, it.displayName, it.avatarUrl.ifEmpty { null }, it.isLive)
            },
            categories = categories.await().map { it.toCategory() },
        )
    }

    override suspend fun liveStream(login: String): LiveStream? {
        val stream = api.streamByLogin(login) ?: return null
        runCatching { loadAvatars(listOf(stream.userId)) }
        return stream.toLive(avatars)
    }

    override suspend fun roomState(channelId: String): RoomState? = runCatching { api.chatSettings(channelId) }.getOrNull()?.let {
        RoomState(
            emoteOnly = it.emoteMode,
            followersOnlyMinutes = if (it.followerMode) it.followerModeDuration ?: 0 else null,
            slowSeconds = if (it.slowMode) it.slowModeWaitTime ?: 0 else null,
            subscribersOnly = it.subscriberMode,
            uniqueOnly = it.uniqueChatMode,
        )
    }

    override suspend fun sendChat(channelId: String, text: String): SendResult {
        if (!session.hasScope(SCOPE_WRITE_CHAT)) return SendResult.NeedsSignIn
        val me = (session.state.value as? SessionState.SignedIn)?.account?.userId ?: return SendResult.Failed
        return try {
            val result = api.sendChat(channelId, me, text.trim())
            if (result.isSent) SendResult.Sent else SendResult.Dropped(result.dropReason?.message.orEmpty())
        } catch (e: CancellationException) {
            throw e
        } catch (e: TwitchApiException) {
            // 401 / 403: the token cannot write chat (scope missing or revoked); 422: the message itself is refused.
            if (e.status == 401 || e.status == 403) SendResult.NeedsSignIn else SendResult.Failed
        } catch (e: Exception) {
            SendResult.Failed
        }
    }

    override suspend fun replays(channelId: String, cursor: String): ReplayPage = api.videos(channelId, after = cursor).let { page -> ReplayPage(page.data.map { it.toReplay() }, page.pagination.cursor) }

    override suspend fun channel(login: String): ChannelDetails = coroutineScope {
        val user = api.userByLogin(login) ?: throw IllegalStateException("unknown channel")
        val liveJob = async { api.streamByLogin(login) }
        val replaysJob = async { runCatching { api.videos(user.id) }.getOrDefault(HelixPage()) }
        val clipsJob = async { runCatching { api.clips(user.id) }.getOrDefault(emptyList()) }
        val scheduleJob = async { runCatching { api.schedule(user.id) }.getOrDefault(emptyList()) }
        val followed = runCatching { follows().let { f -> (f.live.any { it.channelId == user.id } || f.offline.any { it.id == user.id }) } }
            .getOrDefault(false)
        val live = liveJob.await()
        val replays = replaysJob.await()
        avatarState.update { it + (user.id to user.avatarUrl) }
        val similarRaw = runCatching {
            api.streams(first = 12, categoryId = live?.categoryId?.ifEmpty { null }).data.filter { it.userId != user.id }.take(8)
        }.getOrDefault(emptyList())
        runCatching { loadAvatars(similarRaw.map { it.userId }) }
        ChannelDetails(
            channel = ChannelRef(user.id, user.login, user.displayName, user.avatarUrl.ifEmpty { null }, live != null, live?.viewerCount ?: 0),
            stream = live?.toLive(avatars),
            isFollowed = followed,
            replays = replays.data.map { it.toReplay() },
            replaysCursor = replays.pagination.cursor,
            similar = similarRaw.map { ChannelRef(it.userId, it.userLogin, it.userName, avatars[it.userId], isLive = true, viewers = it.viewerCount) },
            schedule = scheduleJob.await().upcoming(),
            clips = clipsJob.await().map { ChannelClip(it.id, it.title, sizedImage(it.thumbnailUrl, 320, 180), it.duration.toInt(), it.viewCount, parseTwitchTime(it.createdAt)) },
        )
    }
}

internal fun TwitchVideo.toReplay() = Replay(
    id,
    title,
    formatTwitchDuration(duration),
    sizedImage(thumbnailUrl, 320, 180),
    parseTwitchTime(createdAt),
    parseTwitchDurationSeconds(duration),
    viewCount,
)

/** Segments that are still to come (or running), not cancelled, soonest first. */
internal fun List<com.twitchplayer.tv.core.network.api.ScheduleSegment>.upcoming(nowMs: Long = WallClock.System.nowMillis()): List<ScheduledStream> = mapNotNull { seg ->
    val start = parseTwitchTime(seg.startTime)
    val end = seg.endTime?.let(::parseTwitchTime) ?: 0L
    val cancelled = seg.canceledUntil != null
    if (start == 0L || cancelled || maxOf(end, start) < nowMs) {
        null
    } else {
        ScheduledStream(seg.title, seg.category?.name.orEmpty(), start, end)
    }
}.sortedBy { it.startMs }.take(3)
