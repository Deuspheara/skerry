package com.twitchplayer.tv.feature.player.data.stream

import java.net.URLEncoder
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/** The shared encoder must match the JDK encoder the usher URLs were built with before the move to common. */
class FormUrlEncodingJdkParityTest {
    @Test fun matchesUrlEncoderOnEveryAsciiCharacter() {
        for (code in 0..127) {
            val s = code.toChar().toString()
            assertEquals(URLEncoder.encode(s, "UTF-8"), formUrlEncode(s), "char $code")
        }
    }

    @Test fun matchesUrlEncoderOnRandomStrings() {
        val random = Random(7)
        repeat(2_000) {
            val s = buildString {
                repeat(random.nextInt(0, 40)) {
                    when (random.nextInt(4)) {
                        0 -> append(random.nextInt(32, 127).toChar())
                        1 -> append(random.nextInt(0xA0, 0xD7FF).toChar())
                        2 -> appendCodePoint(random.nextInt(0x10000, 0x10FFFF))
                        else -> append("{}\":, ~*+%/=?&")
                    }
                }
            }
            assertEquals(URLEncoder.encode(s, "UTF-8"), formUrlEncode(s), s)
        }
    }
}
