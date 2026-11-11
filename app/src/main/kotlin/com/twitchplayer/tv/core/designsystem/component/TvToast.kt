package com.twitchplayer.tv.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.twitchplayer.tv.core.designsystem.theme.LocalMotion
import com.twitchplayer.tv.core.designsystem.theme.TvColors
import com.twitchplayer.tv.core.designsystem.theme.TvDimens
import com.twitchplayer.tv.core.designsystem.theme.TvShapes
import com.twitchplayer.tv.core.designsystem.theme.TvType
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay

/**
 * Queue of short messages: each one is shown in turn (a new one never replaces the one on screen), a repeat of the last
 * pending message is dropped, and the dwell shortens while others wait so a burst clears quickly.
 */
@Stable
class ToastState {
    var message: String? by mutableStateOf(null)
        internal set
    private val pending = ArrayDeque<String>()
    private val signal = Channel<Unit>(Channel.CONFLATED)

    fun show(text: String) {
        synchronized(pending) {
            if (text == (pending.lastOrNull() ?: message)) return
            pending.addLast(text)
        }
        signal.trySend(Unit)
    }

    internal fun poll(): String? = synchronized(pending) { pending.removeFirstOrNull() }
    internal fun waiting(): Boolean = synchronized(pending) { pending.isNotEmpty() }
    internal suspend fun await() = signal.receive()
}

val LocalToast = staticCompositionLocalOf { ToastState() }

/** One short word at the bottom of the screen for ~2 s; announced as a polite live region. */
@Composable
fun TvToastHost(state: ToastState, modifier: Modifier = Modifier) {
    val motion = LocalMotion.current
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(state) {
        while (true) {
            state.await()
            while (true) {
                val next = state.poll() ?: break
                state.message = next
                alpha.snapTo(0f)
                alpha.animateTo(1f, motion.medium())
                delay(if (state.waiting()) 1200 else 2000)
                alpha.animateTo(0f, motion.exit())
                state.message = null
            }
        }
    }
    // Top right, under the clock: the bottom of the screen belongs to the player's controls and chat.
    Box(modifier.fillMaxSize().padding(top = TvDimens.TopBar + 24.dp, end = TvDimens.SafeHorizontal), contentAlignment = Alignment.TopEnd) {
        val text = state.message
        if (text != null) {
            Text(
                text,
                style = TvType.Pill,
                color = TvColors.Text,
                modifier = Modifier
                    .graphicsLayer { this.alpha = alpha.value; translationY = -(1f - alpha.value) * 8.dp.toPx() }
                    .background(TvColors.Alt2, TvShapes.Chip)
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}
