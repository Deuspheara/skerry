package com.twitchplayer.tv.feature.player.presentation

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSDate
import platform.Foundation.NSTimeZone
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeZoneWithName

private const val MILLIS_PER_SECOND = 1000.0

@OptIn(ExperimentalForeignApi::class)
internal actual fun timeOfDay(epochMillis: Long, zoneId: String?): Pair<Int, Int> {
    val calendar = NSCalendar.currentCalendar
    val zone = zoneId?.let { NSTimeZone.timeZoneWithName(it) }
    val date = NSDate.dateWithTimeIntervalSince1970(epochMillis / MILLIS_PER_SECOND)
    // The shared current calendar must not be mutated: ask with an explicit zone through a copy.
    val local = (calendar.copy() as NSCalendar).also { if (zone != null) it.timeZone = zone }
    val parts = local.components(NSCalendarUnitHour or NSCalendarUnitMinute, fromDate = date)
    return parts.hour.toInt() to parts.minute.toInt()
}
