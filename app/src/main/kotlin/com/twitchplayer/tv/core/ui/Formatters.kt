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

/** Live uptime: 3723000 ms -> "1:02:03", under an hour "12:03". */
fun formatUptime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) else String.format(Locale.ROOT, "%d:%02d", m, s)
}

/** Digits of [count] in its thousands group (14820 -> 2, 6340 -> 1, 123000 -> 3). */
private fun leadingDigits(count: Int): Int {
    var n = count
    while (n >= 1000) n /= 1000
    return n.toString().length
}

/** Helix VOD duration "3h12m54s" -> seconds (11574); 0 when it does not parse. */
fun parseTwitchDurationSeconds(raw: String): Int {
    val m = Regex("""^(?:(\d+)h)?(?:(\d+)m)?(?:(\d+)s)?$""").matchEntire(raw) ?: return 0
    val (h, min, sec) = m.destructured
    return ((h.toLongOrNull() ?: 0) * 3600 + (min.toLongOrNull() ?: 0) * 60 + (sec.toLongOrNull() ?: 0)).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}

/** Helix VOD duration "3h12m54s" -> "3:12:54", "12m5s" -> "12:05"; anything else is returned as-is. */
fun formatTwitchDuration(raw: String): String {
    val m = Regex("""^(?:(\d+)h)?(?:(\d+)m)?(?:(\d+)s)?$""").matchEntire(raw) ?: return raw
    if (raw.isEmpty()) return raw
    val (h, min, sec) = m.destructured
    return formatUptime(((h.toLongOrNull() ?: 0) * 3600 + (min.toLongOrNull() ?: 0) * 60 + (sec.toLongOrNull() ?: 0)) * 1000)
}
