package com.twitchplayer.phone.feature.player

import android.content.res.Configuration
import android.graphics.Rect
import android.view.OrientationEventListener
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toAndroidRect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twitchplayer.phone.R
import com.twitchplayer.phone.ui.components.PhoneIcons
import com.twitchplayer.tv.core.ui.KeepScreenOn

/**
 * The frame every player page shares, in one layout for every mode (see [PlayerSheetLayout]): portrait (picture on top, [portraitContent]
 * below), landscape (picture fills the screen, an optional [landscapeSide] panel beside it), the strip (a small picture and a title
 * while the sheet is minimised), plus the tap-to-show control layer, the fullscreen button with its orientation lock, immersive bars,
 * Picture-in-Picture, keep-screen-on and Back (leaves fullscreen first).
 */
@Composable
fun PlayerScaffold(
    snapshot: PlayerSnapshot,
    failed: Boolean,
    title: String,
    subtitle: String?,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    restoration: PlayerRestoration,
    video: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    /** Keeps the control layer up (a sheet is open, a seek is in progress, the viewer is deciding something). */
    pinned: Boolean = false,
    stageOverlay: @Composable BoxScope.(PlayerChrome) -> Unit = {},
    /** Drawn above the control layer, undimmed and clickable: a failure message with its retry, chat laid over the picture. */
    stageTop: @Composable BoxScope.(PlayerChrome) -> Unit = {},
    topActions: @Composable RowScope.(PlayerChrome) -> Unit = {},
    center: @Composable (PlayerChrome) -> Unit = {},
    bottom: @Composable ColumnScope.(PlayerChrome) -> Unit = {},
    landscapeSide: (@Composable (PlayerChrome) -> Unit)? = null,
    portraitContent: @Composable ColumnScope.(PlayerChrome) -> Unit = {},
) {
    val host = LocalPlayerHost.current
    val context = LocalContext.current
    val sheet = LocalPlayerSheet.current
    val strip = sheet?.strip == true
    val sensorLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    // A minimised sheet is a strip whatever way the phone is held: the picture only goes fullscreen from the expanded sheet.
    val landscape = sensorLandscape && !strip
    val inPip by host.inPictureInPicture.collectAsStateWithLifecycle()
    val controls = remember { ControlsVisibility() }
    // A lock saved before the process died is applied again (see PlayerRestoration).
    val policy = remember { FullscreenPolicy(restoration.lock) }
    var lock by remember { mutableStateOf(policy.lock) }

    val onToggleFullscreen = {
        policy.toggle(isLandscape = landscape)
        lock = policy.lock
        restoration.lock = lock
        host.lockOrientation(lock)
    }
    val chrome = remember(controls, landscape) { PlayerChrome(controls, landscape, onToggleFullscreen) }

    // The control layer hides on its own unless something pins it: a paused or finished picture, an error, an open sheet.
    val keepUp = pinned || !snapshot.playWhenReady || snapshot.ended || failed
    LaunchedEffect(controls.visible, controls.interactions, keepUp) { controls.autoHide(keepUp) }

    // A fullscreen toggle locks the orientation until the phone has been turned and then back upright (see FullscreenPolicy).
    DisposableEffect(lock) {
        if (lock == OrientationLock.None) return@DisposableEffect onDispose { }
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (policy.onSensor(orientation)) {
                    lock = policy.lock
                    restoration.lock = lock
                    host.lockOrientation(lock)
                }
            }
        }
        listener.enable()
        onDispose { listener.disable() }
    }
    // The strip hands the screen back to the app: dark-on-light status bar icons, free rotation, no fullscreen lock.
    DisposableEffect(strip) {
        if (strip) {
            host.setVideoSystemBars(false)
            policy.reset()
            lock = policy.lock
            restoration.lock = lock
            host.lockOrientation(OrientationLock.None)
        } else {
            host.setVideoSystemBars(true)
            if (lock != OrientationLock.None) host.lockOrientation(lock)
        }
        onDispose { }
    }
    DisposableEffect(Unit) {
        onDispose {
            host.setVideoSystemBars(false)
            policy.reset()
            host.lockOrientation(OrientationLock.None)
            host.setImmersive(false)
            host.clearPictureInPicture()
        }
    }
    val immersive = landscape && !inPip
    DisposableEffect(immersive) {
        host.setImmersive(immersive)
        onDispose { }
    }

    // Picture-in-Picture: eligible only while the sheet is expanded and a picture is playing; the window's button toggles play / pause.
    val togglePlay by rememberUpdatedState(onTogglePlay)
    var stageBounds by remember { mutableStateOf<Rect?>(null) }
    DisposableEffect(snapshot.hasPicture, snapshot.isPlaying, failed, snapshot.videoWidth, snapshot.videoHeight, stageBounds, strip) {
        val eligible = PipPolicy.eligible(onPlayerScreen = !strip, hasPicture = snapshot.hasPicture, playing = snapshot.isPlaying, failed = failed)
        host.updatePictureInPicture(eligible, snapshot.isPlaying, snapshot.videoWidth, snapshot.videoHeight, stageBounds) { togglePlay() }
        onDispose { }
    }
    KeepScreenOn(enabled = snapshot.isPlaying || snapshot.buffering)

    // Back leaves fullscreen first (to portrait); from the expanded sheet the host minimises it (see PlayerOverlay).
    BackHandler(enabled = landscape && !inPip) { onToggleFullscreen() }

    val progress = sheet?.progress ?: NOT_HOSTED
    val toggleControlsLabel = stringResource(if (strip) R.string.phone_player_expand else R.string.phone_player_toggle_controls)
    // One place draws the picture, and the layout below never gives it another parent: it keeps its view across portrait, landscape,
    // the strip and Picture-in-Picture.
    val stage: @Composable () -> Unit = {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onGloballyPositioned { stageBounds = it.boundsInWindow().toAndroidRect() }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = toggleControlsLabel) {
                    if (strip) sheet.expand() else controls.toggle()
                }
                .testTag(STAGE_TAG),
        ) {
            video(Modifier.fillMaxSize())
            if (!inPip && !strip) {
                // The layer fades out over the first moments of a drag: it is not drawn at strip size.
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = SheetGeometry.controlsAlpha(progress()) }) {
                    stageOverlay(chrome)
                    PlayerControlsLayer(
                        visible = controls.visible,
                        title = title,
                        subtitle = subtitle,
                        onBack = if (landscape) onToggleFullscreen else onBack,
                        backIcon = if (!landscape && sheet != null) PhoneIcons.ChevronDown else null,
                        backDescription = if (!landscape && sheet != null) stringResource(R.string.phone_player_minimise) else null,
                        contentInsets = if (landscape) WindowInsets.safeDrawing else WindowInsets(0),
                        topActions = { topActions(chrome) },
                        center = { center(chrome) },
                        bottom = { bottom(chrome) },
                    )
                    stageTop(chrome)
                }
            }
        }
    }

    // A tablet-width window in portrait shows the expanded player as two panes (picture and page side by side).
    val twoPane = !landscape && !inPip && with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() } >= TWO_PANE_MIN_WIDTH
    val mode = when {
        inPip -> SheetMode.Window
        landscape -> SheetMode.Landscape
        else -> SheetMode.Portrait
    }
    val background = MaterialTheme.colorScheme.background
    val stripBackground = MaterialTheme.colorScheme.surfaceContainerHigh
    val density = LocalDensity.current
    val stripHeight = rememberStripHeight()
    PlayerSheetLayout(
        mode = mode,
        progress = progress,
        statusBarPx = WindowInsets.statusBars.getTop(density),
        stripHeightPx = with(density) { stripHeight.roundToPx() },
        stripPictureWidthPx = with(density) { SheetGeometry.stripPictureWidth(stripHeight).roundToPx() },
        twoPane = twoPane,
        modifier = modifier.fillMaxSize().drawBehind {
            drawRect(if (mode == SheetMode.Portrait) lerp(background, stripBackground, progress()) else Color.Black)
        },
        stage = stage,
        pictureAspect = { widthPx, heightPx -> SheetGeometry.pictureAspect(snapshot.videoWidth, snapshot.videoHeight, widthPx, heightPx) },
        side = if (mode == SheetMode.Landscape) landscapeSide?.let { side -> { side(chrome) } } else null,
        body = if (mode == SheetMode.Portrait && (sheet == null || sheet.bodyVisible)) {
            {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                    Column(Modifier.fillMaxSize().background(background)) { portraitContent(chrome) }
                }
            }
        } else {
            null
        },
        strip = if (mode == SheetMode.Portrait && sheet != null && sheet.stripVisible) {
            { PlayerStrip(title, subtitle, snapshot.playWhenReady, onTogglePlay, sheet.expand, sheet.close) }
        } else {
            null
        },
    )
}

private val NOT_HOSTED: () -> Float = { 0f }
private val TWO_PANE_MIN_WIDTH = 600.dp

/** The fullscreen button of the bottom bar. */
@Composable
fun FullscreenButton(chrome: PlayerChrome) {
    ControlButton(
        icon = if (chrome.landscape) PhoneIcons.FullscreenExit else PhoneIcons.Fullscreen,
        description = stringResource(if (chrome.landscape) R.string.player_exit_fullscreen else R.string.player_fullscreen),
        onClick = {
            chrome.controls.touch()
            chrome.toggleFullscreen()
        },
    )
}

const val STAGE_TAG = "player-stage"
