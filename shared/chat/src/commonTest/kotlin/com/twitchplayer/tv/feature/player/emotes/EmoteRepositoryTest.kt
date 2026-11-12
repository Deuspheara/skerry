package com.twitchplayer.tv.feature.player.emotes

import com.twitchplayer.tv.feature.player.data.emotes.Emote
import com.twitchplayer.tv.feature.player.data.emotes.EmoteChange
import com.twitchplayer.tv.feature.player.data.emotes.EmoteProvider
import com.twitchplayer.tv.feature.player.data.emotes.EmoteRepository
import com.twitchplayer.tv.feature.player.data.emotes.EmoteSection
import com.twitchplayer.tv.feature.player.data.emotes.EmoteSource
import com.twitchplayer.tv.feature.player.data.emotes.applyChange
import com.twitchplayer.tv.feature.player.data.emotes.mergeEmoteLayers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun emote(name: String, from: String) = Emote(name, "https://$from/$name")

private class FakeProvider(
    private val global: List<Emote> = emptyList(),
    var channel: List<Emote> = emptyList(),
    private val failing: Boolean = false,
    val pushes: MutableSharedFlow<EmoteChange> = MutableSharedFlow(extraBufferCapacity = 8),
    private val gate: CompletableDeferred<Unit>? = null,
    override val source: EmoteSource = EmoteSource.Other,
) : EmoteProvider {
    var globalCalls = 0
    var channelCalls = 0
    override suspend fun global(): List<Emote> {
        globalCalls++
        gate?.await()
        if (failing) error("down")
        return global
    }
    override suspend fun channel(channelId: String): List<Emote> {
        channelCalls++
        gate?.await()
        if (failing) error("down")
        return channel
    }
    override fun changes(channelId: String): Flow<EmoteChange> = pushes
}

@OptIn(ExperimentalCoroutinesApi::class)
class EmoteRepositoryTest {
    @Test fun channelBeatsGlobalAndProviderOrderBreaksTies() = runTest {
        val sevenTv = FakeProvider(global = listOf(emote("Same", "7tv-global")), channel = listOf(emote("OnlyChannel", "7tv-channel")))
        val bttv = FakeProvider(global = listOf(emote("Same", "bttv-global"), emote("Both", "bttv-global")), channel = listOf(emote("Both", "bttv-channel"), emote("OnlyChannel", "bttv-channel")))
        val ffz = FakeProvider(channel = listOf(emote("Both", "ffz-channel"), emote("Solo", "ffz-channel")))
        val merged = EmoteRepository(listOf(sevenTv, bttv, ffz)).emotes("c").first()
        assertEquals("https://7tv-channel/OnlyChannel", merged.getValue("OnlyChannel").url, "7TV is first in the list: wins the channel tie")
        assertEquals("https://bttv-channel/Both", merged.getValue("Both").url, "a channel emote of a lower-priority provider still beats a global one")
        assertEquals("https://7tv-global/Same", merged.getValue("Same").url, "global tie: 7TV before BTTV")
        assertEquals("https://ffz-channel/Solo", merged.getValue("Solo").url)
    }

    @Test fun snapshotsListEachProvidersChannelEmotesBeforeItsGlobalOnesAndSkipEmptyOrFailedLists() = runTest {
        val sevenTv = FakeProvider(global = listOf(emote("G7", "a")), channel = listOf(emote("C7", "a")), source = EmoteSource.SevenTv)
        val bttv = FakeProvider(failing = true, source = EmoteSource.Bttv)
        val ffz = FakeProvider(global = listOf(emote("GF", "c")), source = EmoteSource.Ffz)
        val groups = EmoteRepository(listOf(sevenTv, bttv, ffz)).snapshots("c").first().groups
        assertEquals(
            listOf(
                Triple(EmoteSource.SevenTv, EmoteSection.Channel, listOf("C7")),
                Triple(EmoteSource.SevenTv, EmoteSection.Global, listOf("G7")),
                Triple(EmoteSource.Ffz, EmoteSection.Global, listOf("GF")),
            ),
            groups.map { Triple(it.source, it.section, it.emotes.map(Emote::name)) },
        )
    }

