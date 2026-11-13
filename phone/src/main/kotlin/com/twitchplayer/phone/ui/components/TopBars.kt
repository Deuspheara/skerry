@file:OptIn(ExperimentalMaterial3Api::class)

package com.twitchplayer.phone.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.twitchplayer.phone.R
import com.twitchplayer.phone.ui.theme.LocalReducedMotion
import kotlin.math.roundToInt

/** What the header of a tab root offers besides its title: the search entry and the account entry. */
@Immutable
class HeaderActions(
    val account: String?,
    val onSearch: () -> Unit,
    val onProfile: () -> Unit,
    /** Names the pill and the Search page's field as one element (one per tab). */
    val morphKey: String = "search-field",
)

/** Heights of the top bars. */
object TopBarSpec {
    val Header = 56.dp
    val Detail = 48.dp
    val SearchPill = 36.dp
    val Avatar = 28.dp
    val Mark = 28.dp
}

/**
 * A bar whose row folds away with the page and comes back on a scroll up (the "enter always" behaviour), without the Material bar:
 * the offset is read in the layout phase only, so scrolling does not recompose it.
 */
@Composable
fun CollapsingBar(behavior: TopAppBarScrollBehavior, contentHeight: Dp, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    // Only the row folds away: the status bar's height stays, so the tabs under it never slide beneath the clock and the icons.
    val contentPx = with(LocalDensity.current) { contentHeight.toPx() }
    SideEffect { behavior.state.heightOffsetLimit = -contentPx }
    Box(
        modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val offset = behavior.state.heightOffset.roundToInt().coerceIn(-contentPx.roundToInt(), 0)
                layout(placeable.width, placeable.height + offset) { placeable.placeRelative(0, 0) }
            }
            .clipToBounds()
            .background(MaterialTheme.colorScheme.background),
        content = content,
    )
}

/**
 * The header of Home and Browse: the app mark, a search pill (it opens Search, it is not a field) and the account. 56 dp under the
 * status bar, on the page background, no elevation tint when the list moves under it.
 */
@Composable
fun HomeHeader(actions: HeaderActions, behavior: TopAppBarScrollBehavior, modifier: Modifier = Modifier) {
    CollapsingBar(behavior, TopBarSpec.Header, modifier) {
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)).height(TopBarSpec.Header).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppMark()
            SearchPill(actions.onSearch, actions.morphKey, Modifier.weight(1f))
            ProfileButton(actions.account, actions.onProfile)
        }
    }
}

@Composable
private fun AppMark() {
    Box(
        Modifier.size(TopBarSpec.Mark).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primary).clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SearchPill(onClick: () -> Unit, morphKey: String, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.nav_search)
    Row(
        modifier
            .searchMorph(morphKey)
            .height(TopBarSpec.SearchPill)
            .clip(RoundedCornerShape(TopBarSpec.SearchPill / 2))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // The magnifier grows with the text beside it (a 18 dp icon next to 2x text looked like a speck).
        Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(with(LocalDensity.current) { 18.sp.toDp() }))
        Text(
            stringResource(R.string.phone_search_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The account: the viewer's initial in a circle, or a person when nobody is signed in. A 48 dp target around the 28 dp circle. */
@Composable
private fun ProfileButton(account: String?, onClick: () -> Unit) {
    val label = stringResource(R.string.tile_account)
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp).semantics { contentDescription = label }) {
        Box(Modifier.size(TopBarSpec.Avatar).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            val initial = account?.firstOrNull()?.uppercaseChar()
            if (initial != null) {
                UnscaledText { Text(initial.toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer) }
            } else {
                Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** A tab root without a search entry (Settings): the title, as a heading. */
@Composable
fun TitleHeader(title: String, behavior: TopAppBarScrollBehavior, modifier: Modifier = Modifier) {
    CollapsingBar(behavior, TopBarSpec.Header, modifier) {
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)).height(TopBarSpec.Header).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
        }
    }
}

/** The row of a detail page (category, channel): back, the title, then the page's own actions. 48 dp, no tonal elevation. */
@Composable
fun DetailHeader(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "",
    behavior: TopAppBarScrollBehavior? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val row: @Composable BoxScope.() -> Unit = {
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)).height(TopBarSpec.Detail).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back)) }
            MarqueeTitle(title, Modifier.weight(1f).padding(horizontal = 4.dp).semantics { heading() })
            actions()
        }
    }
    if (behavior != null) {
        CollapsingBar(behavior, TopBarSpec.Detail, modifier, row)
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.background), content = row)
    }
}

/**
 * A page title on one line. One that does not fit scrolls slowly past (and rests between rounds) so the whole name can be read; with
 * reduced motion it is cut with an ellipsis instead, which never moves.
 */
@Composable
fun MarqueeTitle(text: String, modifier: Modifier = Modifier) {
    val still = LocalReducedMotion.current
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = if (still) TextOverflow.Ellipsis else TextOverflow.Clip,
        modifier = if (still) modifier else modifier.basicMarquee(iterations = Int.MAX_VALUE, repeatDelayMillis = MARQUEE_PAUSE_MS, velocity = MARQUEE_SPEED),
    )
}

private const val MARQUEE_PAUSE_MS = 2_500
private val MARQUEE_SPEED = 24.dp
