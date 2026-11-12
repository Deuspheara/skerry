@file:Suppress("MagicNumber") // fictional fixture data: the numbers are the data

package com.twitchplayer.tv.platform.apple

import com.twitchplayer.tv.core.data.model.Account
import com.twitchplayer.tv.core.data.model.Category
import com.twitchplayer.tv.core.data.model.CategoryGroup
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
import com.twitchplayer.tv.feature.player.data.ChatAssets
import com.twitchplayer.tv.feature.player.data.ChatAssetsSource
import com.twitchplayer.tv.feature.player.data.ChatSource
import com.twitchplayer.tv.feature.player.data.LiteChatAssets
import com.twitchplayer.tv.feature.player.data.chat.ChatBadge
import com.twitchplayer.tv.feature.player.data.chat.ChatEmote
import com.twitchplayer.tv.feature.player.data.chat.ChatEvent
import com.twitchplayer.tv.feature.player.data.chat.ChatFragment
import com.twitchplayer.tv.feature.player.data.chat.ChatMention
import com.twitchplayer.tv.feature.player.data.chat.ChatMessage
import com.twitchplayer.tv.feature.player.data.chat.ChatMessageContent
import com.twitchplayer.tv.feature.player.data.chat.ChatReply
import com.twitchplayer.tv.feature.player.data.chat.NoticeKind
import com.twitchplayer.tv.feature.player.data.emotes.Emote
import com.twitchplayer.tv.feature.player.data.emotes.EmoteGroup
import com.twitchplayer.tv.feature.player.data.emotes.EmoteSection
import com.twitchplayer.tv.feature.player.data.emotes.EmoteSource
import com.twitchplayer.tv.feature.player.data.emotes.UserEmoteAccess
import com.twitchplayer.tv.feature.player.data.stream.ClipResolver
import com.twitchplayer.tv.feature.player.data.stream.ClipSource
import com.twitchplayer.tv.feature.player.data.stream.ResolvedStream
import com.twitchplayer.tv.feature.player.data.stream.ResolvedVod
import com.twitchplayer.tv.feature.player.data.stream.StreamVariant
import com.twitchplayer.tv.feature.player.data.stream.TwitchPlaybackResolver
import com.twitchplayer.tv.feature.player.data.stream.VodResolver
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.koin.dsl.module
import kotlin.time.Clock

/**
 * Deterministic fictional data (no network, no account, no real channel or brand art) so the Apple apps can be run, screenshotted and
 * tested offline. Swift turns the `demoart://` and `demoemote://` URLs into artwork, and plays the bundled test clip for [DEMO_STREAM_URL].
 * Switched on by a launch argument in the apps (`-demo`), never in release builds.
 */
const val DEMO_STREAM_URL = "demo://stream"

private fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()

private fun art(seed: Int, style: Int) = "demoart://x/$seed/$style"

internal object AppleDemoData {
    private class Cat(val id: String, val name: String, val group: CategoryGroup, val viewers: Int, val seed: Int, val style: Int)

    private val cats = listOf(
        Cat("valmoor", "Valmoor", CategoryGroup.Games, 42300, 11, 2), Cat("racers", "Pixel Racers", CategoryGroup.Games, 31800, 12, 3),
        Cat("kestrel", "Kestrel Ops", CategoryGroup.Games, 28400, 13, 0), Cat("discussion", "Discussion", CategoryGroup.Irl, 18500, 14, 1),
        Cat("speedrun", "Speedruns", CategoryGroup.Games, 12500, 15, 2), Cat("musique", "Musique & DJ", CategoryGroup.Music, 9800, 16, 3),
        Cat("arcana", "Arcana Deck", CategoryGroup.Games, 9100, 17, 0), Cat("simulation", "Bâtisseurs", CategoryGroup.Games, 6200, 18, 1),
        Cat("cuisine", "Cuisine", CategoryGroup.Irl, 4900, 19, 2), Cat("echecs", "Échecs", CategoryGroup.Games, 9050, 20, 0),
        Cat("art", "Art", CategoryGroup.Creative, 4100, 21, 3), Cat("sport", "Sport", CategoryGroup.Irl, 3400, 22, 1),
        Cat("concerts", "Concerts", CategoryGroup.Music, 3300, 23, 2), Cat("voyages", "Voyages", CategoryGroup.Irl, 2600, 24, 3),
        Cat("peinture", "Peinture", CategoryGroup.Creative, 2200, 25, 0), Cat("artisanat", "Artisanat", CategoryGroup.Creative, 1500, 26, 1),
    )

