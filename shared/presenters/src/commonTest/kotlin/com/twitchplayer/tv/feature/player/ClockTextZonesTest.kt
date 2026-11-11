package com.twitchplayer.tv.feature.player

import com.twitchplayer.tv.feature.player.presentation.clockText
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `clockText` in explicit zones, with expected values computed independently of the implementation (Python's zoneinfo)
 * for instants right around daylight-saving transitions and for zones that are not whole hours from UTC.
 */
class ClockTextZonesTest {
    private fun check(zone: String, epochMillis: Long, expected: String) = assertEquals(expected, clockText(epochMillis, zone), "$zone @ $epochMillis")

    @Test fun parisSpringForwardSkipsFromOneFiftyNineToThree() {
        check("Europe/Paris", 1_774_745_999_000L, "01:59") // 2026-03-29T00:59:59Z, last second of CET
        check("Europe/Paris", 1_774_746_000_000L, "03:00") // 01:00:00Z, first second of CEST
    }

    @Test fun parisFallBackRepeatsTheTwoOClockHour() {
        check("Europe/Paris", 1_792_889_999_000L, "02:59") // 2026-10-25T00:59:59Z, still CEST
        check("Europe/Paris", 1_792_890_000_000L, "02:00") // 01:00:00Z, CET: the hour repeats
        check("Europe/Paris", 1_792_893_599_000L, "02:59") // 01:59:59Z
    }

    @Test fun losAngelesTransitionsAreOnDifferentDatesThanParis() {
        check("America/Los_Angeles", 1_772_963_999_000L, "01:59") // 2026-03-08T09:59:59Z
        check("America/Los_Angeles", 1_772_964_000_000L, "03:00")
        check("America/Los_Angeles", 1_793_523_599_000L, "01:59") // 2026-11-01T08:59:59Z
        check("America/Los_Angeles", 1_793_523_600_000L, "01:00")
    }

    @Test fun halfAndQuarterHourZones() {
        check("Asia/Kolkata", 0L, "05:30")
        check("Asia/Kathmandu", 1_794_355_200_000L, "05:45") // 2026-11-11T00:00:00Z
        check("Pacific/Chatham", 1_780_315_200_000L, "00:45") // 2026-06-01T12:00:00Z, UTC+12:45 in winter
        check("Australia/Lord_Howe", 1_780_315_200_000L, "22:30") // UTC+10:30 in winter
        check("Australia/Lord_Howe", 1_768_478_400_000L, "23:00") // 2026-01-15T12:00:00Z, UTC+11:00 in summer
    }

    @Test fun utcAndMidnightEdges() {
        check("UTC", 1_794_441_599_000L, "23:59")
        check("UTC", 1_794_441_600_000L, "00:00")
        check("UTC", 0L, "00:00")
    }
}
