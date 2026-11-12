package com.twitchplayer.phone.feature.player

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.TargetedFlingBehavior
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.twitchplayer.phone.navigation.PlaybackTarget
import com.twitchplayer.phone.ui.theme.LocalReducedMotion
import kotlin.math.roundToInt

/** Where the sheet can rest: the whole screen, the strip above the tab bar, or off the bottom (closed). */
private enum class Rest { Expanded, Collapsed, Dismissed }

private const val SETTLE_MS = 260
private const val POSITIONAL_THRESHOLD = 0.4f

/**
 * The player, hosted above the tabs and their pages instead of being one of them, so leaving a page never ends it. Expanded it covers
 * the screen; dragged down (or minimised with the chevron or Back) it becomes a strip above the tab bar while the app is used under it.
 *
 * The page itself ([LivePlayerRoute], [ReplayRoute], [ClipRoute]) stays composed the whole time and only changes size, so the picture
 * keeps its view and its playback. [bottomChrome] is the tab bar's height when it is showing (the navigation-bar inset is added here),
 * [startChrome] the rail's width when the tabs are a rail.
 */
@Composable
fun PlayerOverlay(
    session: PlayerSessionController,
    bottomChrome: Dp,
    startChrome: Dp,
    onOpenChannel: (String) -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val open = session.state as? PlayerSessionState.Open ?: return
    val host = LocalPlayerHost.current
    val reducedMotion = LocalReducedMotion.current
    val density = LocalDensity.current
    val dispatch = session::dispatch

    val inPip by host.inPictureInPicture.collectAsStateWithLifecycle()
    DisposableEffect(inPip) {
        dispatch(PlayerSessionEvent.PipChanged(inPip))
        onDispose { }
    }
    // Fullscreen is the picture turned sideways from the expanded sheet; the strip stays a strip whichever way the phone is held.
    val sideways = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val fullscreen = sideways && open.sheet == SheetState.Expanded && !open.inPip
    DisposableEffect(fullscreen) {
        dispatch(PlayerSessionEvent.FullscreenChanged(fullscreen))
        onDispose { }
    }

    val anchored = remember { AnchoredDraggableState(if (open.sheet == SheetState.Collapsed) Rest.Collapsed else Rest.Expanded) }
    val metrics = remember { SheetMetrics() }

    // The session decides where the sheet rests (a tap, Back, the chevron, a new stream); the drag decides too, and tells the session.
    val wanted = if (open.sheet == SheetState.Collapsed) Rest.Collapsed else Rest.Expanded
    LaunchedEffect(wanted) {
        if (anchored.settledValue != wanted || anchored.targetValue != wanted) {
            if (reducedMotion || anchored.offset.isNaN()) anchored.snapTo(wanted) else anchored.animateTo(wanted, tween(SETTLE_MS))
        }
    }
    LaunchedEffect(anchored) {
        snapshotFlow { anchored.settledValue }.collect { rest ->
            dispatch(
                when (rest) {
                    Rest.Expanded -> PlayerSessionEvent.Expand
                    Rest.Collapsed -> PlayerSessionEvent.Minimise
                    Rest.Dismissed -> PlayerSessionEvent.Close
                },
            )
        }
    }

    val stripState = rememberUpdatedState(open.showsStrip)
    val bodyVisible = remember(anchored) { derivedStateOf { anchored.settledValue != Rest.Collapsed || anchored.targetValue != Rest.Collapsed } }
    val stripVisible = remember(anchored) { derivedStateOf { anchored.settledValue != Rest.Expanded || anchored.targetValue != Rest.Expanded } }
    val sheet = remember(anchored, metrics) {
        PlayerSheet(
            progress = { SheetGeometry.progress(anchored.offset.let { if (it.isNaN()) 0f else it }, metrics.collapsedPx) },
            stripState = stripState,
            bodyState = bodyVisible,
            stripVisibleState = stripVisible,
            minimise = { dispatch(PlayerSessionEvent.Minimise) },
            expand = { dispatch(PlayerSessionEvent.Expand) },
            close = { dispatch(PlayerSessionEvent.Close) },
        )
    }

    // Back minimises the expanded sheet; the strip (and the window) leave Back to the navigation; it never closes the player. Leaving
    // fullscreen is the page's own handler (it also gives the screen its orientation back), which wins while the page is composed. Before
    // a replay or clip has loaded there is no page to ask, so the sheet turns the screen upright itself and lets go of the lock once it is.
    val back = BackPolicy.action(open)
    var uprightRequested by remember { mutableStateOf(false) }
    BackHandler(enabled = back != BackAction.PassThrough) {
        when (back) {
            BackAction.Minimise -> dispatch(PlayerSessionEvent.Minimise)

            BackAction.ExitFullscreen -> {
                uprightRequested = true
                host.lockOrientation(OrientationLock.Portrait)
            }

            BackAction.PassThrough -> Unit
        }
    }
    DisposableEffect(uprightRequested, fullscreen) {
        if (uprightRequested && !fullscreen) {
            host.lockOrientation(OrientationLock.None)
            uprightRequested = false
        }
        onDispose { }
    }

    val navigationBarPx = WindowInsets.navigationBars.getBottom(density)
    val bottomChromePx = with(density) { bottomChrome.roundToPx() } + navigationBarPx
    val startChromePx = with(density) { startChrome.roundToPx() }
    val stripHeight = rememberStripHeight()
    val stripHeightPx = with(density) { stripHeight.roundToPx() }
    val spec: AnimationSpec<Float> = if (reducedMotion) snap() else tween(SETTLE_MS)
    val containerHeightPx = remember { floatArrayOf(0f) }
    val threshold = remember { { distance: Float -> DismissPolicy.positionalThreshold(distance, containerHeightPx[0], POSITIONAL_THRESHOLD) } }
    val fling = rememberDeliberateFling(anchored, spec, density.density, threshold)

    val owner = rememberTargetStoreOwner(open.target)
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner, LocalPlayerSheet provides sheet) {
        Box(modifier.fillMaxSize()) {
            Box(
                Modifier
                    .layout { measurable, constraints ->
                        val maxWidth = constraints.maxWidth
                        val maxHeight = constraints.maxHeight
                        val collapsed = SheetGeometry.collapsedOffset(maxHeight, bottomChromePx, stripHeightPx)
                        metrics.collapsedPx = collapsed.toFloat()
                        containerHeightPx[0] = maxHeight.toFloat()
                        // Only the strip can be swiped away: a hard fling of the expanded sheet ends on the strip, it never closes the player.
                        val canDismiss = anchored.settledValue != Rest.Expanded
                        anchored.updateAnchors(
                            DraggableAnchors {
                                Rest.Expanded at 0f
                                Rest.Collapsed at collapsed.toFloat()
                                if (canDismiss) Rest.Dismissed at maxHeight.toFloat()
                            },
                        )
                        val offset = anchored.requireOffset()
                        val progress = SheetGeometry.progress(offset, metrics.collapsedPx)
                        val x = (startChromePx * progress).roundToInt()
                        val height = SheetGeometry.sheetHeight(maxHeight, stripHeightPx, progress)
                        val placeable = measurable.measure(Constraints.fixed((maxWidth - x).coerceAtLeast(0), height))
                        // Relative: with the rail on the right (right-to-left languages) the sheet keeps to the left of it.
                        layout(maxWidth, maxHeight) { placeable.placeRelative(IntOffset(x, offset.roundToInt())) }
                    }
                    // The sheet owns every touch inside it: nothing falls through to the page behind.
                    .pointerInput(Unit) { detectTapGestures { } }
                    .anchoredDraggable(anchored, Orientation.Vertical, enabled = !open.fullscreen && !open.inPip, flingBehavior = fling),
            ) {
                key(open.target) { PlayerPage(open.target, session, onOpenChannel, onSignIn) }
            }
        }
    }
}

