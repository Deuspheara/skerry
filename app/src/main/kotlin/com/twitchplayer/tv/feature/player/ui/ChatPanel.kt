package com.twitchplayer.tv.feature.player.ui

import android.app.ActivityManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.twitchplayer.tv.R
import com.twitchplayer.tv.core.designsystem.icon.TvIcons
import com.twitchplayer.tv.core.designsystem.theme.LocalMotion
import com.twitchplayer.tv.core.designsystem.theme.TvColors
import com.twitchplayer.tv.core.designsystem.theme.TvDimens
import com.twitchplayer.tv.core.designsystem.theme.TvType
import com.twitchplayer.tv.core.ui.TvFocusable
import com.twitchplayer.tv.feature.player.presentation.ChatLine
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * Chat column. Newest message at the bottom; while "live" the list follows new messages, otherwise it stays where
 * the viewer scrolled (D-pad, wheel or drag) and a pill counts what arrived since.
 *
 * The messages are not focusable one by one: the whole list is one focus target. Focus only gets here from the chat
 * header, never because messages arrive. While it has focus: Up / Down scroll the history (Up past the oldest message
 * goes back to the header), OK returns to live, Left gives focus back ([onLeave]).
 *
 * [overlay]: drawn straight over the video (no panel colour, lines fade out towards the top).
 * [active] false while chat is hidden: the panel stays composed with its position and messages, but animated emotes stop.
 * [bottomInset] keeps the newest line clear of the screen edge (overscan) and of the "back to live" pill.
 */
@Composable
fun ChatPanel(
    lines: ImmutableList<ChatLine>,
    style: ChatStyle,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onLeave: () -> Unit = {},
    overlay: Boolean = false,
    active: Boolean = true,
    bottomInset: Dp = 0.dp,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val motion = LocalMotion.current
    val reversed = remember(lines) { lines.asReversed() }

    // --- live / history ---------------------------------------------------------------------------
    var following by remember { mutableStateOf(true) }
    var lastSeenId by remember { mutableStateOf<String?>(null) }
    val newest = lines.lastOrNull()?.id
    LaunchedEffect(newest) {
        // A cleared list means another channel (or chat reopened): start live again, whatever the previous position was.
        if (newest == null) { following = true; lastSeenId = null }
        else if (following) {
            lastSeenId = newest
            // A new message at index 0 would otherwise scroll the viewer's position away from the bottom. A small arrival glides
            // up into place (the list keeps the old newest line anchored, so the new one enters from below); a big jump
            // (catching up after the chat was hidden) snaps.
            if (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0) {
                if (motion.enabled && listState.firstVisibleItemIndex <= 4) listState.animateScrollToItem(0)
                else listState.scrollToItem(0)
            }
        }
    }
    val unread by remember(lines) {
        derivedStateOf {
            if (following) 0 else lastSeenId?.let { seen -> lines.size - 1 - lines.indexOfLast { it.id == seen } }?.coerceAtLeast(0) ?: lines.size
        }
    }
    fun goLive() { following = true; scope.launch { listState.scrollToItem(0) } }
    // Pointer scrolling (wheel, drag) pauses following too, and coming back to the newest line resumes it. Only user
    // input reaches a nested-scroll connection: the list's own jumps to the newest message never count as scrolling away.
    val pointerScroll = remember(listState) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && consumed.y != 0f) {
                    following = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                }
                return Offset.Zero
            }
        }
    }

    fun scroll(older: Boolean, repeat: Boolean) {
        if (older && !listState.canScrollForward) return // nothing older: stay live
        val step = listState.layoutInfo.viewportSize.height * 0.4f
        // Reverse layout: positive deltas move towards older messages.
        val delta = if (older) step else -step
        if (older) following = false
        scope.launch {
            if (repeat || !motion.enabled) listState.scrollBy(delta) else listState.animateScrollBy(delta, motion.fast())
            if (!older && listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) following = true
        }
    }

    // --- animation budget ----------------------------------------------------------------------------
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val lowMemory = rememberLowMemory()
    val animationsOn = active && style.animatedEmotes && lifecycleState.isAtLeast(Lifecycle.State.STARTED) && !lowMemory
    var allowance by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    val currentLines by rememberUpdatedState(lines)
    LaunchedEffect(animationsOn, style.animatedLimit) {
        if (!animationsOn) { allowance = emptyMap(); return@LaunchedEffect }
        snapshotFlow {
            // Recomputed only when scrolling has settled: during a fling lines keep their stills and nothing flickers.
            if (listState.isScrollInProgress) null
            else listState.layoutInfo.visibleItemsInfo.map { it.key as String }.also { currentLines }
        }.filterNotNull().distinctUntilChanged().collect { keys ->
            val byId = currentLines.associateBy { it.id }
            allowance = animationAllowance(keys.mapNotNull(byId::get), style.animatedLimit)
        }
    }

    TvFocusable(
        onClick = { if (!following) goLive() },
        shape = RoundedCornerShape(2.dp),
        description = stringResource(R.string.chat_panel),
        focusScale = 1f,
        modifier = modifier
            .focusRequester(focusRequester)
            .onKeyEvent { event ->
                when {
                    event.type != KeyEventType.KeyDown -> false
                    // Nothing older: let focus move up to the chat header.
                    event.key == Key.DirectionUp -> if (!listState.canScrollForward) false else { scroll(older = true, repeat = event.nativeKeyEvent.repeatCount > 0); true }
                    event.key == Key.DirectionDown -> { scroll(older = false, repeat = event.nativeKeyEvent.repeatCount > 0); true }
                    event.key == Key.DirectionLeft -> { onLeave(); true }
                    event.key == Key.DirectionRight -> true
                    else -> false
                }
            },
    ) { focus ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(pointerScroll)
                .then(
                    if (overlay) Modifier.overlayFade() else Modifier.drawWithContent {
                        drawContent()
                        // Flat fade at the top edge: a gradient over the opaque panel, not an offscreen layer.
                        drawRect(Brush.verticalGradient(0f to TvColors.Base, 1f to Color.Transparent, endY = 28.dp.toPx()), size = Size(size.width, 28.dp.toPx()))
                    },
                ),
            reverseLayout = true,
            // Inside the scroll container (not around it): the newest line rests above the inset and scrolls through it.
            contentPadding = PaddingValues(bottom = bottomInset),
            verticalArrangement = Arrangement.spacedBy(style.gapDp.dp, Alignment.Bottom),
        ) {
            items(reversed, key = { it.id }, contentType = { it.kind }) { line ->
                val animated = remember(line.id) { derivedStateOf { allowance[line.id] ?: 0 } }
                ChatLineView(
                    line, style, animated,
                    // New lines fade in as they rise; no placement animation (the scroll glide moves them) and no exit one.
                    modifier = if (motion.enabled) Modifier.animateItem(fadeInSpec = tween(motion.mediumMs), placementSpec = null, fadeOutSpec = null) else Modifier,
                )
            }
        }
        LivePill(
            visible = !following,
            unread = unread,
            focus = { focus.value },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = maxOf(6.dp, bottomInset)),
        )
    }
}

