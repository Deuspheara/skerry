@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.twitchplayer.tv.core.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type

/*
 * Focus redirection between groups. This uses FocusProperties.enter / exit, which Compose 1.12 marks
 * deprecated in favour of onEnter / onExit. The replacement is not usable here: onExit is only invoked
 * when the geometric search finds a neighbour (our keyboard -> results and chips -> grid hops often have
 * none), and a focus request issued inside the callback is swallowed by the running focus transaction.
 * Verified on-device (see TvFocusFlowTest.browseKeeps...). Revisit when a supported redirect API exists.
 */

/** When focus tries to leave this group in [direction], send it to [target] (resolved at that moment). */
@Suppress("DEPRECATION")
fun Modifier.redirectFocusOnExit(direction: FocusDirection, target: () -> FocusRequester): Modifier = focusProperties {
    exit = { if (it == direction) target() else FocusRequester.Default }
}

/** When focus enters this group from a D-pad move, land on [target] (e.g. the selected rail item). */
@Suppress("DEPRECATION")
fun Modifier.redirectFocusOnEnter(target: () -> FocusRequester): Modifier = focusProperties {
    enter = { if (it in DPad) target() else FocusRequester.Default }
}

private val DPad = setOf(FocusDirection.Left, FocusDirection.Right, FocusDirection.Up, FocusDirection.Down)

/**
 * Sends [key] presses bubbling up from any child to [target] (resolved at that moment). For "header -> content"
 * jumps where every child of the header goes down to the same remembered item: a key handler is deterministic
 * where the focus-search based redirect above is not consulted (the geometric search already has an answer).
 */
fun Modifier.jumpOnKey(key: Key, target: () -> FocusRequester): Modifier = onKeyEvent { event ->
    if (event.type == KeyEventType.KeyDown && event.key == key) runCatching { target().requestFocus() }.getOrDefault(false) else false
}
