package com.twitchplayer.tv.demo

import android.content.Context
import com.twitchplayer.tv.core.data.model.Account
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
import com.twitchplayer.tv.core.data.repository.SendResult
import com.twitchplayer.tv.core.data.repository.SessionRepository
import com.twitchplayer.tv.core.data.repository.SessionState
import com.twitchplayer.tv.core.data.repository.TwitchRepository
import com.twitchplayer.tv.core.network.auth.DeviceCodeResponse
import com.twitchplayer.tv.feature.player.data.ChatAssetsSource
import com.twitchplayer.tv.feature.player.data.ChatSource
import com.twitchplayer.tv.feature.player.data.chat.ChatEvent
import com.twitchplayer.tv.feature.player.data.chat.ChatFragment
import com.twitchplayer.tv.feature.player.data.chat.ChatMessage
import com.twitchplayer.tv.feature.player.data.chat.ChatMessageContent
import com.twitchplayer.tv.feature.player.data.stream.ClipResolver
import com.twitchplayer.tv.feature.player.data.stream.ClipSource
import com.twitchplayer.tv.feature.player.data.stream.GqlVodResolver
import com.twitchplayer.tv.feature.player.data.stream.ResolvedStream
import com.twitchplayer.tv.feature.player.data.stream.ResolvedVod
import com.twitchplayer.tv.feature.player.data.stream.StreamVariant
import com.twitchplayer.tv.feature.player.data.stream.TwitchPlaybackResolver
import com.twitchplayer.tv.feature.player.data.stream.VodAccessException
import com.twitchplayer.tv.feature.player.data.stream.VodFailure
import com.twitchplayer.tv.feature.player.data.stream.VodResolver
import com.twitchplayer.tv.feature.player.ui.embed.PlayerConfigProvider
import com.twitchplayer.tv.feature.search.presentation.VoiceInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

private class DemoSession : SessionRepository {
    override val state: StateFlow<SessionState> = MutableStateFlow(SessionState.SignedIn(Account("demo", "Camille")))
    override fun current(): String = "demo"
    override suspend fun refresh(rejected: String?): String = "demo"
    override fun start() = Unit
    override suspend fun signIn(onCode: (DeviceCodeResponse) -> Unit): Boolean {
        onCode(DeviceCodeResponse("d", "ABCD-EFGH", "https://www.twitch.tv/activate", 600, 5))
        delay(60_000)
        return false
    }
    override suspend fun signOut() = Unit
}

private class DemoRepository(private val context: Context) : TwitchRepository {
    override suspend fun sendChat(channelId: String, text: String): SendResult {
        delay(300)
        DemoBus.events.emit(
            ChatEvent.Message(ChatMessage("demo-me-${System.nanoTime()}", "b", "me", "Camille", "camille", ChatMessageContent(text, listOf(ChatFragment("text", text))), color = "#BF94FF")),
        )
        return SendResult.Sent
    }

    override suspend fun follows(force: Boolean) = Follows(DemoData.followedLive, DemoData.followedOffline)
    override suspend fun recommended(): List<LiveStream> = DemoData.recommended
    override suspend fun topCategories(): List<Category> = DemoData.categories
    override suspend fun categoryStreams(categoryId: String, language: String?): List<LiveStream> = DemoData.liveStreams.filter { it.categoryId == categoryId }
    override suspend fun liveStream(login: String): LiveStream? = DemoData.stream(login)

    override suspend fun search(query: String): SearchResults {
        val q = query.lowercase()
        return SearchResults(
            DemoData.channels.filter { q in it.name.lowercase() }.take(4),
            DemoData.categories.filter { q in it.name.lowercase() }.take(3),
        )
    }

    override suspend fun replays(channelId: String, cursor: String): ReplayPage {
        delay(400)
        val (seed, style) = DemoData.channelSeed(channelId)
        val more = List(8) { j -> demoReplay(6 + j, "Rediffusion ${j + 7}", "${1 + j % 3}:${10 + j * 4}:05", seed, style) }
        return ReplayPage(more, null)
    }

    private fun demoReplay(i: Int, title: String, duration: String, seed: Int, style: Int): Replay {
        val seconds = duration.split(':').fold(0) { acc, part -> acc * 60 + (part.toIntOrNull() ?: 0) }
        return Replay("r$i", title, duration, DemoData.artFor(seed + 40 + i, (style + i + 1) % 4), System.currentTimeMillis() - (i + 1) * 86_400_000L, seconds, 1_200 * (i + 3))
    }

