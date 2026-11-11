package com.twitchplayer.phone.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.twitchplayer.phone.R
import com.twitchplayer.phone.ui.theme.LocalReducedMotion
import com.twitchplayer.tv.core.presentation.Load
import kotlinx.coroutines.delay

/** A section title; announced as a heading so TalkBack users can jump between sections. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.semantics { heading() },
    )
}

/** Centered empty / error / signed-out message with an optional action. Scrollable so pull-to-refresh and small screens work. */
@Composable
fun MessageState(title: String, modifier: Modifier = Modifier, body: String? = null, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        if (body != null) {
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (actionLabel != null && onAction != null) {
            Button(onClick = onAction, modifier = Modifier.padding(top = 24.dp)) { Text(actionLabel) }
        }
    }
}

/**
 * The four states of a network-backed screen: [skeleton] while loading, a sign-in prompt when signed out, a retry message when
 * nothing could be shown, and [content] otherwise.
 */
@Composable
fun <T> LoadHost(
    load: Load<T>,
    onRetry: () -> Unit,
    onSignIn: () -> Unit,
    skeleton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    Box(modifier.fillMaxSize()) {
        when (load) {
            Load.Loading -> skeleton()

            Load.SignedOut -> MessageState(
                title = stringResource(R.string.state_signin_title),
                body = stringResource(R.string.phone_signin_body),
                actionLabel = stringResource(R.string.state_signin),
                onAction = onSignIn,
            )

            Load.Failed -> MessageState(
                title = stringResource(R.string.state_error),
                body = stringResource(R.string.phone_error_body),
                actionLabel = stringResource(R.string.state_retry),
                onAction = onRetry,
            )

            is Load.Ready -> content(load.data)
        }
    }
}

/** A neutral block that breathes slowly while content loads; still when motion is reduced. */
@Composable
fun SkeletonBlock(modifier: Modifier = Modifier, shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(8.dp)) {
    val reduced = LocalReducedMotion.current
    val alpha = if (reduced) {
        SKELETON_STILL_ALPHA
    } else {
        val transition = rememberInfiniteTransition(label = "skeleton")
        val pulse by transition.animateFloat(
            initialValue = SKELETON_LOW_ALPHA,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(SKELETON_PULSE_MS), RepeatMode.Reverse),
            label = "skeleton-alpha",
        )
        pulse
    }
    Box(modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh).alpha(alpha))
}

/** The geometry of a [StreamCard] in skeleton tones, for the same [layout]. Hidden from accessibility: the screen announces loading itself. */
@Composable
fun StreamCardSkeleton(layout: StreamLayout, modifier: Modifier = Modifier) {
    if (layout.compact) {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SkeletonBlock(Modifier.width(SKELETON_THUMBNAIL_WIDTH).aspectRatio(THUMBNAIL_RATIO))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonBlock(Modifier.fillMaxWidth(SKELETON_TITLE_WIDTH).height(14.dp))
                SkeletonBlock(Modifier.fillMaxWidth(SKELETON_META_WIDTH).height(12.dp))
            }
        }
    } else {
        Column(modifier) {
            SkeletonBlock(Modifier.fillMaxWidth().aspectRatio(THUMBNAIL_RATIO))
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SkeletonBlock(Modifier.size(AvatarSmall), CircleShape)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkeletonBlock(Modifier.fillMaxWidth(SKELETON_TITLE_WIDTH).height(14.dp))
                    SkeletonBlock(Modifier.fillMaxWidth(SKELETON_META_WIDTH).height(12.dp))
                }
            }
        }
    }
}

/**
 * Pull-to-refresh around [content]. The presenters expose no "refreshing" flag (a reload keeps the data on screen and swaps it
 * when the new copy arrives), so the indicator is a short, bounded gesture acknowledgement.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefreshBox(onRefresh: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(refreshing) {
        if (refreshing) {
            delay(REFRESH_INDICATOR_MS)
            refreshing = false
        }
    }
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            refreshing = true
            onRefresh()
        },
        modifier = modifier,
    ) { content() }
}

private val SKELETON_THUMBNAIL_WIDTH = 168.dp
private const val SKELETON_STILL_ALPHA = 0.7f
private const val SKELETON_LOW_ALPHA = 0.45f
private const val SKELETON_PULSE_MS = 900
private const val SKELETON_TITLE_WIDTH = 0.8f
private const val SKELETON_META_WIDTH = 0.5f
private const val REFRESH_INDICATOR_MS = 900L
