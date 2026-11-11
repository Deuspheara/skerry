package com.twitchplayer.tv.core.ui

private const val SECONDS_PER_HOUR = 3600
private const val SECONDS_PER_MINUTE = 60
private val TwitchDuration = Regex("""^(?:(\d+)h)?(?:(\d+)m)?(?:(\d+)s)?$""")

/** Live uptime: 3723000 ms -> "1:02:03", under an hour "12:03". */
fun formatUptime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / SECONDS_PER_HOUR
    val m = (total % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
    val s = total % SECONDS_PER_MINUTE
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}" else "$m:${s.toString().padStart(2, '0')}"
}

private fun twitchDurationSeconds(match: MatchResult): Long {
    val (h, min, sec) = match.destructured
    return (h.toLongOrNull() ?: 0) * SECONDS_PER_HOUR + (min.toLongOrNull() ?: 0) * SECONDS_PER_MINUTE + (sec.toLongOrNull() ?: 0)
}

/** Helix VOD duration "3h12m54s" -> seconds (11574); 0 when it does not parse. */
fun parseTwitchDurationSeconds(raw: String): Int {
    val match = TwitchDuration.matchEntire(raw) ?: return 0
    return twitchDurationSeconds(match).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}

/** Helix VOD duration "3h12m54s" -> "3:12:54", "12m5s" -> "12:05"; anything else is returned as-is. */
fun formatTwitchDuration(raw: String): String {
    val match = TwitchDuration.matchEntire(raw) ?: return raw
    if (raw.isEmpty()) return raw
    return formatUptime(twitchDurationSeconds(match) * 1000)
}
