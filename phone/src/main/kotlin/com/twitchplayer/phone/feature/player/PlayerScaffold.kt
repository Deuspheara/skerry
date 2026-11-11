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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAndroidRect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twitchplayer.phone.R
import com.twitchplayer.phone.ui.components.PhoneIcons
import com.twitchplayer.tv.core.ui.KeepScreenOn

/**
 * The frame every player page shares: portrait (picture on top, [portraitContent] below), landscape (picture fills the screen, an
 * optional [landscapeSide] panel beside it), the tap-to-show control layer, the fullscreen button with its orientation lock,
 * immersive bars, Picture-in-Picture, keep-screen-on and Back (leaves fullscreen first).
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
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
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

    // A fullscreen toggle locks the orientation until the phone's own sensor agrees, then auto-rotate is the system's again.
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
    DisposableEffect(Unit) {
        host.setVideoSystemBars(true)
        if (lock != OrientationLock.None) host.lockOrientation(lock)
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

    // Picture-in-Picture: eligible only while a picture is playing; the window's button toggles play / pause.
    val togglePlay by rememberUpdatedState(onTogglePlay)
    var stageBounds by remember { mutableStateOf<Rect?>(null) }
    DisposableEffect(snapshot.hasPicture, snapshot.isPlaying, failed, snapshot.videoWidth, snapshot.videoHeight, stageBounds) {
        val eligible = PipPolicy.eligible(onPlayerScreen = true, hasPicture = snapshot.hasPicture, playing = snapshot.isPlaying, failed = failed)
        host.updatePictureInPicture(eligible, snapshot.isPlaying, snapshot.videoWidth, snapshot.videoHeight, stageBounds) { togglePlay() }
        onDispose { }
    }
    KeepScreenOn(enabled = snapshot.isPlaying || snapshot.buffering)

    // Back leaves fullscreen first (to portrait); in portrait the navigation pops the page.
    BackHandler(enabled = landscape && !inPip) { onToggleFullscreen() }

    // One place draws the picture (and so owns its player view) in every layout, Picture-in-Picture included.
    val stage: @Composable (Modifier) -> Unit = { stageModifier ->
        Box(
            stageModifier
                .background(Color.Black)
                .onGloballyPositioned { stageBounds = it.boundsInWindow().toAndroidRect() }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = stringResource(R.string.phone_player_toggle_controls)) {
                    controls.toggle()
                }
                .testTag(STAGE_TAG),
        ) {
            video(Modifier.fillMaxSize())
            if (!inPip) {
                stageOverlay(chrome)
                PlayerControlsLayer(
                    visible = controls.visible,
                    title = title,
                    subtitle = subtitle,
                    onBack = if (landscape) onToggleFullscreen else onBack,
                    modifier = if (landscape) Modifier.safeDrawingPadding() else Modifier,
                    topActions = { topActions(chrome) },
                    center = { center(chrome) },
                    bottom = { bottom(chrome) },
                )
                stageTop(chrome)
            }
        }
    }

    when {
        inPip -> Box(modifier.fillMaxSize().background(Color.Black)) { stage(Modifier.fillMaxSize()) }

        landscape -> Row(modifier.fillMaxSize().background(Color.Black)) {
            stage(Modifier.weight(1f).fillMaxHeight())
            landscapeSide?.invoke(chrome)
        }

        else -> Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Box(Modifier.fillMaxWidth().background(Color.Black).statusBarsPadding()) { stage(Modifier.fillMaxWidth().aspectRatio(VIDEO_ASPECT)) }
            portraitContent(chrome)
        }
    }
}

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
private const val VIDEO_ASPECT = 16f / 9f