/** The measurements the layout pass shares with [PlayerSheet.progress]: plain numbers, written and read in layout. */
private class SheetMetrics {
    var collapsedPx: Float = 0f
}

@Composable
private fun PlayerPage(target: PlaybackTarget, session: PlayerSessionController, onOpenChannel: (String) -> Unit, onSignIn: () -> Unit) {
    val minimise = { session.dispatch(PlayerSessionEvent.Minimise) }
    when (target) {
        is PlaybackTarget.Live -> LivePlayerRoute(target.login, onBack = minimise, onOpenChannel = onOpenChannel, onSignIn = onSignIn, autoplay = session.autoplay)

        is PlaybackTarget.Replay -> ReplayRoute(
            info = target.toInfo(),
            autoplay = session.autoplay,
            onBack = minimise,
            onOpenChannel = onOpenChannel,
            onOpenReplay = { other -> session.open(other) },
        )

        is PlaybackTarget.Clip -> ClipRoute(target.slug, target.title, onBack = minimise, autoplay = session.autoplay)
    }
}

/**
 * A ViewModel store of its own for each thing that plays: its presenter (chat, quality, the stream) lives as long as that target is
 * the session's, and is cleared when another one replaces it or the session closes. Not the activity's, which would keep a channel's
 * chat connected after the viewer closed the player.
 */
@Composable
private fun rememberTargetStoreOwner(target: PlaybackTarget): ViewModelStoreOwner {
    val owner = remember(target) {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    return owner
}

/**
 * The anchored draggable's own fling, except that a slow downward flick of the strip does not count as a fling (see [DismissPolicy]): it
 * settles by distance instead, so only a firm swipe closes the player.
 */
@Composable
private fun rememberDeliberateFling(
    state: AnchoredDraggableState<Rest>,
    spec: AnimationSpec<Float>,
    density: Float,
    positionalThreshold: (Float) -> Float,
): TargetedFlingBehavior {
    val delegate = AnchoredDraggableDefaults.flingBehavior(state, positionalThreshold = positionalThreshold, animationSpec = spec)
    return remember(state, delegate, density) {
        object : TargetedFlingBehavior {
            override suspend fun ScrollScope.performFling(initialVelocity: Float, onRemainingDistanceUpdated: (Float) -> Unit): Float {
                val velocity = DismissPolicy.settleVelocity(state.settledValue == Rest.Collapsed, initialVelocity, density)
                return with(delegate) { performFling(velocity, onRemainingDistanceUpdated) }
            }
        }
    }
}
