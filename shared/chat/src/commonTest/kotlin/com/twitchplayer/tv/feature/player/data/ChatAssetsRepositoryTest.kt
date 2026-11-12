package com.twitchplayer.tv.feature.player.data

import com.twitchplayer.tv.core.data.model.Account
import com.twitchplayer.tv.core.data.repository.SCOPE_READ_EMOTES
import com.twitchplayer.tv.core.data.repository.SessionRepository
import com.twitchplayer.tv.core.data.repository.SessionState
import com.twitchplayer.tv.core.network.api.TokenSource
import com.twitchplayer.tv.core.network.api.TwitchApi
import com.twitchplayer.tv.core.network.api.TwitchEmote
import com.twitchplayer.tv.core.network.auth.DeviceCodeResponse
import com.twitchplayer.tv.feature.player.data.emotes.Emote
import com.twitchplayer.tv.feature.player.data.emotes.EmoteProvider
import com.twitchplayer.tv.feature.player.data.emotes.EmoteRepository
import com.twitchplayer.tv.feature.player.data.emotes.EmoteSection
import com.twitchplayer.tv.feature.player.data.emotes.EmoteSource
import com.twitchplayer.tv.feature.player.data.emotes.UserEmoteAccess
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private class FakeSession(account: Account? = null, private val scopes: Set<String> = emptySet()) : SessionRepository {
    override val state: StateFlow<SessionState> = MutableStateFlow(if (account == null) SessionState.SignedOut else SessionState.SignedIn(account))
    override fun start() = Unit
    override suspend fun signIn(onCode: (DeviceCodeResponse) -> Unit) = false
    override suspend fun signOut() = Unit
    override fun current(): String? = "token"
    override suspend fun refresh(rejected: String?): String? = null
    override fun hasScope(scope: String): Boolean = scope in scopes
}

private class OneProvider(override val source: EmoteSource, private val global: List<Emote>, private val failing: Boolean = false) : EmoteProvider {
    override suspend fun global(): List<Emote> = if (failing) error("down") else global
    override suspend fun channel(channelId: String): List<Emote> = emptyList()
}

private const val GLOBAL = """{"data":[{"id":"25","name":"Kappa","emote_type":"globals","format":["static"]},{"id":"1","name":":)","emote_type":"smilies","format":["static"]}]}"""
private const val CHANNEL = """{"data":[
  {"id":"c1","name":"chanHi","emote_type":"subscriptions","tier":"1000","owner_id":"42","format":["static","animated"]},
  {"id":"c2","name":"chanFollow","emote_type":"follower","owner_id":"42","format":["static"]},
  {"id":"c3","name":"chanT3","emote_type":"subscriptions","tier":"3000","owner_id":"42","format":["static"]}]}"""
private const val USER_PAGE_1 = """{"data":[{"id":"25","name":"Kappa","emote_type":"globals","owner_id":"0"},{"id":"c1","name":"chanHi","emote_type":"subscriptions","owner_id":"42"},{"id":"o1","name":"otherSub","emote_type":"subscriptions","owner_id":"77"}],"pagination":{"cursor":"p2"}}"""
private const val USER_PAGE_2 = """{"data":[{"id":"o2","name":"otherBits","emote_type":"bitstier","owner_id":"77"}],"pagination":{}}"""

class ChatAssetsRepositoryTest {
    private val urls = mutableListOf<String>()

    private fun api(userStatus: HttpStatusCode = HttpStatusCode.OK, globalStatus: HttpStatusCode = HttpStatusCode.OK): TwitchApi {
        val http = HttpClient(
            MockEngine { request ->
                val url = request.url.toString()
                urls += url
                val (status, body) = when {
                    "chat/emotes/global" in url -> globalStatus to GLOBAL
                    "chat/emotes/user" in url -> userStatus to (if ("after=p2" in url) USER_PAGE_2 else USER_PAGE_1)
                    "chat/emotes?" in url -> HttpStatusCode.OK to CHANNEL
                    else -> HttpStatusCode.OK to """{"data":[]}"""
                }
                respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
            },
        ) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        return TwitchApi(
            http,
            "cid",
            object : TokenSource {
                override fun current() = "token"
                override suspend fun refresh(rejected: String?): String? = null
            },
        )
    }

    private fun repository(session: SessionRepository, api: TwitchApi, providers: List<EmoteProvider> = emptyList()) = ChatAssetsRepository(api, EmoteRepository(providers, initialWaitMs = 50), session)

    private suspend fun ChatAssetsRepository.loaded(): ChatAssets = assets("42").first { it.groups.any { g -> g.source == EmoteSource.Twitch } }

    @Test fun withoutTheScopeEveryChannelEmoteIsUsableAndTheViewerIsToldToSignInAgain() = runTest {
        val assets = repository(FakeSession(Account("9", "me"), scopes = emptySet()), api()).loaded()
        assertEquals(UserEmoteAccess.NeedsSignIn, assets.access)
        val channel = assets.groups.single { it.section == EmoteSection.Channel }
        assertEquals(listOf("chanHi", "chanFollow", "chanT3"), channel.emotes.map { it.name })
        assertTrue(assets.groups.none { it.section == EmoteSection.Locked || it.section == EmoteSection.OtherChannels })
        assertTrue(urls.none { "chat/emotes/user" in it }, "no call that needs the scope")
    }

