package com.twitchplayer.phone.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.twitchplayer.tv.core.data.model.ChatMode

/**
 * What a player page keeps when the system kills the app's process in the background and later brings the page back (saved-instance
 * state, so it also survives a recreation): the chat mode the viewer chose for this session, the orientation lock of a fullscreen
 * toggle, and the position a replay or clip had reached. The channel or replay itself comes back through the navigation back stack.
 */
class PlayerRestoration(
    chatMode: String? = null,
    lock: OrientationLock = OrientationLock.None,
    positionMs: Long? = null,
    durationMs: Long? = null,
) {
    /** A [ChatMode] name, as written by `ChatMode.name`; null until the viewer changed it. */
    var chatMode: String? by mutableStateOf(chatMode)

    var lock: OrientationLock by mutableStateOf(lock)

    /** Where a replay or clip was, in milliseconds; null before it started. */
    var positionMs: Long? by mutableStateOf(positionMs)

    /** The length of the clip when [positionMs] was written, so a position in its last seconds is not resumed (see [resumableRestoredPosition]). */
    var durationMs: Long? by mutableStateOf(durationMs)

    companion object {
        val Saver = listSaver<PlayerRestoration, Any?>(
            save = { listOf(it.chatMode, it.lock.name, it.positionMs, it.durationMs) },
            restore = { saved ->
                PlayerRestoration(
                    chatMode = saved[0] as String?,
                    lock = OrientationLock.entries.firstOrNull { it.name == saved[1] } ?: OrientationLock.None,
                    positionMs = saved[2] as Long?,
                    durationMs = saved.getOrNull(3) as Long?,
                )
            },
        )
    }
}

/** The restoration state of one player page; [key] (the channel or the replay id) keeps two pages apart. */
@Composable
fun rememberPlayerRestoration(key: Any?): PlayerRestoration = rememberSaveable(key, saver = PlayerRestoration.Saver) { PlayerRestoration() }

/** The saved [ChatMode] name as a mode, or null when nothing was saved or the name is not one we know (an older build's value). */
fun restoredChatMode(name: String?): ChatMode? = ChatMode.entries.firstOrNull { it.name == name }

/**
 * The chat mode has one writer on each side of the saved state: the viewer's choices go through [choose] (saved state first, then the
 * presenter), and the saved mode reaches the presenter once, when the page comes back ([toRestore]); from then on the presenter's
 * state is what the screen renders. Nothing copies the presenter's state back into the saved state, so a default that is still
 * loading can never overwrite what the viewer chose.
 */
fun PlayerRestoration.choose(mode: ChatMode, send: (ChatMode) -> Unit) {
    chatMode = mode.name
    send(mode)
}

/**
 * The mode to hand to the presenter when the page comes back, or null when the viewer never chose one (or the name is unknown). It is
 * **not** compared with the presenter's current mode: until the saved default has loaded that mode is only the placeholder
 * (`ChatMode.Hidden`), so "equal, nothing to do" would drop a choice equal to the placeholder (saved default Compact, viewer chose
 * Hidden, process killed). Sending it is idempotent, and it also marks the choice as made so the late default cannot overwrite it.
 */
fun PlayerRestoration.toRestore(): ChatMode? = restoredChatMode(chatMode)
