package com.twitchplayer.tv.feature.player

import com.twitchplayer.tv.feature.player.presentation.clockText
import com.twitchplayer.tv.feature.player.presentation.cycleIndex
import com.twitchplayer.tv.feature.player.presentation.defaultNickColor
import java.util.Calendar
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/** The common code replaced `Math.floorMod`, `Math.pow` and `Calendar`: on the JVM the results must be the old ones. */
class ChatLineJvmParityTest {
    @Test fun cycleIndexEqualsFloorMod() {
        val cases = (2..9).flatMap { size -> (-1 until size).flatMap { index -> (-3..3).map { delta -> Triple(size, index, delta) } } }
        for ((size, index, delta) in cases) {
            val old = if (index < 0) (if (delta > 0) 0 else size - 1) else Math.floorMod(index + delta, size)
            assertEquals(old, cycleIndex(index, delta, size), "index=$index delta=$delta size=$size")
        }
    }

    @Test fun nickColorIndexEqualsFloorModOfTheHash() {
        val random = Random(42)
        val logins = List(500) { String(CharArray(random.nextInt(1, 25)) { random.nextInt('!'.code, '~'.code).toChar() }) } + listOf("camille", "", "ZED")
        for (login in logins) {
            val hash = login.lowercase().hashCode()
            assertEquals(Math.floorMod(hash, 12), hash.mod(12), login)
            // And the colour is a function of that index only, so the same login keeps the same hue.
            assertEquals(defaultNickColor(login), defaultNickColor(login.uppercase()))
        }
    }

    @Test fun clockTextEqualsTheOldCalendarFormatInTheDefaultZone() {
        val random = Random(7)
        repeat(300) {
            val millis = random.nextLong(0, 4_100_000_000_000)
            val c = Calendar.getInstance().apply { timeInMillis = millis }
            val old = "${if (c.get(Calendar.HOUR_OF_DAY) < 10) "0" else ""}${c.get(Calendar.HOUR_OF_DAY)}:${if (c.get(Calendar.MINUTE) < 10) "0" else ""}${c.get(Calendar.MINUTE)}"
            assertEquals(old, clockText(millis), "millis=$millis")
        }
    }
}
