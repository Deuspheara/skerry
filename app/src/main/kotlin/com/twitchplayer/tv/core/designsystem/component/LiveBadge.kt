package com.twitchplayer.tv.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.twitchplayer.tv.R
import com.twitchplayer.tv.core.designsystem.theme.TvColors
import com.twitchplayer.tv.core.designsystem.theme.TvType

/** Red LIVE tag: the only place #EB0400 is used, always paired with the word. [large] is the 20 dp channel-page variant. */
@Composable
fun LiveBadge(modifier: Modifier = Modifier, large: Boolean = false) {
    Box(
        modifier
            .then(if (large) Modifier.height(20.dp) else Modifier)
            .background(TvColors.Live, RoundedCornerShape(2.dp))
            .padding(horizontal = if (large) 7.dp else 5.dp, vertical = if (large) 0.dp else 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = stringResource(R.string.live), color = TvColors.Text, style = TvType.LiveBadge)
    }
}
