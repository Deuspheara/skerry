package com.twitchplayer.tv.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size
import com.twitchplayer.tv.core.designsystem.theme.LocalMotion
import com.twitchplayer.tv.core.designsystem.theme.TvColors

/**
 * Remote image decoded at the target size (never full resolution), drawn over a stable [TvColors.Base]
 * placeholder so the layout never jumps. Short crossfade, disabled with reduced motion.
 */
@Composable
fun TvAsyncImage(
    url: String?,
    contentDescription: String?,
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val crossfadeMs = LocalMotion.current.fastMs
    // The last image shown here: when the URL changes (a fresh thumbnail of the same stream), it stays as the
    // placeholder and the new one crossfades over it instead of flashing the empty background.
    val shown = remember { arrayOfNulls<MemoryCache.Key>(1) }
    val request = remember(url, width, height, crossfadeMs) {
        val w = with(density) { width.roundToPx() }
        val h = with(density) { height.roundToPx() }
        ImageRequest.Builder(context)
            .data(url)
            .size(Size(w, h))
            .placeholderMemoryCacheKey(shown[0])
            .crossfade(crossfadeMs)
            .build()
    }
    Box(modifier.background(TvColors.Base)) {
        if (!url.isNullOrEmpty()) {
            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                contentScale = contentScale,
                onSuccess = { shown[0] = it.result.memoryCacheKey },
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}