    val categories: List<Category> = cats.sortedByDescending { it.viewers }.map { Category(it.id, it.name, art(it.seed, it.style), it.group) }

    private class Chan(val id: String, val name: String, val title: String, val cat: String, val viewers: Int, val live: Boolean, val seed: Int, val style: Int)

    private val chans = listOf(
        Chan("lunaria", "Lunaria", "Run nocturne : boss final sans dégâts", "valmoor", 14820, true, 101, 2),
        Chan("maitrekoi", "MaîtreKoi", "Ouverture sicilienne, on analyse vos parties", "echecs", 6340, true, 102, 0),
        Chan("ines", "Cuisine d'Inès", "Gratin dauphinois, la version de ma grand-mère", "cuisine", 3185, true, 103, 1),
        Chan("oriel", "DJ Oriel", "Set deep house, vinyles uniquement", "musique", 5470, true, 104, 3),
        Chan("kestrelfr", "Kestrel FR", "Ranked Diamant : on pousse vers Élite", "kestrel", 22910, true, 105, 0),
        Chan("manon", "Pixel Manon", "Peinture numérique : portrait au pinceau doux", "art", 1820, true, 106, 3),
        Chan("zero", "Vitesse Zéro", "Any% en moins de 40 minutes ?", "speedrun", 9760, true, 107, 2),
        Chan("theo", "Arcana Théo", "Draft de la saison : deck Tempête", "arcana", 4210, true, 108, 1),
        Chan("tournesol", "Tournesol", "Discussion : vos questions du jour", "discussion", 7880, true, 109, 1),
        Chan("nova", "Garage Nova", "Course d'endurance 6 h, relais en direct", "racers", 11340, true, 110, 3),
        Chan("sacha", "Atelier Sacha", "Je construis une ville dense sans embouteillage", "simulation", 2650, true, 111, 0),
        Chan("brume", "Brume", "Soirée coop tranquille, venez avec un café", "valmoor", 3920, true, 112, 2),
        Chan("marlou", "Marlou", "Tournoi entre amis, finale ce soir", "racers", 5120, true, 113, 1),
        Chan("ondine", "Ondine", "Blitz 3+2 contre les abonnés", "echecs", 2710, true, 114, 3),
        Chan("hibou", "Hibou du Soir", "", "discussion", 0, false, 115, 0),
        Chan("clem", "Clémentine", "", "peinture", 0, false, 116, 2),
        Chan("fanfan", "Fanfan OST", "", "musique", 0, false, 117, 1),
    )
    private val followedIds = setOf("lunaria", "maitrekoi", "ines", "oriel", "theo", "manon", "zero", "nova", "hibou", "clem", "fanfan")

    private fun catName(id: String) = cats.first { it.id == id }.name

    private fun Chan.stream() = LiveStream(id, id, name, title, cat, catName(cat), viewers, art(seed + 500, style), art(seed + 900, style), startedAtMs = nowMs() - (seed % 7 + 1) * 1_800_000L, language = "fr")
    private fun Chan.ref() = ChannelRef(id, id, name, art(seed + 900, style), live, viewers)

    val liveStreams: List<LiveStream> get() = chans.filter { it.live }.map { it.stream() }
    val followedLive: List<LiveStream> get() = chans.filter { it.live && it.id in followedIds }.map { it.stream() }
    val recommended: List<LiveStream> get() = liveStreams.filter { s -> s.channelId !in followedIds }.sortedByDescending { it.viewers }
    val followedOffline: List<ChannelRef> get() = chans.filter { !it.live && it.id in followedIds }.map { it.ref().copy(lastSeenLiveMs = nowMs() - 86_400_000L) }
    val channels: List<ChannelRef> get() = chans.map { it.ref() }

    fun channelSeed(id: String): Pair<Int, Int> = chans.first { it.id == id }.let { it.seed to it.style }

    fun isFollowed(id: String) = id in followedIds

    fun stream(login: String): LiveStream? = liveStreams.firstOrNull { it.login == login }

    fun artFor(seed: Int, style: Int) = art(seed, style)
}

