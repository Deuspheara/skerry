package com.twitchplayer.phone.feature.following

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.twitchplayer.phone.R
import com.twitchplayer.phone.navigation.PlaybackTarget
import com.twitchplayer.phone.ui.components.RemoteImage
import com.twitchplayer.phone.ui.components.THUMBNAIL_RATIO
import com.twitchplayer.phone.ui.components.clickableItem
import com.twitchplayer.tv.feature.follows.presentation.ContinueWatchingItem
import kotlin.math.roundToInt

/**
 * The replays the viewer started: a row of thumbnails with a bar showing how far they got, the title and the channel. A tap resumes
 * where they stopped (the replay screen reads the saved position itself).
 */
@Composable
fun ContinueWatchingRow(items: List<ContinueWatchingItem>, onReplay: (PlaybackTarget.Replay) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 16.dp)) {
        items(items, key = { it.replay.replay.id }) { item ->
            ContinueCard(item, onClick = { onReplay(item.toTarget()) })
        }
    }
}

@Composable
private fun ContinueCard(item: ContinueWatchingItem, onClick: () -> Unit) {
    val replay = item.replay.replay
    val percent = (item.progress.fraction * PERCENT).roundToInt()
    val description = stringResource(R.string.phone_following_resume_description, replay.title, item.replay.channelName, percent)
    Column(
        Modifier.width(CARD_WIDTH).clip(RoundedCornerShape(12.dp)).clickableItem(onClick).semantics(mergeDescendants = true) { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(THUMBNAIL_RATIO).clip(RoundedCornerShape(12.dp))) {
            RemoteImage(replay.thumbnailUrl, Modifier.matchParentSize())
            // How much has been watched: a thin bar along the bottom edge.
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(BAR_HEIGHT).background(Color.Black.copy(alpha = TRACK_ALPHA))) {
                Box(Modifier.fillMaxWidth(item.progress.fraction.coerceIn(0f, 1f)).height(BAR_HEIGHT).background(MaterialTheme.colorScheme.primary))
            }
        }
        Text(replay.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 2.dp))
        Text(
            item.replay.channelName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}

private fun ContinueWatchingItem.toTarget() = PlaybackTarget.Replay(
    id = replay.replay.id,
    title = replay.replay.title,
    durationSeconds = replay.replay.durationSeconds,
    channelLogin = replay.channelLogin,
    channelName = replay.channelName,
    channelAvatarUrl = replay.channelAvatarUrl,
    views = replay.replay.views,
    publishedAtMs = replay.replay.publishedAtMs,
)

private val CARD_WIDTH = 220.dp
private val BAR_HEIGHT = 3.dp
private const val TRACK_ALPHA = 0.5f
private const val PERCENT = 100
