package com.twitchplayer.tv.core.data.repository

import com.twitchplayer.tv.core.data.model.Category
import com.twitchplayer.tv.core.data.model.CategoryGroup
import com.twitchplayer.tv.core.data.model.LiveStream
import com.twitchplayer.tv.core.network.api.TwitchGame
import com.twitchplayer.tv.core.network.api.TwitchStream
import com.twitchplayer.tv.core.platform.WallClock
import kotlin.time.Instant

/** Fills Twitch's `{width}x{height}` template (also the `%{width}` form used by videos). */
internal fun sizedImage(template: String, width: Int, height: Int): String = template.replace("%{width}", width.toString()).replace("%{height}", height.toString())
    .replace("{width}", width.toString()).replace("{height}", height.toString())

/**
 * A live thumbnail URL never changes while the picture does, and Coil caches by URL without HTTP cache headers: a
 * 5-minute bucket in the query makes a new frame load at most every 5 minutes (Twitch refreshes them about that often).
 */
internal fun freshThumbnail(url: String, nowMs: Long = WallClock.System.nowMillis()): String = if (url.isEmpty()) url else url + (if ('?' in url) '&' else '?') + "t=" + nowMs / THUMBNAIL_BUCKET_MS

private const val THUMBNAIL_BUCKET_MS = 5 * 60_000L

internal fun TwitchStream.toLive(avatars: Map<String, String>) = LiveStream(
    channelId = userId,
    login = userLogin,
    name = userName,
    title = title,
    categoryId = categoryId,
    categoryName = category,
    viewers = viewerCount,
    thumbnailUrl = freshThumbnail(sizedImage(thumbnailUrl, 400, 225)),
    avatarUrl = avatars[userId],
    startedAtMs = parseTwitchTime(startedAt),
    tags = tags,
    language = language,
)

internal fun TwitchGame.toCategory() = Category(
    id = id,
    name = name,
    artUrl = sizedImage(boxArtUrl, 240, 320),
    group = categoryGroupOf(name),
)

private val IRL = setOf(
    "just chatting", "travel & outdoors", "sports", "fitness & health", "food & drink", "talk shows & podcasts",
    "pools, hot tubs, and beaches", "asmr", "irl", "special events", "politics", "science & technology",
)
private val MUSIC = setOf("music", "djs", "music & performing arts", "karaoke")
private val CREATIVE = setOf("art", "makers & crafting", "software and game development", "animals, aquariums, and zoos")

/** Heuristic by name: Twitch categories carry no group, and anything unknown is a game. */
internal fun categoryGroupOf(name: String): CategoryGroup {
    val key = name.trim().lowercase()
    return when {
        key in IRL -> CategoryGroup.Irl
        key in MUSIC -> CategoryGroup.Music
        key in CREATIVE -> CategoryGroup.Creative
        else -> CategoryGroup.Games
    }
}

/** The exact Helix shape, e.g. "2026-11-07T09:12:00Z". Offsets and fractions are not produced by Helix and are rejected. */
private val HelixTimestamp = Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z""")

/** Helix timestamps ("2026-11-07T09:12:00Z") in epoch milliseconds. 0 when absent, malformed or not in the exact shape. */
internal fun parseTwitchTime(value: String): Long = if (HelixTimestamp.matches(value)) {
    runCatching { Instant.parse(value).toEpochMilliseconds() }.getOrDefault(0L)
} else {
    0L
}

/** [millis] since the epoch as the UTC timestamp Helix filters take ("2026-11-12T08:30:00Z"); no platform calendar, so it works in common code. */
@Suppress("MagicNumber") // the constants of the civil-calendar algorithm, named in its source, not worth a name each
internal fun isoUtc(millis: Long): String {
    val totalSeconds = millis.floorDiv(1000L)
    val days = totalSeconds.floorDiv(86_400L)
    val secondOfDay = (totalSeconds - days * 86_400L).toInt()
    // Civil date from days since 1970-01-01 (Howard Hinnant's algorithm).
    val z = days + 719_468L
    val era = z.floorDiv(146_097L)
    val dayOfEra = (z - era * 146_097L).toInt()
    val yearOfEra = (dayOfEra - dayOfEra / 1_460 + dayOfEra / 36_524 - dayOfEra / 146_096) / 365
    val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
    val monthPart = (5 * dayOfYear + 2) / 153
    val day = dayOfYear - (153 * monthPart + 2) / 5 + 1
    val month = if (monthPart < 10) monthPart + 3 else monthPart - 9
    val year = (yearOfEra + era * 400 + if (month <= 2) 1 else 0).toInt()
    fun two(n: Int) = n.toString().padStart(2, '0')
    return "${year.toString().padStart(4, '0')}-${two(month)}-${two(day)}T${two(secondOfDay / 3_600)}:${two(secondOfDay % 3_600 / 60)}:${two(secondOfDay % 60)}Z"
}
