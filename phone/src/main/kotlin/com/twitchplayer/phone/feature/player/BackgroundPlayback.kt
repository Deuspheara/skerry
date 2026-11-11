package com.twitchplayer.phone.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Player
import kotlinx.coroutines.delay

/**
 * What a replay or a clip does when the app leaves the screen: it pauses, and it picks up again on return only if it was playing when
 * the app left (a viewer who paused on purpose finds it paused). Same rules as the live player's `onForeground` and the TV replay screen.
 *
 * Picture-in-Picture needs no exception: entering the window only pauses the activity (`onPause`, the picture keeps playing), it never
 * stops it. The activity is stopped when the window is dismissed with its X, when the screen locks and when another app takes over, and
 * in the first case `isInPictureInPictureMode` is still true while `onStop` runs (seen on an API 36 emulator: a replay kept playing
 * after the X when `onStop` trusted it). So any stop pauses.
 */
class BackgroundResume {
    private var resumeOnStart = false

    /** The activity stopped: playback must pause now; remembers whether to resume later. */
    fun onStop(playing: Boolean) {
        resumeOnStart = playing
    }

    /** The activity started again. Returns true when playback should resume (it was playing when the app left). */
    fun onStart(): Boolean = resumeOnStart.also { resumeOnStart = false }
}

/**
 * Binds [BackgroundResume] to a lifecycle and a Media3 player. A finished clip (end of stream) counts as not playing: coming back never
 * restarts it.
 */
class PauseInBackground(private val player: Player) : LifecycleEventObserver {
    private val policy = BackgroundResume()

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_STOP -> {
                policy.onStop(playing = player.playWhenReady && player.playbackState != Player.STATE_ENDED)
                player.pause()
            }

            Lifecycle.Event.ON_START -> if (policy.onStart()) player.play()

            else -> Unit
        }
    }
}

/** Pauses [player] while the app is stopped (see [BackgroundResume]). */
@Composable
fun PauseWhenInBackground(player: Player) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(player, lifecycle) {
        val observer = PauseInBackground(player)
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}

/**
 * Keeps the place a replay or clip has reached for a page brought back after the process was killed: [save] is called when playback
 * pauses and when the app stops, so a pause followed by the process dying (or the viewer swiping the app away) keeps the position.
 * Nothing is saved before the player has a source or after it ended (the caller forgets a finished replay itself).
 */
class PositionSaver(private val player: Player, private val save: (Long) -> Unit) :
    Player.Listener,
    LifecycleEventObserver {
    fun saveNow() {
        if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) return
        save(player.currentPosition.coerceAtLeast(0L))
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (!isPlaying) saveNow()
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (event == Lifecycle.Event.ON_STOP) saveNow()
    }
}

/**
 * [PositionSaver] plus a save every [everyMs] while playing. [enabled] switches all of it off (a replay that has not started, a
 * viewer dragging the seek bar).
 */
@Composable
fun SavePlaybackPosition(player: Player, everyMs: Long, enabled: Boolean, save: (Long) -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentEnabled = rememberUpdatedState(enabled)
    val currentSave = rememberUpdatedState(save)
    DisposableEffect(player, lifecycle) {
        val saver = PositionSaver(player) { at -> if (currentEnabled.value) currentSave.value(at) }
        player.addListener(saver)
        lifecycle.addObserver(saver)
        onDispose {
            player.removeListener(saver)
            lifecycle.removeObserver(saver)
        }
    }
    LaunchedEffect(player) {
        while (true) {
            delay(everyMs)
            if (currentEnabled.value && player.isPlaying) currentSave.value(player.currentPosition)
        }
    }
}
