package com.twitchplayer.phone.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Player

/**
 * What a replay or a clip does when the app leaves the screen (home, another app, the screen locking): it pauses, unless the picture
 * lives on in a Picture-in-Picture window, and it picks up again on return only if it was playing when the app left (a viewer who
 * paused on purpose finds it paused). Same rules as the live player's `onForeground` and the TV replay screen.
 */
class BackgroundResume {
    private var resumeOnStart = false

    /** The activity stopped. Returns true when playback must pause now; remembers whether to resume later. */
    fun onStop(playing: Boolean, inPictureInPicture: Boolean): Boolean {
        if (inPictureInPicture) return false
        resumeOnStart = playing
        return true
    }

    /** The activity started again. Returns true when playback should resume (it was playing when the app left). */
    fun onStart(): Boolean = resumeOnStart.also { resumeOnStart = false }
}

/**
 * Binds [BackgroundResume] to a lifecycle and a Media3 player. A finished clip (end of stream) counts as not playing: coming back never
 * restarts it.
 */
class PauseInBackground(private val player: Player, private val inPictureInPicture: () -> Boolean) : LifecycleEventObserver {
    private val policy = BackgroundResume()

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_STOP -> {
                val playing = player.playWhenReady && player.playbackState != Player.STATE_ENDED
                if (policy.onStop(playing, inPictureInPicture())) player.pause()
            }

            Lifecycle.Event.ON_START -> if (policy.onStart()) player.play()

            else -> Unit
        }
    }
}

/** Pauses [player] while the app is in the background (see [BackgroundResume]); outside Picture-in-Picture only. */
@Composable
fun PauseWhenInBackground(player: Player) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val host = LocalPlayerHost.current
    val inPip = rememberUpdatedState(host.inPictureInPicture)
    DisposableEffect(player, lifecycle) {
        val observer = PauseInBackground(player) { inPip.value.value }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}