    @Test fun signedOutOnlyGlobalAndChannelListsAreLoaded() = runTest {
        val assets = repository(FakeSession(), api()).loaded()
        assertEquals(UserEmoteAccess.Unavailable, assets.access)
        assertEquals(listOf(EmoteSection.Channel, EmoteSection.Global), assets.groups.map { it.section })
    }

    @Test fun withTheScopeUsableLockedAndOtherChannelEmotesAreToldApart() = runTest {
        val assets = repository(FakeSession(Account("9", "me"), setOf(SCOPE_READ_EMOTES)), api()).loaded()
        assertEquals(UserEmoteAccess.Known, assets.access)
        fun names(section: EmoteSection) = assets.groups.single { it.section == section }.emotes.map { it.name }
        assertEquals(listOf("chanHi", "chanFollow"), names(EmoteSection.Channel), "chanHi is in the viewer's emotes; a follower emote is never called locked")
        assertEquals(listOf("chanT3"), names(EmoteSection.Locked))
        assertEquals(listOf("Kappa", ":)"), names(EmoteSection.Global))
        assertEquals(listOf("otherSub", "otherBits"), names(EmoteSection.OtherChannels), "global and this channel's emotes are not repeated")
        assertEquals(2, urls.count { "chat/emotes/user" in it }, "both pages were read")
        assertTrue(urls.none { "broadcaster_id" in it && "chat/emotes/user" in it }, "the parameter Twitch removed is not sent")
    }

    @Test fun aFailingUserEmotesCallFallsBackToTheUnfilteredChannelList() = runTest {
        val assets = repository(FakeSession(Account("9", "me"), setOf(SCOPE_READ_EMOTES)), api(userStatus = HttpStatusCode.InternalServerError)).loaded()
        assertEquals(UserEmoteAccess.Unavailable, assets.access)
        assertEquals(3, assets.groups.single { it.section == EmoteSection.Channel }.emotes.size)
        assertTrue(assets.groups.none { it.section == EmoteSection.Locked })
    }

    @Test fun aFailingGlobalCallOnlyLosesTheGlobalList() = runTest {
        val assets = repository(FakeSession(), api(globalStatus = HttpStatusCode.ServiceUnavailable)).loaded()
        assertEquals(listOf(EmoteSection.Channel), assets.groups.map { it.section })
    }

    @Test fun twitchEmotesUseTheDocumentedCdnTemplateAndAnimatedOnlyWhenListed() = runTest {
        val channel = repository(FakeSession(), api()).loaded().groups.single { it.section == EmoteSection.Channel }.emotes
        assertEquals("https://static-cdn.jtvnw.net/emoticons/v2/c1/static/dark/2.0", channel[0].url)
        assertEquals("https://static-cdn.jtvnw.net/emoticons/v2/c1/animated/dark/2.0", channel[0].animatedUrl)
        assertEquals(null, channel[1].animatedUrl)
    }

    @Test fun providerListsFollowTwitchsAndAFailingProviderHasNone() = runTest {
        val providers = listOf(
            OneProvider(EmoteSource.SevenTv, listOf(Emote("seven", "u"))),
            OneProvider(EmoteSource.Bttv, emptyList(), failing = true),
            OneProvider(EmoteSource.Ffz, listOf(Emote("ffz", "u"))),
        )
        val assets = repository(FakeSession(), api(), providers).assets("42").first { a -> a.groups.map { it.source }.toSet().containsAll(listOf(EmoteSource.Twitch, EmoteSource.SevenTv, EmoteSource.Ffz)) }
        assertEquals(listOf(EmoteSource.Twitch, EmoteSource.Twitch, EmoteSource.SevenTv, EmoteSource.Ffz), assets.groups.map { it.source })
        assertEquals(setOf("seven", "ffz"), assets.emotes.keys, "chat lines still resolve third-party words only")
    }

    @Test fun theViewersEmotesAreKeptForTheNextChannel() = runTest {
        val repository = repository(FakeSession(Account("9", "me"), setOf(SCOPE_READ_EMOTES)), api())
        repository.loaded()
        val before = urls.count { "chat/emotes/user" in it }
        repository.assets("43").firstOrNull { it.groups.isNotEmpty() }
        assertNotNull(before)
        assertEquals(before, urls.count { "chat/emotes/user" in it }, "zapping does not re-read the viewer's emotes")
    }

    @Test fun theLiteAssetsAreBadgesAndThirdPartyEmotesOnlyWithNoEmoteListsAndNoUserEmotePaging() = runTest {
        val provider = OneProvider(EmoteSource.SevenTv, listOf(Emote("catJam", "https://x/catJam")))
        val repo = repository(FakeSession(Account("9", "me"), setOf(SCOPE_READ_EMOTES)), api(), listOf(provider))
        val assets = repo.lite("42").first()
        assertEquals(setOf("catJam"), assets.emotes.keys)
        assertTrue(assets.groups.isEmpty(), "no picker lists in a chat that is only read")
        assertTrue(urls.none { "chat/emotes" in it }, "no Twitch emote list, no viewer emotes: $urls")
        assertTrue(urls.any { "chat/badges" in it }, "the badges are still fetched")
    }

    @Test fun theChannelBadgesOfAChannelJustSeenAreNotFetchedAgain() = runTest {
        val repo = repository(FakeSession(Account("9", "me")), api())
        repo.lite("42").first()
        val before = urls.count { "chat/badges?" in it }
        repo.lite("42").first()
        assertEquals(before, urls.count { "chat/badges?" in it }, "the second look at the same channel used the cache")
        assertEquals(1, before)
    }
}
