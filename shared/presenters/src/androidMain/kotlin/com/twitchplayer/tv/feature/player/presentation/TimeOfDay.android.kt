package com.twitchplayer.tv.feature.player.presentation

import java.util.Calendar
import java.util.TimeZone

internal actual fun timeOfDay(epochMillis: Long, zoneId: String?): Pair<Int, Int> {
    val calendar = if (zoneId == null) Calendar.getInstance() else Calendar.getInstance(TimeZone.getTimeZone(zoneId))
    calendar.timeInMillis = epochMillis
    return calendar.get(Calendar.HOUR_OF_DAY) to calendar.get(Calendar.MINUTE)
}
