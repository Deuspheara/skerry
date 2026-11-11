package com.twitchplayer.tv.feature.player.data.stream

import kotlin.test.Test
import kotlin.test.assertEquals

class FormUrlEncodingTest {
    @Test fun keepsTheUnreservedCharactersOfTheFormEncoding() {
        assertEquals("AZaz09.-*_", formUrlEncode("AZaz09.-*_"))
    }

    @Test fun encodesSpaceAsPlusAndTheTildeAsPercent() {
        assertEquals("a+b", formUrlEncode("a b"))
        assertEquals("%7E", formUrlEncode("~"))
    }

    @Test fun encodesAJsonTokenWithUpperCaseHex() {
        assertEquals(
            "%7B%22channel%22%3A%22zerator%22%2C%22expires%22%3A1%7D",
            formUrlEncode("""{"channel":"zerator","expires":1}"""),
        )
    }

    @Test fun encodesNonAsciiAsUtf8Bytes() {
        assertEquals("%C3%A9", formUrlEncode("é"))
        assertEquals("%E2%82%AC", formUrlEncode("€"))
        assertEquals("%F0%9F%98%80", formUrlEncode("😀"))
    }

    @Test fun anEmptyValueStaysEmpty() {
        assertEquals("", formUrlEncode(""))
    }
}
