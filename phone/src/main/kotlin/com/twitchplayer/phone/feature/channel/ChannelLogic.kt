package com.twitchplayer.phone.feature.channel

import com.twitchplayer.tv.core.data.model.ChannelDetails
import com.twitchplayer.tv.core.data.model.Replay
import com.twitchplayer.tv.core.ui.formatUptime
import com.twitchplayer.tv.feature.channel.presentation.ReplayTail

/** The channel's first page of replays followed by the pages loaded since, without repeats (a page boundary can overlap). */
fun allReplays(details: ChannelDetails, tail: ReplayTail): List<Replay> = (details.replays + tail.replays).distinctBy { it.id }

/** True while a further page exists: the cursor of the last page loaded, or the first page's cursor before any was requested. */
fun hasMoreReplays(details: ChannelDetails, tail: ReplayTail): Boolean = if (tail.started) tail.cursor != null else details.replaysCursor != null

/** "1:23:45" / "2:05" for a clip or replay length in seconds. */
fun durationLabel(seconds: Int): String = formatUptime(seconds.coerceAtLeast(0) * MILLIS_PER_SECOND)

/** How long a live stream has been on air, "1:23:45"; null when the start is unknown or in the future. */
fun liveForLabel(startedAtMs: Long, nowMs: Long): String? = if (startedAtMs <= 0L || nowMs < startedAtMs) null else formatUptime(nowMs - startedAtMs)

private const val MILLIS_PER_SECOND = 1_000L
