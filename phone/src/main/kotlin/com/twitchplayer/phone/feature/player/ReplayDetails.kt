package com.twitchplayer.phone.feature.player

import android.text.format.DateUtils
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twitchplayer.phone.R
import com.twitchplayer.phone.feature.channel.ReplayRow
import com.twitchplayer.phone.feature.channel.durationLabel
import com.twitchplayer.phone.navigation.PlaybackTarget
import com.twitchplayer.phone.ui.components.Avatar
import com.twitchplayer.phone.ui.components.FilterPill
import com.twitchplayer.phone.ui.components.SectionHeader
import com.twitchplayer.tv.core.data.model.ChannelDetails
import com.twitchplayer.tv.core.data.model.Replay
import com.twitchplayer.tv.core.platform.NumberFormatter
import com.twitchplayer.tv.core.presentation.Load
import com.twitchplayer.tv.core.ui.formatViewers
import com.twitchplayer.tv.feature.channel.presentation.ChannelViewModel
import com.twitchplayer.tv.feature.vod.ui.PLAYBACK_SPEEDS
import com.twitchplayer.tv.feature.vod.ui.speedLabel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.math.abs

/**
 * Under a replay's picture (portrait): the title, how long it is, how many watched it and when, the channel with a way into it, the
 * speed choices, and the channel's other replays, so the page is not a title and a void.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReplayDetails(
    info: ReplayInfo,
    durationMs: Long,
    speed: Float,
    speedsEnabled: Boolean,
    numbers: NumberFormatter,
    onSpeed: (Float) -> Unit,
    onOpenChannel: (String) -> Unit,
    onOpenReplay: (PlaybackTarget.Replay) -> Unit,
    modifier: Modifier = Modifier,
) {
    val now = remember { System.currentTimeMillis() }
    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(info.title, style = MaterialTheme.typography.titleMedium.copy(fontSize = TITLE_SP.sp, textDirection = androidx.compose.ui.text.style.TextDirection.Content), fontWeight = FontWeight.SemiBold)
        val meta = replayMeta(info, durationMs, now)
        if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (info.channelLogin.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Avatar(info.channelAvatarUrl, info.channelName.ifBlank { info.channelLogin }, 40.dp)
                Text(info.channelName.ifBlank { info.channelLogin }, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                FilledTonalButton(onClick = { onOpenChannel(info.channelLogin) }) { Text(stringResource(R.string.phone_player_open_channel)) }
            }
        }
        // A replay still being recorded is pinned to normal speed. The chips wrap onto a second line instead of running off the edge.
        if (speedsEnabled) {
            Text(stringResource(R.string.phone_player_speed), style = MaterialTheme.typography.labelLarge)
            // One row that scrolls if it must, never a lone "2x" on a second line.
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PLAYBACK_SPEEDS.forEach { candidate ->
                    FilterPill(speedLabel(candidate, numbers), selected = abs(speed - candidate) < SPEED_EPSILON, onClick = { onSpeed(candidate) })
                }
            }
        }
        if (info.channelLogin.isNotBlank()) MoreFromChannel(info, now, onOpenReplay)
        // The last row stops above the gesture bar.
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

/** The channel's other replays (the data the channel page already loads), under "More from <channel>". */
@Composable
private fun MoreFromChannel(info: ReplayInfo, now: Long, onOpenReplay: (PlaybackTarget.Replay) -> Unit) {
    val viewModel: ChannelViewModel = koinViewModel(key = "more-${info.channelLogin}") { parametersOf(info.channelLogin) }
    val state by viewModel.presenter.state.collectAsStateWithLifecycle()
    val details = (state.details as? Load.Ready<ChannelDetails>)?.data
    val others = remember(details, info.id) { details?.replays.orEmpty().filter { it.id != info.id }.take(MORE_REPLAYS) }
    if (others.isEmpty()) return
    SectionHeader(stringResource(R.string.phone_player_more_from, info.channelName.ifBlank { info.channelLogin }))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        others.forEach { replay ->
            ReplayRow(replay, progress = null, now = now, onClick = { onOpenReplay(replay.toTarget(info)) })
        }
    }
}

private fun Replay.toTarget(channel: ReplayInfo) = PlaybackTarget.Replay(id, title, durationSeconds, channel.channelLogin, channel.channelName, channel.channelAvatarUrl, views, publishedAtMs)

/** "3:12:40 · 3.6K views · Yesterday": what is known of the replay, skipping what is not. */
@Composable
private fun replayMeta(info: ReplayInfo, durationMs: Long, now: Long): String {
    val seconds = if (durationMs > 0) (durationMs / MILLIS).toInt() else info.durationSeconds
    val views = if (info.views > 0) stringResource(R.string.replay_views, formatViewers(info.views)) else ""
    val published = if (info.publishedAtMs > 0L) DateUtils.getRelativeTimeSpanString(info.publishedAtMs, now, DateUtils.MINUTE_IN_MILLIS).toString() else ""
    return listOf(if (seconds > 0) durationLabel(seconds) else "", views, published).filter { it.isNotBlank() }.joinToString(" · ")
}

private const val SPEED_EPSILON = 0.01f
private const val TITLE_SP = 18
private const val MORE_REPLAYS = 6
private const val MILLIS = 1_000L