/**
 * Transparent chat over the video: the lines fade out towards the top of the panel (the middle of the screen).
 * A real alpha mask needs an offscreen layer, which stays cheap because the overlay panel is a small corner of the screen.
 */
private fun Modifier.overlayFade(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            // Many soft stops (roughly ease-in) so the fade is diffuse, with no visible edge.
            Brush.verticalGradient(
                0f to Color.Transparent,
                0.15f to Color.Black.copy(alpha = 0.04f),
                0.35f to Color.Black.copy(alpha = 0.25f),
                0.6f to Color.Black.copy(alpha = 0.65f),
                0.85f to Color.Black,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

/** "Back to live" chip with the number of messages that arrived while browsing; purple while the panel has focus (OK acts on it). */
@Composable
private fun LivePill(visible: Boolean, unread: Int, focus: () -> Float, modifier: Modifier) {
    val motion = LocalMotion.current
    val label = if (unread > 0) pluralStringResource(R.plurals.chat_new_messages, unread, unread) else stringResource(R.string.chat_back_to_live)
    AnimatedVisibility(visible, modifier, enter = fadeIn(motion.fast()), exit = fadeOut(motion.exit())) {
        Row(
            Modifier
                .height(28.dp)
                .background(TvColors.Black85, CircleShape)
                .drawBehind { drawRoundRect(TvColors.Purple, cornerRadius = CornerRadius(size.height / 2f), alpha = focus()) }
                .padding(horizontal = 10.dp)
                .semantics { contentDescription = label },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(TvIcons.ArrowDown, null, tint = TvColors.Text, modifier = Modifier.size(16.dp))
            if (unread > 0) Text(if (unread > 99) "99+" else unread.toString(), style = TvType.Label, color = TvColors.Text)
        }
    }
}

/**
 * True once the system reports memory pressure while chat is open: animations stop for good, stills remain.
 * The platform's callbacks for this are deprecated (the foreground trim levels since API 35, onLowMemory since 34), so
 * this polls the system's own "low memory" flag every 15 s; Coil trims its image caches on its own trim callbacks.
 */
@Composable
private fun rememberLowMemory(): Boolean {
    val context = LocalContext.current
    var low by remember { mutableStateOf(false) }
    LaunchedEffect(context) {
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val info = ActivityManager.MemoryInfo()
        while (!low) {
            activityManager.getMemoryInfo(info)
            if (info.lowMemory) low = true else delay(15_000)
        }
    }
    return low
}
