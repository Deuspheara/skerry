package com.twitchplayer.tv.feature.clip.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.twitchplayer.tv.R
import com.twitchplayer.tv.core.designsystem.component.EmptyState
import com.twitchplayer.tv.core.designsystem.component.TvButton
import com.twitchplayer.tv.core.designsystem.component.TvSpinner
import com.twitchplayer.tv.core.designsystem.icon.TvIcons
import com.twitchplayer.tv.core.designsystem.theme.TvColors
import com.twitchplayer.tv.core.designsystem.theme.TvDimens
import com.twitchplayer.tv.core.designsystem.theme.TvShapes
import com.twitchplayer.tv.core.designsystem.theme.TvType
import com.twitchplayer.tv.feature.player.data.stream.ClipResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

private sealed interface ClipLoad {
    data object Loading : ClipLoad
    data class Ready(val url: String) : ClipLoad
    data object Failed : ClipLoad
}

private const val SEEK_MS = 10_000L
private const val SKIP_BUBBLE_MS = 800L

/** One press skips 10 s; holding the key skips further at each repeat (10, 10, ..., then 20, 30, up to 60 s). */
private fun seekStep(repeatCount: Int): Long = SEEK_MS * (1 + (repeatCount / 4).coerceAtMost(5))
private const val CHROME_MS = 3_500L

/**
 * Plays one clip full screen: OK pauses or resumes, Left / Right jump 10 s, Back returns to the channel. The title and a
 * thin progress line show for a few seconds after each key. Its own ExoPlayer, released with the screen.
 */
@OptIn(UnstableApi::class)
@Composable
fun ClipRoute(slug: String, title: String, onBack: () -> Unit, resolver: ClipResolver = koinInject()) {
    var load by remember(slug) { mutableStateOf<ClipLoad>(ClipLoad.Loading) }
    var attempt by remember(slug) { mutableStateOf(0) }
    LaunchedEffect(slug, attempt) {
        load = try {
            // Highest quality first; a TV decodes 1080p H.264 without trouble.
            ClipLoad.Ready(resolver.resolve(slug).first().url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ClipLoad.Failed
        }
    }
    BackHandler(onBack = onBack)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (val state = load) {
            ClipLoad.Loading -> Box(Modifier.align(Alignment.Center)) { TvSpinner() }

            ClipLoad.Failed -> {
                val retry = remember { androidx.compose.ui.focus.FocusRequester() }
                LaunchedEffect(Unit) { runCatching { retry.requestFocus() } }
                EmptyState(
                    TvIcons.OffAir,
                    stringResource(R.string.clip_unavailable),
                    Modifier.align(Alignment.Center),
                    line = stringResource(R.string.clip_unavailable_line),
                    action = {
                        TvButton(stringResource(R.string.state_retry), TvIcons.Replay, onClick = {
                            load = ClipLoad.Loading
                            attempt++
                        }, modifier = Modifier.focusRequester(retry), primary = true)
                    },
                )
            }

            is ClipLoad.Ready -> ClipPlayback(state.url, title, onFailed = { load = ClipLoad.Failed })
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun ClipPlayback(url: String, title: String, onFailed: () -> Unit) {
    val context = LocalContext.current
    val player = remember(url) { ExoPlayer.Builder(context).build() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var playing by remember { mutableStateOf(true) }
    var ended by remember { mutableStateOf(false) }
    com.twitchplayer.tv.core.ui.KeepScreenOn(playing)
    DisposableEffect(player, lifecycle) {
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) = onFailed()
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }
            override fun onPlaybackStateChanged(state: Int) {
                ended = state == Player.STATE_ENDED
            }
        })
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.playWhenReady = true
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) player.pause() }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            player.release()
        }
    }

    val motion = com.twitchplayer.tv.core.designsystem.theme.LocalMotion.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    var chromeUntil by remember { mutableStateOf(System.currentTimeMillis() + CHROME_MS) }
    var recent by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    // The bar stays while the clip is paused or finished: the viewer is then looking at it to decide what to do.
    val chromeVisible = recent || !playing
    LaunchedEffect(chromeUntil, chromeVisible) {
        recent = true
        while (System.currentTimeMillis() < chromeUntil || !playing) {
            position = player.currentPosition.coerceAtLeast(0L)
            duration = player.duration.takeIf { it > 0 } ?: 0L
            delay(250)
        }
        recent = false
    }

    // The last skip, shown as a bubble ("+0:10") that stays for a moment; holding the key skips further each repeat.
    var skipMs by remember { mutableLongStateOf(0L) }
    var skipAt by remember { mutableLongStateOf(0L) }
    var skipVisible by remember { mutableStateOf(false) }
    LaunchedEffect(skipAt) {
        if (skipAt == 0L) return@LaunchedEffect
        skipVisible = true
        delay(SKIP_BUBBLE_MS)
        skipVisible = false
    }
    fun seekBy(deltaMs: Long) {
        val end = maxOf(player.duration, 0L)
        val target = (player.currentPosition + deltaMs).coerceIn(0L, end)
        skipMs = if (skipVisible && (skipMs > 0) == (deltaMs > 0)) skipMs + deltaMs else deltaMs
        skipAt = System.currentTimeMillis()
        player.seekTo(target)
        position = target
    }
    Box(
        Modifier.fillMaxSize().focusRequester(focus)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val handled = when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Spacebar, Key.MediaPlayPause -> {
                        // A finished clip starts again.
                        if (player.playbackState == Player.STATE_ENDED) {
                            player.seekTo(0)
                            player.playWhenReady = true
                        } else {
                            player.playWhenReady = !player.isPlaying
                        }
                        true
                    }

                    Key.DirectionRight, Key.MediaFastForward -> {
                        seekBy(seekStep(event.nativeKeyEvent.repeatCount))
                        true
                    }

                    Key.DirectionLeft, Key.MediaRewind -> {
                        seekBy(-seekStep(event.nativeKeyEvent.repeatCount))
                        true
                    }

                    else -> false
                }
                chromeUntil = System.currentTimeMillis() + CHROME_MS
                handled
            }
            .focusable(),
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    isFocusable = false
                    descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    this.player = player
                }
            },
            modifier = Modifier.fillMaxSize(),
            onRelease = { it.player = null },
        )
        // Paused or finished: a large glyph in the middle says so (and what OK will do).
        AnimatedVisibility(
            !playing,
            Modifier.align(Alignment.Center),
            // A small settle (1.2 -> 1) so the pause is felt, not just shown.
            enter = fadeIn(motion.fast()) + androidx.compose.animation.scaleIn(motion.fast(), initialScale = 1.2f),
            exit = fadeOut(motion.exit()),
        ) {
            Box(Modifier.size(80.dp).background(TvColors.Black60, CircleShape), contentAlignment = Alignment.Center) {
                Icon(if (ended) TvIcons.Replay else TvIcons.Play, stringResource(if (ended) R.string.clip_replay else R.string.player_play), tint = TvColors.Text, modifier = Modifier.size(40.dp))
            }
        }
        // Skip feedback: where the playhead went, so a key press is never silent.
        AnimatedVisibility(
            skipVisible,
            Modifier.align(Alignment.Center),
            enter = fadeIn(motion.fast()),
            exit = fadeOut(motion.exit()),
        ) {
            Text(
                (if (skipMs >= 0) "+" else "-") + clipTime(kotlin.math.abs(skipMs)),
                style = TvType.Headline,
                color = TvColors.Text,
                modifier = Modifier.background(TvColors.Black60, TvShapes.Chip).padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }
        AnimatedVisibility(chromeVisible, Modifier.align(Alignment.BottomStart).fillMaxWidth(), enter = fadeIn(motion.medium()), exit = fadeOut(motion.exit())) {
            Column(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(0f to Color.Transparent, 0.45f to TvColors.Black60, 1f to TvColors.Black85))
                    .padding(start = TvDimens.SafeHorizontal, end = TvDimens.SafeHorizontal, top = 64.dp, bottom = TvDimens.SafeBottom),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(title, style = TvType.PlayerName.copy(shadow = Shadow(TvColors.Black80, Offset(0f, 1f), 3f)), color = TvColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(clipTime(position) + " / " + clipTime(duration), style = TvType.MetaNumeric, color = TvColors.TextSecondary, maxLines = 1)
                }
                ProgressLine(if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f)
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    // "10 s" in the app language's own unit (ICU), the key glyph alone for play/pause (the icon says what OK does).
                    val step = androidx.compose.runtime.remember {
                        android.icu.text.MeasureFormat.getInstance(java.util.Locale.getDefault(), android.icu.text.MeasureFormat.FormatWidth.NARROW)
                            .format(android.icu.util.Measure(10, android.icu.util.MeasureUnit.SECOND))
                    }
                    Hint(TvIcons.ChevronLeft, TvIcons.ChevronRight, step)
                    Hint(if (playing) TvIcons.Pause else TvIcons.Play, null, null)
                }
            }
        }
    }
}

