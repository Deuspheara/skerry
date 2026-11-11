package com.twitchplayer.tv.core.ui

import java.util.Locale

/**
 * Viewer count in the locale's short form: 14820 -> "14,8 k" (fr), "14.8K" (en), "1.5万" (zh), 1250000 -> "1.25M".
 * Plain digits below 1000. Falls back to a "k" suffix where ICU is unavailable (JVM unit tests).
 */
fun formatViewers(count: Int, locale: Locale = Locale.getDefault()): String {
    if (count < 1000) return java.text.NumberFormat.getIntegerInstance(locale).format(count)
    return try {
        android.icu.text.CompactDecimalFormat.getInstance(locale, android.icu.text.CompactDecimalFormat.CompactStyle.SHORT)
            // One decimal at most, like "6.3K" / "14.8K" / "123K": the digits before the unit, plus one.
            .apply { maximumSignificantDigits = minOf(3, leadingDigits(count) + 1) }
            .format(count)
    } catch (e: Throwable) {
        if (e is VirtualMachineError) throw e
        String.format(locale, "%.1f k", count / 1000.0)
    }
}

/** Digits of [count] in its thousands group (14820 -> 2, 6340 -> 1, 123000 -> 3). */
private fun leadingDigits(count: Int): Int {
    var n = count
    while (n >= 1000) n /= 1000
    return n.toString().length
}