private class AppleDemoSession : SessionRepository {
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

/** The demo's "signed out" session, to show the sign-in flow (launch argument `-demo-signed-out`). */
private class AppleDemoSignedOutSession : SessionRepository {
    override val state: StateFlow<SessionState> = MutableStateFlow(SessionState.SignedOut)

    override fun current(): String? = null

    override suspend fun refresh(rejected: String?): String = ""

    override fun start() = Unit

    override suspend fun signIn(onCode: (DeviceCodeResponse) -> Unit): Boolean {
        onCode(DeviceCodeResponse("d", "ABCD-EFGH", "https://www.twitch.tv/activate", 600, 5))
        delay(60_000)
        return false
    }

    override suspend fun signOut() = Unit
}

private object DemoBus {
    val events = MutableSharedFlow<ChatEvent>(extraBufferCapacity = 16)
}

private class AppleDemoRepository : TwitchRepository {
    override suspend fun sendChat(channelId: String, text: String): SendResult {
        delay(300)
        DemoBus.events.emit(
            ChatEvent.Message(ChatMessage("demo-me-${nowMs()}", "b", "me", "Camille", "camille", ChatMessageContent(text, listOf(ChatFragment("text", text))), color = "#BF94FF")),
        )
        return SendResult.Sent
    }

    override suspend fun follows(force: Boolean) = Follows(AppleDemoData.followedLive, AppleDemoData.followedOffline)

    override suspend fun recommended(): List<LiveStream> = AppleDemoData.recommended

    override suspend fun topCategories(): List<Category> = AppleDemoData.categories

    override suspend fun categoryStreams(categoryId: String, language: String?): List<LiveStream> = AppleDemoData.liveStreams.filter { it.categoryId == categoryId }

    override suspend fun liveStream(login: String): LiveStream? = AppleDemoData.stream(login)

    override suspend fun search(query: String): SearchResults {
        val q = query.lowercase()
        delay(250)
        return SearchResults(
            AppleDemoData.channels.filter { q in it.name.lowercase() }.take(6),
            AppleDemoData.categories.filter { q in it.name.lowercase() }.take(4),
        )
    }

    override suspend fun replays(channelId: String, cursor: String): ReplayPage {
        delay(400)
        val (seed, style) = AppleDemoData.channelSeed(channelId)
        return ReplayPage(List(8) { j -> demoReplay(6 + j, "Rediffusion ${j + 7}", "${1 + j % 3}:${10 + j * 4}:05", seed, style) }, null)
    }

    private fun demoReplay(i: Int, title: String, duration: String, seed: Int, style: Int): Replay {
        val seconds = duration.split(':').fold(0) { acc, part -> acc * 60 + (part.toIntOrNull() ?: 0) }
        return Replay("r$i", title, duration, AppleDemoData.artFor(seed + 40 + i, (style + i + 1) % 4), nowMs() - (i + 1) * 86_400_000L, seconds, 1_200 * (i + 3))
    }

    override suspend fun channel(login: String): ChannelDetails {
        val ref = AppleDemoData.channels.first { it.login == login }
        val (seed, style) = AppleDemoData.channelSeed(login)
        val titles = listOf("Session du mardi soir", "Suite et fin", "Questions du public", "Édition spéciale", "Retour sur la saison", "Coulisses")
        val durations = listOf("3:12:40", "2:05:18", "1:48:02", "4:20:55", "2:41:09", "1:12:30")
        return ChannelDetails(
            channel = ref,
            stream = AppleDemoData.stream(login),
            isFollowed = AppleDemoData.isFollowed(login),
            replays = titles.mapIndexed { i, t -> demoReplay(i, t, durations[i], seed, style) },
            replaysCursor = "p2",
            similar = AppleDemoData.channels.filter { it.isLive && it.login != login }.take(8),
            clips = List(4) { i -> ChannelClip("demo-clip-$i", "Clip ${i + 1}", AppleDemoData.artFor(seed + 60 + i, (style + i) % 4), 20 + i * 7, 1200 * (i + 1)) },
            schedule = listOf(ScheduledStream("Soirée échecs", "Échecs", nowMs() + 86_400_000L, 0L)),
        )
    }
}

/** Every demo channel "streams" the bundled test clip (a looping 720p file), so `AVPlayer` runs offline. */
private class AppleDemoClips : ClipResolver {
    override suspend fun resolve(slug: String) = listOf(ClipSource(720, 60f, DEMO_STREAM_URL))
}

private val demoVariants = listOf(
    StreamVariant("720p60", "720p60", 3_400_000, 1280, 720, 60f, "avc1.4D401F,mp4a.40.2", DEMO_STREAM_URL),
    StreamVariant("480p30", "480p", 1_400_000, 852, 480, 30f, "avc1.4D401F,mp4a.40.2", DEMO_STREAM_URL),
)

private class AppleDemoVods : VodResolver {
    override suspend fun resolve(vodId: String) = ResolvedVod(vodId, DEMO_STREAM_URL, demoVariants)
}

private class AppleDemoResolver : TwitchPlaybackResolver {
    override suspend fun resolve(channel: String) = ResolvedStream(
        channel,
        DEMO_STREAM_URL,
        demoVariants + StreamVariant(StreamVariant.AUDIO_ONLY, "audio_only", 160_000, 0, 0, 0f, "mp4a.40.2", DEMO_STREAM_URL),
    )
}

private object DemoEmoteArt {
    fun url(name: String, animated: Boolean) = "demoemote://x/$name/${if (animated) "anim" else "still"}"

