package com.twitchplayer.tv.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.twitchplayer.tv.core.designsystem.theme.interFamily
import com.twitchplayer.tv.core.designsystem.theme.TvColors
import com.twitchplayer.tv.core.ui.TvAsyncImage

/** Round channel avatar: remote picture when known, otherwise the purple initial. Decorative. */
@Composable
fun Avatar(
    name: String,
    imageUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    background: androidx.compose.ui.graphics.Color = TvColors.Purple,
    border: androidx.compose.ui.graphics.Color? = null,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        if (imageUrl.isNullOrEmpty()) {
            Text(
                text = name.firstOrNull()?.uppercase().orEmpty(),
                color = TvColors.Text,
                style = TextStyle(fontFamily = interFamily(600, size.value * 0.44f), fontWeight = FontWeight.SemiBold, fontSize = size.value.sp * 0.44f),
            )
        } else {
            TvAsyncImage(imageUrl, null, size, size, Modifier.size(size))
        }
    }
}