    @Test fun globalTieIsDecidedByProviderOrder() {
        val merged = mergeEmoteLayers(listOf(listOf(emote("X", "ffz")), listOf(emote("X", "bttv")), listOf(emote("X", "7tv"))))
        assertEquals("https://7tv/X", merged.getValue("X").url)
    }

    @Test fun namesAreCaseSensitive() {
        val merged = mergeEmoteLayers(listOf(listOf(emote("Kappa", "a"), emote("kappa", "b"))))
        assertEquals(2, merged.size)
    }

    @Test fun aFailingProviderOnlyLosesItsOwnEmotes() = runTest {
        val good = FakeProvider(global = listOf(emote("Good", "g")), channel = listOf(emote("Chan", "g")))
        val bad = FakeProvider(failing = true)
        val merged = EmoteRepository(listOf(bad, good)).emotes("c").first()
        assertEquals(setOf("Good", "Chan"), merged.keys)
    }

    @Test fun aSlowProviderDoesNotHoldTheFirstSnapshotBackAndJoinsWhenItAnswers() = runTest {
        val gate = CompletableDeferred<Unit>()
        val slow = FakeProvider(channel = listOf(emote("Late", "slow")), gate = gate)
        val fast = FakeProvider(channel = listOf(emote("Early", "fast")))
        val seen = mutableListOf<Set<String>>()
        EmoteRepository(listOf(slow, fast), initialWaitMs = 1_000).emotes("c").onEach { seen += it.keys }.launchIn(backgroundScope)
        advanceTimeBy(999)
        runCurrent()
        assertTrue(seen.isEmpty(), "nothing yet: waiting for the providers")
        advanceTimeBy(2)
        runCurrent()
        assertEquals(listOf(setOf("Early")), seen)
        gate.complete(Unit)
        runCurrent()
        assertEquals(setOf("Early", "Late"), seen.last())
    }

    @Test fun metadataIsCachedAcrossCollectionsUntilItExpires() = runTest {
        var clock = 0L
        val provider = FakeProvider(global = listOf(emote("G", "p")), channel = listOf(emote("C", "p")))
        val repo = EmoteRepository(listOf(provider), now = { clock })
        repo.emotes("c").first()
        repo.emotes("c").first()
        assertEquals(1, provider.channelCalls, "zapping back to a channel does not refetch")
        assertEquals(1, provider.globalCalls)
        repo.emotes("other").first()
        assertEquals(2, provider.channelCalls, "another channel fetches its own set, the global one is shared")
        assertEquals(1, provider.globalCalls)
        clock += EmoteRepository.CHANNEL_TTL_MS + 1
        repo.emotes("c").first()
        assertEquals(3, provider.channelCalls, "expired channel metadata is fetched again")
        assertEquals(1, provider.globalCalls)
    }

    @Test fun livePushesAndPeriodicRefreshUpdateTheMap() = runTest {
        val provider = FakeProvider(channel = listOf(emote("Old", "p"), emote("Keep", "p")))
        val seen = mutableListOf<Map<String, Emote>>()
        EmoteRepository(listOf(provider), refreshMs = 60_000).emotes("c").onEach { seen += it }.launchIn(backgroundScope)
        runCurrent()
        assertEquals(setOf("Old", "Keep"), seen.last().keys)
        provider.pushes.emit(EmoteChange(added = listOf(emote("New", "p")), removed = setOf("Old")))
        runCurrent()
        assertEquals(setOf("Keep", "New"), seen.last().keys)
        provider.channel = listOf(emote("Keep", "p"), emote("FromRefresh", "p"))
        advanceTimeBy(60_001)
        runCurrent()
        assertEquals(setOf("Keep", "FromRefresh"), seen.last().keys)
    }

    @Test fun applyChangeReplacesByNameAndRemovesFirst() {
        val result = applyChange(listOf(emote("A", "1"), emote("B", "1")), EmoteChange(added = listOf(emote("A", "2")), removed = setOf("B")))
        assertEquals(listOf("https://2/A"), result.map { it.url })
    }
}
