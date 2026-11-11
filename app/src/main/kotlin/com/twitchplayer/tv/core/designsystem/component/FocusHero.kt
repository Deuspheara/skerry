package com.twitchplayer.tv.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.twitchplayer.tv.core.designsystem.theme.TvColors
import com.twitchplayer.tv.core.ui.TvAsyncImage

/** A picture on the right 62 % of a header that melts into the page: flat gradients, no blur, no layer. */
@Composable
fun HeroBackdrop(imageUrl: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(0.62f).fillMaxHeight()) {
        TvAsyncImage(imageUrl, null, 600.dp, 240.dp, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to TvColors.Body, 0.55f to Color.Transparent)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to TvColors.Body)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to TvColors.Body, 0.35f to Color.Transparent)))
    }
}

/** Larger rendition (960x540) of a 400x225 stream thumbnail, for banners. */
fun heroImage(url: String): String = url.replace("-400x225", "-960x540")
