package com.twitchplayer.phone.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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

/** A page title of a tab root: one size, one weight on every tab (Browse, Following), announced as a heading. */
@Composable
fun PageTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        modifier = modifier.semantics { heading() },
    )
}

/**
 * The one empty / error / signed-out component: a tonal icon disc, a title, one line of explanation and an optional action. Scrollable so
 * pull-to-refresh and small screens work. [icon] is decorative (the title says it all).
 */
@Composable
fun MessageState(
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    icon: ImageVector? = null,
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Box(
                Modifier.size(MESSAGE_ICON_DISC).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(MESSAGE_ICON_SIZE))
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = if (icon != null) 16.dp else 0.dp).semantics { heading() },
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

private val MESSAGE_ICON_DISC = 64.dp
private val MESSAGE_ICON_SIZE = 28.dp

/** What a screen says when nobody is signed in: its own title and one line (never "channels you follow" on Browse). */
@Immutable
data class SignedOutCopy(val title: String, val body: String)

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
    /** The signed-out message of this screen; the generic one when null. */
    signedOut: SignedOutCopy? = null,
    /** Whether a refresh that failed shows its marker above the content (a full-bleed page such as Discover says it another way). */
    staleMarker: Boolean = true,
    content: @Composable (T) -> Unit,
) {
    // The skeleton, the message and the content cross-fade into each other; a refresh that only swaps the data of the content (same kind)
    // is not animated. Not with reduced motion.
    val reduced = LocalReducedMotion.current
    AnimatedContent(
        targetState = load,
        modifier = modifier.fillMaxSize(),
        contentKey = { it::class },
        transitionSpec = { if (reduced) EnterTransition.None togetherWith ExitTransition.None else fadeIn(tween(LOAD_FADE_MS)) togetherWith fadeOut(tween(LOAD_FADE_MS)) },
        label = "load-host",
    ) { state ->
        Box(Modifier.fillMaxSize()) {
            when (state) {
                Load.Loading -> skeleton()

                Load.SignedOut -> MessageState(
                    title = signedOut?.title ?: stringResource(R.string.phone_signedout_home_title),
                    body = signedOut?.body ?: stringResource(R.string.phone_signedout_home_body),
                    actionLabel = stringResource(R.string.state_signin),
                    onAction = onSignIn,
                    icon = Icons.Filled.Person,
                )

                Load.Failed -> MessageState(
                    title = stringResource(R.string.phone_error_title),
                    body = stringResource(R.string.phone_error_body),
                    actionLabel = stringResource(R.string.state_retry),
                    onAction = onRetry,
                    icon = Icons.Filled.Warning,
                )

                is Load.Ready -> Column(Modifier.fillMaxSize()) {
                    if (staleMarker) StaleMarker(state, onRetry)
                    Box(Modifier.weight(1f).fillMaxWidth()) { content(state.data) }
                }
            }
        }
    }
}

private const val LOAD_FADE_MS = 200

/** A refresh that failed under the minutes before which an age is not worth saying: the marker then only says it could not refresh. */
private const val STALE_AGE_MS = 3 * 60_000L

/**
 * Shown above data that is on screen although the last refresh failed: "Couldn't refresh", with how long ago it was fetched once that is a few
 * minutes (the age of a copy read from the disk is not known), and a button to try again. Nothing when the data is up to date. It is a live
 * region, so a screen reader announces it when it appears (a pull to refresh that failed says so).
 */
@Composable
fun StaleMarker(load: Load<*>, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val ready = load as? Load.Ready<*> ?: return
    if (!ready.refreshFailed) return
    val now = System.currentTimeMillis()
    val age = ready.ageMs(now)
    val text = if (age != null && age >= STALE_AGE_MS) {
        val ago = android.text.format.DateUtils.getRelativeTimeSpanString(ready.loadedAtMs, now, android.text.format.DateUtils.MINUTE_IN_MILLIS).toString()
        stringResource(R.string.phone_stale_ago, ago)
    } else {
        stringResource(R.string.phone_stale_failed)
    }
    Row(
        modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(start = 16.dp, end = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
        androidx.compose.material3.TextButton(onClick = onRetry) { Text(stringResource(R.string.state_retry)) }
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