    fun aspect(name: String) = when (name) {
        "gg" -> 1.7f
        "wave" -> 2f
        else -> 1f
    }
}

/** Fictional chat with third-party style emotes, replies, mentions and notices: about one message a second. */
private class AppleDemoChat : ChatSource {
    private val users = listOf(
        "mireille_88" to "#BF94FF",
        "Tom_Lefebvre" to "#EFEFF1",
        "ClaraPlays" to "#FF7F50",
        "nuit_blanche" to "#0000FF",
        "Gaspard" to "#2E8B57",
        "petitpois" to null,
        "Lagom_TV" to "#1E1E8C",
        "zouzou" to "#FF69B4",
    )
    private val lines = listOf(
        "Salut tout le monde", "Quelqu'un sait quelle musique est en fond ?", "GG bien joué !", "On est combien ce soir ?",
        "La qualité du flux est top aujourd'hui", "Merci pour le live, ça fait du bien après le boulot", "Allez, on y croit",
        "Salut depuis Lyon !", "J'ai raté le début, un résumé ?", "Belle ambiance ici", "Ce passage était incroyable, on le refait ?",
        "Je mets le son plus fort", "Première fois ici, c'est super bien fait",
    )
    private val thirdParty = listOf("heartPop", "gg", "peepoHey", "catJam", "clapClap", "dancePls", "spinHype")

    override fun events(broadcasterId: String, userId: String): Flow<ChatEvent> = channelFlow {
        launch { DemoBus.events.collect { send(it) } }
        var n = 0
        repeat(14) { send(event(n++)) }
        while (true) {
            delay(1_000)
            send(event(n++))
        }
    }

    private fun event(n: Int): ChatEvent {
        if (n % 17 == 16) return ChatEvent.Notice("demo-notice-$n", "Marlou vient de s'abonner pour 6 mois !", NoticeKind.Resub)
        if (n % 23 == 22) return ChatEvent.Notice("demo-gift-$n", "ClaraPlays offre 5 abonnements à la communauté !", NoticeKind.GiftCommunity)
        if (n % 29 == 28) return ChatEvent.Notice("demo-announce-$n", "Le live continue jusqu'à minuit, n'oubliez pas de vous hydrater.", NoticeKind.Announcement, "BLUE")
        return ChatEvent.Message(message(n))
    }

