package com.twitchplayer.phone.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.twitchplayer.phone.R
import com.twitchplayer.phone.ui.components.PhoneIcons
import com.twitchplayer.phone.ui.theme.PhoneColors
import com.twitchplayer.tv.core.ui.formatUptime
import com.twitchplayer.tv.feature.vod.ui.SEEK_STEP_MS

/** The middle of a replay or clip: back 10 s, play / pause (or replay at the end), forward 10 s. */
@Composable
fun RecordedCenter(
    snapshot: PlayerSnapshot,
    onTogglePlay: () -> Unit,
    onReplay: () -> Unit,
    onSkip: (Long) -> Unit,
    chrome: PlayerChrome,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        SkipButton(forward = false) {
            chrome.controls.touch()
            onSkip(-SEEK_STEP_MS)
        }
        if (snapshot.ended) {
            IconButton(onClick = onReplay, modifier = Modifier.size(CENTER_SIZE)) {
                Icon(PhoneIcons.Replay, contentDescription = stringResource(R.string.clip_replay), tint = PhoneColors.OnScrim, modifier = Modifier.size(CENTER_SIZE / 2))
            }
        } else {
            CenterPlayButton(playing = snapshot.playWhenReady, buffering = snapshot.buffering, onClick = onTogglePlay)
        }
        SkipButton(forward = true) {
            chrome.controls.touch()
            onSkip(SEEK_STEP_MS)
        }
    }
}

@Composable
private fun SkipButton(forward: Boolean, onClick: () -> Unit) {
    val description = stringResource(if (forward) R.string.phone_player_forward else R.string.phone_player_back10)
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp).semantics { contentDescription = description }) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                PhoneIcons.Replay,
                contentDescription = null,
                tint = PhoneColors.OnScrim,
                modifier = Modifier.size(32.dp).graphicsLayer { scaleX = if (forward) -1f else 1f },
            )
            Text("10", style = MaterialTheme.typography.labelSmall, color = PhoneColors.OnScrim, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** The seek bar with the elapsed and total time. [onDragging] pins the control layer while the thumb is held; [onSeek] gets the target. */
@Composable
fun SeekRow(
    snapshot: PlayerSnapshot,
    onSeek: (Long) -> Unit,
    onDragging: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    /** The length to use when the player does not know it yet (the replay's length from the channel page). */
    durationMs: Long = snapshot.durationMs,
) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val duration = durationMs
    val fraction = drag ?: SeekModel.fractionOf(snapshot.positionMs, duration)
    val shownMs = if (drag != null) SeekModel.positionFor(drag ?: 0f, duration) else snapshot.positionMs
    val time = formatUptime(shownMs)
    val total = formatUptime(duration)
    Row(modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(time, style = MaterialTheme.typography.labelMedium, color = PhoneColors.OnScrim)
        Slider(
            value = fraction,
            onValueChange = {
                drag = it
                onDragging(true)
            },
            onValueChangeFinished = {
                drag?.let { onSeek(SeekModel.positionFor(it, duration)) }
                drag = null
                onDragging(false)
            },
            enabled = duration > 0,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = PhoneColors.OnScrim.copy(alpha = TRACK_ALPHA),
            ),
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp).semantics { stateDescription = "$time / $total" },
        )
        Text(total, style = MaterialTheme.typography.labelMedium, color = PhoneColors.OnScrim)
    }
}

/** Replay start choice shown over the picture when a saved position is worth resuming. */
@Composable
fun ResumeOffer(savedMs: Long, onResume: () -> Unit, onStartOver: () -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = OFFER_ALPHA), shape = MaterialTheme.shapes.large, modifier = modifier) {
        androidx.compose.foundation.layout.Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.material3.Button(onClick = onResume) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.padding(end = 6.dp).size(18.dp))
                Text(stringResource(R.string.vod_resume, formatUptime(savedMs)))
            }
            androidx.compose.material3.OutlinedButton(onClick = onStartOver) { Text(stringResource(R.string.vod_start_over)) }
        }
    }
}

private val CENTER_SIZE = 64.dp
private const val TRACK_ALPHA = 0.35f
private const val OFFER_ALPHA = 0.95f
