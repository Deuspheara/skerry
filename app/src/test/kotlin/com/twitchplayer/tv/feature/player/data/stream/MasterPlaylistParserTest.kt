package com.twitchplayer.tv.feature.player.data.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MasterPlaylistParserTest {
    // Shape of a real usher answer (urls shortened).
    private val master = """
        #EXTM3U
        #EXT-X-TWITCH-INFO:ORIGIN="s3",B="false",REGION="EU",USER-IP="1.2.3.4",SERVING-ID="abc",CLUSTER="fra05"
        #EXT-X-MEDIA:TYPE=VIDEO,GROUP-ID="chunked",NAME="1080p60 (source)",AUTOSELECT=YES,DEFAULT=YES
        #EXT-X-STREAM-INF:PROGRAM-ID=1,BANDWIDTH=8000000,RESOLUTION=1920x1080,CODECS="avc1.64002A,mp4a.40.2",VIDEO="chunked",FRAME-RATE=60.000
        https://weaver.example/v1/playlist/chunked.m3u8
        #EXT-X-MEDIA:TYPE=VIDEO,GROUP-ID="720p60",NAME="720p60",AUTOSELECT=YES,DEFAULT=YES
        #EXT-X-STREAM-INF:PROGRAM-ID=1,BANDWIDTH=3400000,RESOLUTION=1280x720,CODECS="avc1.4D401F,mp4a.40.2",VIDEO="720p60",FRAME-RATE=60.000
        https://weaver.example/v1/playlist/720p60.m3u8
        #EXT-X-MEDIA:TYPE=VIDEO,GROUP-ID="480p30",NAME="480p",AUTOSELECT=YES,DEFAULT=YES
        #EXT-X-STREAM-INF:PROGRAM-ID=1,BANDWIDTH=1400000,RESOLUTION=852x480,CODECS="avc1.4D401F,mp4a.40.2",VIDEO="480p30",FRAME-RATE=30.000
        https://weaver.example/v1/playlist/480p30.m3u8
        #EXT-X-MEDIA:TYPE=VIDEO,GROUP-ID="audio_only",NAME="audio_only",AUTOSELECT=NO,DEFAULT=NO
        #EXT-X-STREAM-INF:PROGRAM-ID=1,BANDWIDTH=160000,CODECS="mp4a.40.2",VIDEO="audio_only"
        https://weaver.example/v1/playlist/audio_only.m3u8
        #EXT-X-MEDIA:TYPE=VIDEO,GROUP-ID="160p30",NAME="160p",AUTOSELECT=YES,DEFAULT=YES
        #EXT-X-STREAM-INF:PROGRAM-ID=1,BANDWIDTH=230000,RESOLUTION=284x160,CODECS="avc1.4D400C,mp4a.40.2",VIDEO="160p30",FRAME-RATE=30.000
        https://weaver.example/v1/playlist/160p30.m3u8
    """.trimIndent()

    @Test fun readsEveryVariantBestFirstAndAudioOnlyLast() {
        val variants = MasterPlaylistParser.parse(master)
        assertEquals(listOf("chunked", "720p60", "480p30", "160p30", "audio_only"), variants.map { it.id })
        val source = variants.first()
        assertEquals("1080p60 (source)", source.name)
        assertEquals(1920, source.width); assertEquals(1080, source.height); assertEquals(60f, source.frameRate, 0f)
        assertEquals("https://weaver.example/v1/playlist/chunked.m3u8", source.url)
        assertTrue(source.isSource)
        assertTrue(variants.last().audioOnly)
        assertEquals(0, variants.last().height)
    }

    @Test fun anythingThatIsNotAPlaylistGivesNoVariants() {
        assertTrue(MasterPlaylistParser.parse("").isEmpty())
        assertTrue(MasterPlaylistParser.parse("<html>nope</html>").isEmpty())
        assertTrue(MasterPlaylistParser.parse("#EXTM3U\n#EXT-X-VERSION:3").isEmpty())
    }

    @Test fun defaultIs720p60ThenSource1080p30ThenBestUnder1080() {
        val parsed = MasterPlaylistParser.parse(master)
        assertEquals("720p60", QualityPolicy.default(parsed)?.id)

        val no720 = parsed.filter { it.id != "720p60" }
        assertEquals("chunked", QualityPolicy.default(no720)?.id) // 1080p60 is the best that is not above 1080p

        val v1080p30 = StreamVariant("chunked", "1080p (source)", 6_000_000, 1920, 1080, 30f, "avc1", "u1")
        val v720p30 = StreamVariant("720p30", "720p", 2_500_000, 1280, 720, 30f, "avc1", "u2")
        assertEquals("chunked", QualityPolicy.default(listOf(v1080p30, v720p30))?.id) // 1080p30 beats 720p30

        val audio = parsed.last()
        assertEquals("audio_only", QualityPolicy.default(listOf(audio))?.id)
        assertNull(QualityPolicy.default(emptyList()))
    }

    @Test fun aPreferredQualityWinsWhenTheStreamHasItAndAutoMeansTheMasterPlaylist() {
        val parsed = MasterPlaylistParser.parse(master)
        assertEquals("480p30", QualityPolicy.choose(parsed, "480p30")?.id)
        assertEquals("720p60", QualityPolicy.choose(parsed, "1440p60")?.id) // not offered: falls back to the default
        assertNull(QualityPolicy.choose(parsed, QualityPolicy.AUTO))
    }
}