    private fun message(n: Int): ChatMessage {
        val (name, color) = users[n % users.size]
        val fragments = ArrayList<ChatFragment>()
        val base = lines[(n * 7 + 3) % lines.size]
        var text = base
        if (n % 11 == 5) {
            fragments += ChatFragment("mention", "@Camille", mention = ChatMention("camille"))
            text = " $base"
        }
        fragments += ChatFragment("text", text)
        when (n % 4) {
            0 -> fragments += ChatFragment("emote", "smileTV", ChatEmote("demo-smile", format = listOf("static")))
            1 -> fragments += ChatFragment("text", " ${thirdParty[n % thirdParty.size]} ")
            2 -> fragments += ChatFragment("emote", "starTV", ChatEmote("demo-star", format = listOf("static")))
            else -> Unit
        }
        val full = fragments.joinToString("") { it.text }
        val badges = buildList {
            if (n % 3 == 0) add(ChatBadge("subscriber", "1"))
            if (n % 8 == 1) add(ChatBadge("moderator", "1"))
            if (n % 13 == 4) add(ChatBadge("vip", "1"))
        }
        val reply = if (n > 0 && n % 7 == 3) {
            val (parent, _) = users[(n - 1) % users.size]
            ChatReply("demo-${n - 1}", "Quelqu'un sait quelle musique est en fond ?", parent, parent.lowercase())
        } else {
            null
        }
        return ChatMessage(
            id = "demo-$n", broadcasterId = "b", userId = "u${n % users.size}", username = name, login = name.lowercase(),
            content = ChatMessageContent(full, fragments), badges = badges, color = color, reply = reply,
        )
    }
}

private object AppleDemoChatAssets : ChatAssetsSource, LiteChatAssets {
    private fun still(name: String, label: String, animated: Boolean = false, zeroWidth: Boolean = false) = Emote(
        name = label,
        url = DemoEmoteArt.url(name, false),
        animatedUrl = if (animated) DemoEmoteArt.url(name, true) else null,
        zeroWidth = zeroWidth,
        aspect = DemoEmoteArt.aspect(name),
    )

    private val thirdParty = listOf(
        still("heart", "heartPop"),
        still("gg", "gg"),
        still("smile", "peepoHey"),
        still("bounce", "catJam", animated = true),
        still("clap", "clapClap", animated = true),
        still("wave", "dancePls", animated = true),
        still("spin", "spinHype", animated = true),
    )

    private val groups = listOf(
        EmoteGroup(EmoteSource.Twitch, EmoteSection.Channel, listOf(still("heart", "demoHeart"), still("clap", "demoClap", animated = true), still("star", "demoStar"))),
        EmoteGroup(EmoteSource.Twitch, EmoteSection.Locked, listOf(still("spin", "demoTierThree", animated = true), still("wave", "demoTierTwo", animated = true))),
        EmoteGroup(EmoteSource.Twitch, EmoteSection.Global, listOf(still("smile", "Kappa"), still("star", "PogChamp"), still("heart", "<3"))),
        EmoteGroup(EmoteSource.SevenTv, EmoteSection.Channel, thirdParty),
        EmoteGroup(EmoteSource.SevenTv, EmoteSection.Global, listOf(still("smile", "EZ"), still("gg", "Clap"), still("star", "Pog"), still("heart", "peepoLove"))),
        EmoteGroup(EmoteSource.Bttv, EmoteSection.Global, listOf(still("smile", "OMEGALUL"), still("gg", "monkaS"), still("star", "Sadge"))),
        EmoteGroup(EmoteSource.Ffz, EmoteSection.Global, listOf(still("heart", "ZrehplaR"), still("smile", "LilZ"))),
    )

    private val assets = ChatAssets(
        badges = mapOf(
            "subscriber:1" to DemoEmoteArt.url("badge-sub", false),
            "moderator:1" to DemoEmoteArt.url("badge-mod", false),
            "vip:1" to DemoEmoteArt.url("badge-vip", false),
            "broadcaster:1" to DemoEmoteArt.url("badge-bc", false),
        ),
        emotes = thirdParty.associateBy { it.name },
        groups = groups,
        access = UserEmoteAccess.Known,
    )

    override fun assets(broadcasterId: String): Flow<ChatAssets> = flowOf(assets)

    override fun lite(broadcasterId: String): Flow<ChatAssets> = flowOf(assets)
}

/** Replaces the network-backed pieces so the whole UI runs offline on the fictional data. [signedIn] false shows the sign-in flow. */
fun appleDemoModule(signedIn: Boolean = true) = module {
    single<SessionRepository> { if (signedIn) AppleDemoSession() else AppleDemoSignedOutSession() }
    single<TwitchRepository> { AppleDemoRepository() }
    single<ClipResolver> { AppleDemoClips() }
    single<VodResolver> { AppleDemoVods() }
    single<ChatSource> { AppleDemoChat() }
    single<ChatAssetsSource> { AppleDemoChatAssets }
    single<LiteChatAssets> { AppleDemoChatAssets }
    single<TwitchPlaybackResolver> { AppleDemoResolver() }
}
