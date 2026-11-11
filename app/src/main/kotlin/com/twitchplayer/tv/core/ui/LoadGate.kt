package com.twitchplayer.tv.core.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.twitchplayer.tv.R
import com.twitchplayer.tv.core.designsystem.component.EmptyState
import com.twitchplayer.tv.core.designsystem.component.TvButton
import com.twitchplayer.tv.core.designsystem.component.TvSpinner
import com.twitchplayer.tv.core.designsystem.icon.TvIcons
import com.twitchplayer.tv.core.presentation.Load
import kotlinx.coroutines.delay

/**
 * Renders the non-content states of a [Load] with a focused action, so D-pad is never stranded.
 * [ready] draws the content.
 */
@Composable
fun <T> LoadGate(
    load: Load<T>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    /** Placeholder with the screen's layout, shown while loading; the spinner is the fallback. */
    skeleton: (@Composable () -> Unit)? = null,
    ready: @Composable (T) -> Unit,
) {
    val wasLoading = remember { mutableStateOf(load is Load.Loading) }
    if (load is Load.Loading) wasLoading.value = true
    when (load) {
        is Load.Ready -> {
            // Content that follows a loading state in this composition enters with a short stagger of its first cards.
            val gate = remember { if (wasLoading.value) StaggerGate() else null }
            CompositionLocalProvider(LocalStagger provides gate) { ready(load.data) }
        }

        // Most loads (and every cached one) finish within a few frames: the placeholder only shows when it takes a while.
        Load.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            var slow by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                delay(if (skeleton != null) SKELETON_DELAY_MS else SPINNER_DELAY_MS)
                slow = true
            }
            if (slow) {
                if (skeleton != null) com.twitchplayer.tv.core.designsystem.component.SkeletonPulse { skeleton() } else TvSpinner()
            }
            FocusHold()
        }

        Load.SignedOut -> StateWithAction(
            modifier,
            TvIcons.User,
            stringResource(R.string.state_signin_title),
            stringResource(R.string.state_signin),
            TvIcons.User,
            LocalAppActions.current::signIn,
        )

        Load.Failed -> StateWithAction(
            modifier,
            TvIcons.OffAir,
            stringResource(R.string.state_error),
            stringResource(R.string.state_retry),
            TvIcons.Play,
            onRetry,
        )
    }
}

@Composable
private fun StateWithAction(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    action: String,
    actionIcon: androidx.compose.ui.graphics.vector.ImageVector,
    onAction: () -> Unit,
) {
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { requester.requestFocus() } }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // The state arrives with the same short fade and rise as content, instead of popping in.
        Box(Modifier.staggerIn(0, remember { StaggerGate() })) {
            EmptyState(icon, title) {
                TvButton(action, actionIcon, onAction, Modifier.focusRequester(requester), primary = true)
            }
        }
    }
}

private const val SPINNER_DELAY_MS = 300L
private const val SKELETON_DELAY_MS = 120L

/**
 * Keeps D-pad focus inside the screen while it loads. Without it the card that was pressed disappears with its screen
 * and Compose hands focus to the first thing left, the navigation rail, which pops open for the whole load.
 * Invisible and replaced by the screen's own first focus when the content arrives.
 */
@Composable
private fun FocusHold() {
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { requester.requestFocus() } }
    Box(Modifier.size(1.dp).focusRequester(requester).focusable())
}