/** Track, purple fill and a handle at the playhead. Drawn, not a widget: the remote seeks in 10 s steps. */
@Composable
internal fun ProgressLine(fraction: Float) {
    Box(
        Modifier.fillMaxWidth().height(12.dp).drawBehind {
            val h = 4.dp.toPx()
            val y = (size.height - h) / 2f
            // Time runs from the start edge: right to left in right-to-left languages.
            val rtl = layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl
            val filled = size.width * fraction
            drawRoundRect(Color.White.copy(alpha = 0.24f), topLeft = Offset(0f, y), size = Size(size.width, h), cornerRadius = CornerRadius(h / 2f))
            drawRoundRect(TvColors.Purple, topLeft = Offset(if (rtl) size.width - filled else 0f, y), size = Size(filled, h), cornerRadius = CornerRadius(h / 2f))
            val edge = if (rtl) size.width - filled else filled
            drawCircle(TvColors.Text, radius = 6.dp.toPx(), center = Offset(edge.coerceIn(6.dp.toPx(), size.width - 6.dp.toPx()), size.height / 2f))
        },
    )
}

/** A key hint: one or two glyphs and a short label (no sentence). */
@Composable
internal fun Hint(first: ImageVector, second: ImageVector?, label: String?) {
    // Arrows name physical keys: they never mirror in right-to-left languages.
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(first, null, tint = TvColors.TextSecondary, modifier = Modifier.size(20.dp))
            if (second != null) Icon(second, null, tint = TvColors.TextSecondary, modifier = Modifier.size(20.dp))
            if (label != null) Text(label, style = TvType.Pill, color = TvColors.TextSecondary, maxLines = 1)
        }
    }
}

internal fun clipTime(ms: Long): String {
    val total = (ms / 1000).toInt()
    return "%d:%02d".format(total / 60, total % 60)
}
