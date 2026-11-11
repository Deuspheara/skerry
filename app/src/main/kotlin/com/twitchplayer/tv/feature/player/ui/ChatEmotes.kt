package com.twitchplayer.tv.feature.player.ui

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.decode.Decoder
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.size.Size
import com.twitchplayer.tv.feature.player.data.emotes.Emote
import java.util.concurrent.ConcurrentHashMap

/**
 * Animated emotes are decoded by Coil's own animated decoder, requested per image: the app-wide loader stays still-only,
 * so only an emote that was granted animation budget ever pays for frame decoding.
 */
private val animatedDecoder: Decoder.Factory =
    if (Build.VERSION.SDK_INT >= 28) AnimatedImageDecoder.Factory() else GifDecoder.Factory()

/**
 * Width / height of emote artwork learned from the first successful load. Providers such as BTTV do not publish
 * dimensions, so the first line to show an emote may letterbox it; every later line reserves the right width.
 */
internal object EmoteAspects {
    private val learned = ConcurrentHashMap<String, Float>()
    operator fun get(url: String): Float? = learned[url]
    fun learn(url: String, width: Int, height: Int) {
        if (width > 0 && height > 0) learned.putIfAbsent(url, width.toFloat() / height)
    }
}

/**
 * One emote image in a box of [widthPx] x [heightPx]. The still image is always there (small, shared through
 * Coil's memory cache). While [animate] is true the animated variant is loaded on top and replaces the still
 * once it is ready; when the budget is taken away it simply disappears and the still is already underneath.
 * (A dedicated render node per animated layer was tried and measured: no difference in frame time, so there is none.)
 */
@Composable
internal fun EmoteLayer(emote: Emote, animate: Boolean, widthPx: Int, heightPx: Int, modifier: Modifier = Modifier.fillMaxSize()) {
    val context = LocalContext.current
    val animating = animate && emote.animatedUrl != null
    var animatedReady by remember(emote.animatedUrl, animating) { mutableStateOf(false) }
    Box(modifier) {
        if (!(animating && animatedReady)) {
            val still = remember(emote.url, widthPx, heightPx) {
                ImageRequest.Builder(context).data(emote.url).size(Size(widthPx, heightPx)).build()
            }
            AsyncImage(
                model = still, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(),
                onSuccess = { EmoteAspects.learn(emote.url, it.result.image.width, it.result.image.height) },
            )
        }
        if (animating) {
            val moving = remember(emote.animatedUrl, widthPx, heightPx) {
                ImageRequest.Builder(context).data(emote.animatedUrl).size(Size(widthPx, heightPx))
                    .decoderFactory(animatedDecoder)
                    // A decoded animation is stateful (it owns a playback clock): never share it through the memory cache.
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .build()
            }
            AsyncImage(
                model = moving, contentDescription = null, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                onSuccess = { animatedReady = true },
            )
        }
    }
}
