package com.twitchplayer.phone.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.twitchplayer.phone.R
import com.twitchplayer.phone.ui.theme.PhoneColors
import com.twitchplayer.tv.core.ui.formatViewers

/** The red LIVE chip: red is reserved for the live state, and the word is there too (never colour alone). */
@Composable
fun LiveBadge(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.live),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = PhoneColors.OnLive,
        modifier = modifier.clip(RoundedCornerShape(4.dp)).background(PhoneColors.Live).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** Viewer count on a dark pill, readable over any thumbnail. */
@Composable
fun ViewersBadge(count: Int, modifier: Modifier = Modifier) {
    val text = remember(count) { formatViewers(count) }
    Row(
        modifier.clip(RoundedCornerShape(4.dp)).background(PhoneColors.Scrim).padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(PhoneColors.Live))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = PhoneColors.OnScrim,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

/** A small dark label over a thumbnail (clip or replay length). */
@Composable
fun ThumbnailLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = PhoneColors.OnScrim,
        modifier = modifier.clip(RoundedCornerShape(4.dp)).background(PhoneColors.Scrim).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
