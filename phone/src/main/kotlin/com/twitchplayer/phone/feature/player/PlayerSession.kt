package com.twitchplayer.phone.feature.player

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import com.twitchplayer.phone.navigation.PlaybackTarget

/** Where the player sheet rests: the whole screen, or the docked strip above the tab bar. */
enum class SheetState { Expanded, Collapsed }

/**
 * The player session, as plain data: nothing is playing ([Closed]) or something is ([Open]). The tabs and their pages are navigation;
 * the session lives above them, so leaving a page never ends it.
 */
@Immutable
sealed interface PlayerSessionState {
    data object Closed : PlayerSessionState

    /**
     * @property sheet where the sheet rests; kept while [inPip] so the window's end brings the same layout back.
     * @property fullscreen the picture fills the screen sideways (landscape); implies [SheetState.Expanded].
     * @property inPip the activity is shown in the Picture-in-Picture window: the picture alone, whatever [sheet] says.
     */
    data class Open(
        val target: PlaybackTarget,
        val sheet: SheetState = SheetState.Expanded,
        val fullscreen: Boolean = false,
        val inPip: Boolean = false,
    ) : PlayerSessionState {
        /** The page layout to draw: the strip only when the sheet rests collapsed outside the Picture-in-Picture window. */
        val showsStrip: Boolean get() = sheet == SheetState.Collapsed && !inPip
    }
}

/** What happens to the session. */
sealed interface PlayerSessionEvent {
    /** A tap on a stream, replay or clip: opens the sheet expanded; the same target again expands it, another one replaces it. */
    data class Open(val target: PlaybackTarget) : PlayerSessionEvent

    /** The chevron, a drag down that settled on the strip, or Back from the expanded sheet. */
    data object Minimise : PlayerSessionEvent

    /** A tap on the strip, or a drag up that settled expanded. */
    data object Expand : PlayerSessionEvent

    /** The strip's X, a drag of the strip off the screen. */
    data object Close : PlayerSessionEvent

    /** The window turned sideways or upright. */
    data class FullscreenChanged(val fullscreen: Boolean) : PlayerSessionEvent

    data class PipChanged(val inPip: Boolean) : PlayerSessionEvent
}

object PlayerSessionReducer {
    fun reduce(state: PlayerSessionState, event: PlayerSessionEvent): PlayerSessionState = when (event) {
        is PlayerSessionEvent.Open -> open(state, event.target)
        PlayerSessionEvent.Minimise -> minimise(state)
        PlayerSessionEvent.Expand -> withOpen(state) { it.copy(sheet = SheetState.Expanded) }
        PlayerSessionEvent.Close -> PlayerSessionState.Closed
        is PlayerSessionEvent.FullscreenChanged -> withOpen(state) { fullscreenChanged(it, event.fullscreen) }
        is PlayerSessionEvent.PipChanged -> withOpen(state) { it.copy(inPip = event.inPip) }
    }

    private inline fun withOpen(state: PlayerSessionState, change: (PlayerSessionState.Open) -> PlayerSessionState): PlayerSessionState = if (state is PlayerSessionState.Open) change(state) else state

    private fun open(state: PlayerSessionState, target: PlaybackTarget): PlayerSessionState = when {
        state !is PlayerSessionState.Open -> PlayerSessionState.Open(target)
        state.target == target -> state.copy(sheet = SheetState.Expanded)
        else -> PlayerSessionState.Open(target, inPip = state.inPip)
    }

    /** The window itself cannot be minimised, and a sideways picture leaves fullscreen first. */
    private fun minimise(state: PlayerSessionState): PlayerSessionState = withOpen(state) {
        if (it.inPip || it.fullscreen) it else it.copy(sheet = SheetState.Collapsed)
    }

    private fun fullscreenChanged(state: PlayerSessionState.Open, fullscreen: Boolean) = state.copy(fullscreen = fullscreen, sheet = if (fullscreen) SheetState.Expanded else state.sheet)
}

/** What the Back button does while a session exists. */
enum class BackAction {
    /** Leave fullscreen (the screen turns upright). */
    ExitFullscreen,

    /** Collapse the sheet to the strip; Back never closes a session. */
    Minimise,

    /** Not the player's: the navigation (pages, tabs, leaving the app) handles it. */
    PassThrough,
}

object BackPolicy {
    fun action(state: PlayerSessionState): BackAction = when {
        state !is PlayerSessionState.Open || state.inPip -> BackAction.PassThrough
        state.fullscreen -> BackAction.ExitFullscreen
        state.sheet == SheetState.Expanded -> BackAction.Minimise
        else -> BackAction.PassThrough
    }
}

/** Observable holder of the session for the composition. */
@Stable
class PlayerSessionController(initial: PlayerSessionState = PlayerSessionState.Closed, restored: Boolean = false) {
    var state: PlayerSessionState by mutableStateOf(initial)
        private set

    /**
     * Whether a replay or clip starts playing by itself. A session brought back after the process was killed comes back paused (the
     * viewer did not just ask for it, and sound out of nowhere is a bug); the next thing the viewer opens plays as usual.
     */
    var autoplay: Boolean by mutableStateOf(!restored)
        private set

    fun dispatch(event: PlayerSessionEvent) {
        state = PlayerSessionReducer.reduce(state, event)
    }

    fun open(target: PlaybackTarget) {
        if (state.let { it !is PlayerSessionState.Open || it.target != target }) autoplay = true
        dispatch(PlayerSessionEvent.Open(target))
    }

    companion object {
        /**
         * Saved with the activity: the target and where the sheet rests. Fullscreen and Picture-in-Picture are not saved (the system
         * decides both again from the window); a process that was killed comes back to the same stream or replay.
         */
        val Saver: Saver<PlayerSessionController, Any> = Saver(
            save = { controller ->
                (controller.state as? PlayerSessionState.Open)?.let { open -> listOf(open.sheet.name) + open.target.toSaved() }.orEmpty()
            },
            restore = { saved ->
                @Suppress("UNCHECKED_CAST")
                val list = saved as List<Any?>
                val sheet = list.firstOrNull()?.let { name -> SheetState.entries.firstOrNull { it.name == name } }
                val target = if (sheet == null) null else list.drop(1).toTarget()
                PlayerSessionController(if (sheet != null && target != null) PlayerSessionState.Open(target, sheet) else PlayerSessionState.Closed, restored = true)
            },
        )
    }
}

private fun PlaybackTarget.toSaved(): List<Any?> = when (this) {
    is PlaybackTarget.Live -> listOf(KIND_LIVE, login)
    is PlaybackTarget.Clip -> listOf(KIND_CLIP, slug, title)
    is PlaybackTarget.Replay -> listOf(KIND_REPLAY, id, title, durationSeconds, channelLogin, channelName, channelAvatarUrl, views, publishedAtMs)
}

@Suppress("MagicNumber")
private fun List<Any?>.toTarget(): PlaybackTarget? = runCatching {
    when (first()) {
        KIND_LIVE -> PlaybackTarget.Live(this[1] as String)

        KIND_CLIP -> PlaybackTarget.Clip(this[1] as String, this[2] as String)

        KIND_REPLAY -> PlaybackTarget.Replay(
            id = this[1] as String,
            title = this[2] as String,
            durationSeconds = this[3] as Int,
            channelLogin = this[4] as String,
            channelName = this[5] as String,
            channelAvatarUrl = this[6] as String?,
            views = this[7] as Int,
            publishedAtMs = this[8] as Long,
        )

        else -> null
    }
}.getOrNull()

private const val KIND_LIVE = "live"
private const val KIND_CLIP = "clip"
private const val KIND_REPLAY = "replay"
