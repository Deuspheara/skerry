package com.twitchplayer.phone.feature.player

import com.twitchplayer.tv.feature.player.data.stream.QualityPolicy
import com.twitchplayer.tv.feature.player.data.stream.StreamVariant
import com.twitchplayer.tv.testing.FakeSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReplayQualityTest {
    private val source = StreamVariant("chunked", "1080p60 (source)", 6_000_000, 1920, 1080, 60f, "avc1", "https://v/chunked.m3u8")
    private val p720 = StreamVariant("720p60", "720p60", 3_000_000, 1280, 720, 60f, "avc1", "https://v/720p60.m3u8")
    private val p480 = StreamVariant("480p30", "480p", 1_000_000, 852, 480, 30f, "avc1", "https://v/480p.m3u8")
    private val variants = listOf(source, p720, p480)

    @Test fun aSavedQualityThisReplayHasIsTheOneItStartsIn() {
        assertEquals("480p30", ReplayQuality.initial(variants, preferred = "480p30"))
    }

    @Test fun aSavedQualityThisReplayLacksFallsBackToTheDefaultPolicy() {
        assertEquals(QualityPolicy.default(variants)?.id, ReplayQuality.initial(variants, preferred = "360p30"))
    }

    @Test fun savedAutoStartsOnTheAdaptivePlaylist() {
        assertEquals(QualityPolicy.AUTO, ReplayQuality.initial(variants, preferred = QualityPolicy.AUTO))
    }

    @Test fun optionsListAutoFirstThenEveryVariantAndMarkTheSelectedOne() {
        val options = ReplayQuality.options(variants, autoLabel = "Auto", selected = "720p60")

        assertEquals(listOf("auto", "chunked", "720p60", "480p30"), options.options.map { it.id })
        assertEquals("Auto", options.options.first().label)
        assertEquals("720p60", options.selected)
    }

    @Test fun eachQualityOpensItsOwnPlaylistAndAutoOpensTheMasterOne() {
        assertEquals("https://v/720p60.m3u8", ReplayQuality.urlFor(variants, "https://v/master.m3u8", "720p60"))
        assertEquals("https://v/master.m3u8", ReplayQuality.urlFor(variants, "https://v/master.m3u8", QualityPolicy.AUTO))
        assertEquals("https://v/master.m3u8", ReplayQuality.urlFor(variants, "https://v/master.m3u8", "gone"))
    }

    @Test fun pickingAQualityIsSavedAsTheViewersChoiceForTheNextStream() = runTest {
        val settings = FakeSettings()

        QualityChoice(settings, this).pick("480p30")
        advanceUntilIdle()

        assertEquals("480p30", settings.settings.value.preferredQuality)
        assertEquals("480p30", ReplayQuality.initial(variants, settings.settings.value.preferredQuality))
    }
}
