package com.twitchplayer.phone.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.twitchplayer.phone.R
import com.twitchplayer.phone.navigation.PhoneTab

/** Sizes of the app's own navigation chrome. The bar is icon-only: the tab order never changes, and each tab is described for TalkBack. */
@Immutable
object PhoneChrome {
    val BarHeight = 56.dp
    val RailWidth = 72.dp
    val TabTarget = 48.dp
    val IconSelected = 26.dp
    val IconIdle = 24.dp
    val Dot = 4.dp
    val IndicatorWidth = 48.dp
    val Hairline = 0.5.dp
    val FixedTabWidth = 112.dp

    /** Up to this many tabs each keep [FixedTabWidth]; more share the whole width. */
    const val MAX_FIXED_TABS = 3

    /** From this window width the tabs move from a bar under the page to a rail beside it. */
    const val WIDE_WIDTH_DP = 600
}

/** True from [PhoneChrome.WIDE_WIDTH_DP]: a tablet, a foldable, or a phone held sideways. */
@Composable
@ReadOnlyComposable
fun isWideWindow(): Boolean {
    val widthPx = LocalWindowInfo.current.containerSize.width
    return with(LocalDensity.current) { widthPx.toDp() } >= PhoneChrome.WIDE_WIDTH_DP.dp
}

@Composable
@ReadOnlyComposable
private fun chromeColor(): Color = MaterialTheme.colorScheme.surface.copy(alpha = if (MaterialTheme.colorScheme.surface.luminanceIsLow()) BAR_ALPHA_DARK else BAR_ALPHA_LIGHT)

private fun Color.luminanceIsLow() = red + green + blue < 1.5f

/** The tabs under the page: icons only, 56 dp plus the gesture-bar inset, a hairline above. */
@Composable
fun PhoneBottomBar(tabs: List<PhoneTab>, selected: PhoneTab, onSelect: (PhoneTab) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().background(chromeColor()).windowInsetsPadding(WindowInsets.navigationBars)) {
        Box(Modifier.fillMaxWidth().height(PhoneChrome.Hairline).background(MaterialTheme.colorScheme.outlineVariant))
        // Two or three tabs keep a comfortable size and sit in the middle; a bar of four or more shares the width.
        Row(Modifier.fillMaxWidth().height(PhoneChrome.BarHeight), horizontalArrangement = Arrangement.Center) {
            tabs.forEach { tab ->
                val width = if (tabs.size > PhoneChrome.MAX_FIXED_TABS) Modifier.weight(1f) else Modifier.width(PhoneChrome.FixedTabWidth)
                TabItem(tab, tab == selected, { onSelect(tab) }, width.fillMaxHeight())
            }
        }
    }
}

/** The same tabs in a column beside the page (wide windows). */
@Composable
fun PhoneRail(tabs: List<PhoneTab>, selected: PhoneTab, onSelect: (PhoneTab) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxHeight().background(chromeColor())) {
        Column(
            Modifier.width(PhoneChrome.RailWidth).fillMaxHeight().windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical)).padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            tabs.forEach { tab -> TabItem(tab, tab == selected, { onSelect(tab) }, Modifier.size(PhoneChrome.BarHeight)) }
        }
        Box(Modifier.width(PhoneChrome.Hairline).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
    }
}

@Composable
private fun TabItem(tab: PhoneTab, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(tab.label())
    val tint = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = PhoneChrome.TabTarget / 2),
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            val size = if (selected) PhoneChrome.IconSelected else PhoneChrome.IconIdle
            // The selected tab is told by more than a dot: a soft pill behind its filled icon.
            val pill = if (selected) Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(percent = 50)) else Modifier
            Box(Modifier.size(width = PhoneChrome.IndicatorWidth, height = PhoneChrome.IconSelected + 6.dp).then(pill), contentAlignment = Alignment.Center) {
                Icon(if (selected) tab.filledIcon() else tab.outlinedIcon(), contentDescription = null, tint = tint, modifier = Modifier.size(size))
            }
            Box(Modifier.size(PhoneChrome.Dot).clip(CircleShape).background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent))
        }
    }
}

private fun PhoneTab.filledIcon(): ImageVector = when (this) {
    PhoneTab.Home -> Icons.Filled.Home
    PhoneTab.Discover -> PhoneIcons.DiscoverFilled
    PhoneTab.Following -> Icons.Filled.Favorite
    PhoneTab.Browse -> PhoneIcons.BrowseFilled
}

private fun PhoneTab.outlinedIcon(): ImageVector = when (this) {
    PhoneTab.Home -> Icons.Outlined.Home
    PhoneTab.Discover -> PhoneIcons.Discover
    PhoneTab.Following -> Icons.Outlined.FavoriteBorder
    PhoneTab.Browse -> PhoneIcons.Browse
}

/** The tab's name: spoken by TalkBack, no longer drawn. */
private fun PhoneTab.label(): Int = when (this) {
    PhoneTab.Home -> R.string.nav_home
    PhoneTab.Discover -> R.string.phone_nav_discover
    PhoneTab.Following -> R.string.sec_following
    PhoneTab.Browse -> R.string.nav_browse
}

private const val BAR_ALPHA_DARK = 0.94f
private const val BAR_ALPHA_LIGHT = 0.96f