    override suspend fun channel(login: String): ChannelDetails {
        val ref = DemoData.channels.first { it.login == login }
        val (seed, style) = DemoData.channelSeed(login)
        val titles = listOf("Session du mardi soir", "Suite et fin", "Questions du public", "Édition spéciale", "Retour sur la saison", "Coulisses")
        val durations = listOf("3:12:40", "2:05:18", "1:48:02", "4:20:55", "2:41:09", "1:12:30")
        return ChannelDetails(
            channel = ref,
            stream = DemoData.stream(login),
            isFollowed = DemoData.isFollowed(login),
            replays = titles.mapIndexed { i, t -> demoReplay(i, t, durations[i], seed, style) },
            replaysCursor = "p2",
            similar = DemoData.channels.filter { it.isLive && it.login != login }.take(8),
            clips = if (!demoEvents(context)) emptyList() else List(4) { i -> ChannelClip("demo-clip-$i", "Clip ${i + 1}", DemoData.artFor(seed + 60 + i, (style + i) % 4), 20 + i * 7, 1200 * (i + 1)) },
            schedule = if (!demoEvents(context)) emptyList() else listOf(ScheduledStream("Soirée échecs", "Échecs", System.currentTimeMillis() + 86_400_000L, 0L)),
        )
    }
}

private class DemoVoice : VoiceInput {
    private var n = 0
    override fun isAvailable() = true
    override suspend fun listen(): String? {
        delay(2_400)
        n++
        return if (n % 3 == 0) null else listOf("échecs", "valmoor", "musique", "cuisine")[n % 4]
    }
}

/** Every demo channel "streams" the bundled test clip (a looping 720p file), so the native player runs offline. */
private class DemoClips : ClipResolver {
    override suspend fun resolve(slug: String) = listOf(ClipSource(720, 60f, "asset:///demo_stream.mp4"))
}

/**
 * Replays play the bundled test file. For the emulator: `adb shell settings put global twitchtv_demo_vod <id>` plays that real
 * Twitch VOD instead (network), and `gone` / `subs` / `token` / `net` simulate each failure.
 */
private class DemoVods(private val context: Context, private val real: VodResolver) : VodResolver {
    override suspend fun resolve(vodId: String): ResolvedVod {
        val mode = android.provider.Settings.Global.getString(context.contentResolver, "twitchtv_demo_vod")?.takeIf { it.isNotBlank() }
        when (mode) {
            null -> Unit
            "gone" -> throw VodAccessException(VodFailure.NOT_FOUND, "gql")
            "subs" -> throw VodAccessException(VodFailure.SUBSCRIBER_ONLY, "usher", 403)
            "token" -> throw VodAccessException(VodFailure.TOKEN, "gql", 200, integrity = true)
            "net" -> throw java.io.IOException("demo: no network")
            else -> return real.resolve(mode)
        }
        val clip = "asset:///demo_stream.mp4"
        val variants = listOf(
            StreamVariant("720p60", "720p60", 3_400_000, 1280, 720, 60f, "avc1.4D401F,mp4a.40.2", clip),
            StreamVariant("480p30", "480p", 1_400_000, 852, 480, 30f, "avc1.4D401F,mp4a.40.2", clip),
        )
        return ResolvedVod(vodId, clip, variants)
    }
}

private class DemoResolver(private val context: Context) : TwitchPlaybackResolver {
    override suspend fun resolve(channel: String): ResolvedStream {
        // `adb shell settings put global twitchtv_demo_hls http://10.0.2.2:8099/live.m3u8` plays a real HLS live stream (ad markers, latency).
        android.provider.Settings.Global.getString(context.contentResolver, "twitchtv_demo_hls")?.takeIf { it.isNotBlank() }?.let { url ->
            return ResolvedStream(channel, url, listOf(StreamVariant("720p60", "720p60", 3_400_000, 1280, 720, 60f, "avc1.4D401F,mp4a.40.2", url)))
        }
        val clip = "asset:///demo_stream.mp4"
        val variants = listOf(
            StreamVariant("720p60", "720p60", 3_400_000, 1280, 720, 60f, "avc1.4D401F,mp4a.40.2", clip),
            StreamVariant("480p30", "480p", 1_400_000, 852, 480, 30f, "avc1.4D401F,mp4a.40.2", clip),
            StreamVariant(StreamVariant.AUDIO_ONLY, "audio_only", 160_000, 0, 0, 0f, "mp4a.40.2", clip),
        )
        return ResolvedStream(channel, clip, variants)
    }
}

/** Replaces network-backed pieces so the whole UI runs offline on the prototype's fictional data. */
val demoModule = module {
    single<SessionRepository> { DemoSession() }
    single<TwitchRepository> { DemoRepository(androidContext()) }
    single<ClipResolver> { DemoClips() }
    single<VodResolver> { DemoVods(androidContext(), GqlVodResolver(get(), get(), clientId = get<com.twitchplayer.tv.core.platform.AppConfig>().playbackClientId)) }
    single<ChatSource> { DemoChat(androidContext()) }
    single<ChatAssetsSource> { DemoChatAssets }
    factory<VoiceInput> { DemoVoice() }
    single<TwitchPlaybackResolver> { DemoResolver(androidContext()) }
    single { PlayerConfigProvider(null) }
}
