package com.twitchplayer.tv.feature.player.presentation

/**
 * Hour (0-23) and minute (0-59) of [epochMillis] on the wall clock of the time zone [zoneId] (an IANA id such as
 * "Europe/Paris"), or of the device's current time zone when null. The platform owns the zone database, so this is the
 * one place chat lines need `expect/actual` (a datetime library would add ~170 classes to the release build for it).
 */
internal expect fun timeOfDay(epochMillis: Long, zoneId: String? = null): Pair<Int, Int>
