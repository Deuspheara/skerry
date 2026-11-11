package com.twitchplayer.tv.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

/**
 * Remembers which item of a screen last had D-pad focus (survives back-stack pops and process death)
 * and puts focus back on it. Every tracked item owns a [FocusRequester] keyed by its id, so the
 * "remembered" target stays correct while focus moves, without recomposing anything: [lastKey] is a
 * plain field, not snapshot state.
 */
@Stable
class FocusMemory internal constructor(var lastKey: String?) {
    private val requesters = HashMap<String, FocusRequester>()
    private var defaultKey: String? = null

    /**
     * True from the screen's first composition until its restore attempt has run. Compose hands focus to the first focusable
     * the moment a screen comes back, before [RequestFocusWhen] reads [lastKey]: without this guard that automatic focus
     * overwrote the remembered item, and Back from a category landed on the first card instead of the poster just opened.
     */
    private var restoring = false

    private fun requesterFor(key: String) = requesters.getOrPut(key) { FocusRequester() }

    /** Marks an item. [default] designates the item that takes focus when nothing is remembered yet. */
    fun Modifier.track(key: String, default: Boolean = false): Modifier {
        if (default) defaultKey = key
        return this
            .focusRequester(requesterFor(key))
            .onFocusChanged { if (it.isFocused && !restoring && recentDirectionKey()) lastKey = key }
    }

    /** The remembered item (or the default one) as a focus target, e.g. "down from the chips lands on the remembered poster". */
    val focusRequester: FocusRequester
        get() = (lastKey ?: defaultKey)?.let(::requesterFor) ?: FocusRequester.Default

    /** Takes focus on the remembered/default item once [ready]; retries across a few frames while lazy items compose. */
    @Composable
    fun RequestFocusWhen(ready: Boolean, token: Any? = null) {
        androidx.compose.runtime.remember { restoring = true }
        LaunchedEffect(ready, token) {
            if (!ready) return@LaunchedEffect
            try {
              repeat(8) {
                if (runCatching { focusRequester.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
                withFrameNanos { }
              }
            } finally { restoring = false }
        }
    }
}

/**
 * Time of the last D-pad key press, fed from the app's key handler. Focus can also move without the user: when a screen
 * is left, Compose gives focus to the first card of the screen underneath it for a moment, and that must not become the
 * "remembered" item (Back from a category then landed on the first card instead of the poster just opened).
 */
object DirectionKeys {
    @Volatile private var lastAt = 0L
    fun onKey(event: androidx.compose.ui.input.key.KeyEvent) {
        if (event.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return
        when (event.key) {
            androidx.compose.ui.input.key.Key.DirectionUp, androidx.compose.ui.input.key.Key.DirectionDown,
            androidx.compose.ui.input.key.Key.DirectionLeft, androidx.compose.ui.input.key.Key.DirectionRight,
            androidx.compose.ui.input.key.Key.PageUp, androidx.compose.ui.input.key.Key.PageDown -> lastAt = android.os.SystemClock.uptimeMillis()
            else -> Unit
        }
    }
    fun recent(): Boolean = android.os.SystemClock.uptimeMillis() - lastAt < 600L
}

private fun recentDirectionKey() = DirectionKeys.recent()

@Composable
fun rememberFocusMemory(): FocusMemory = rememberSaveable(
    saver = Saver(save = { it.lastKey ?: "" }, restore = { FocusMemory(it.ifEmpty { null }) }),
) { FocusMemory(null) }
