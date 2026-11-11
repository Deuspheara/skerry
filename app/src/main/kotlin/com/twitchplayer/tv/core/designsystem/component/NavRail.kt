package com.twitchplayer.tv.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.twitchplayer.tv.core.designsystem.theme.LocalMotion
import com.twitchplayer.tv.core.designsystem.theme.TvColors
import com.twitchplayer.tv.core.designsystem.theme.TvDimens
import com.twitchplayer.tv.core.designsystem.theme.TvShapes
import com.twitchplayer.tv.core.designsystem.theme.TvType
import com.twitchplayer.tv.core.ui.TvFocusable
import com.twitchplayer.tv.core.ui.drawFocusRing

/**
 * Makes the rail need two Left presses from the content: the first stops at the content's edge (focus does not move),
 * the next, as a separate key press, enters the rail. Fed with every key press by [onKey] (from a preview handler).
 */
class RailEdgeGuard {
    private var pressId = 0
    private var blockedPress = -1

    fun onKey(event: androidx.compose.ui.input.key.KeyEvent) {
        if (event.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return
        if (event.key == androidx.compose.ui.input.key.Key.DirectionLeft) {
            val now = android.os.SystemClock.uptimeMillis()
            gapBeforeLeft = now - lastLeftAt
            lastLeftAt = now
            if (event.nativeKeyEvent.repeatCount == 0) pressId++
        } else {
            blockedPress = -1
        }
    }

    /**
     * True when this entry attempt may go through: a separate press after the blocked one, or the same press held
     * at the edge for [HOLD_MS]. The first attempt (and its quick repeats) is blocked.
     */
    internal fun allowEntry(): Boolean {
        val now = android.os.SystemClock.uptimeMillis()
        // A press after a pause is a deliberate "go to the sidebar": only Lefts that follow each other fast (scrolling
        // the row, or a held key) stop at the edge first.
        if (blockedPress == -1 && gapBeforeLeft > SCROLL_GAP_MS) return true
        if (blockedPress == -1) { blockedPress = pressId; blockedAt = now; return false }
        if (blockedPress == pressId) {
            if (now - blockedAt < HOLD_MS) return false
        }
        blockedPress = -1
        return true
    }

    private var blockedAt = 0L
    private var lastLeftAt = 0L
    private var gapBeforeLeft = Long.MAX_VALUE

    private companion object { const val HOLD_MS = 600L
        const val SCROLL_GAP_MS = 450L }
}

@Immutable
data class NavRailItem(val icon: ImageVector, val description: String, val tint: Color? = null)

/**
 * Persistent rail: icons only while the content has focus, expanded with labels while the rail has focus.
 * The expanded panel is drawn over the content (the rail keeps its collapsed width in layout, so nothing reflows).
 * Entering the rail from the content lands on the selected item.
 * [selectedIndex] may be -1 (nothing highlighted, e.g. the dock opened over the player): the first item takes focus.
 */
@Composable
fun NavRail(
    items: List<NavRailItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** Attached to the item that takes focus on entry (the selected one, or the first when nothing is selected). */
    selectedRequester: FocusRequester = remember { FocusRequester() },
    /** Never expands, even focused (the dock over the player: icons only, so nothing grows or flickers when it opens). */
    compactOnly: Boolean = false,
    edgeGuard: RailEdgeGuard? = null,
) {
    var hasFocus by remember { mutableStateOf(false) }
    val expanded = !compactOnly && hasFocus
    val motion = LocalMotion.current
    val open by animateFloatAsState(if (expanded) 1f else 0f, if (expanded) motion.fast() else motion.exit(), label = "rail")
    val collapsedFraction = TvDimens.RailWidth / TvDimens.RailExpandedWidth
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    Box(
        modifier
            .zIndex(1f)
            .width(TvDimens.RailWidth)
            .fillMaxHeight(),
    ) {
        // Panel: full expanded width in layout, scaled down to the rail width when collapsed (draw-only animation).
        Box(
            Modifier
                .wrapContentWidth(Alignment.Start, unbounded = true)
                .width(TvDimens.RailExpandedWidth + TvDimens.RailShade)
                .fillMaxHeight()
                .graphicsLayer {
                    transformOrigin = TransformOrigin(if (rtl) 1f else 0f, 0.5f)
                    scaleX = collapsedFraction + (1f - collapsedFraction) * open
                }
                .drawBehind {
                    // Drawn for left-to-right, mirrored for right-to-left (the rail then sits on the right).
                    if (rtl) scale(-1f, 1f) { drawPanel(open) } else drawPanel(open)
                },
        )
        Column(
            Modifier
                .align(Alignment.CenterStart)
                .wrapContentWidth(Alignment.Start, unbounded = true)
                .padding(start = ITEM_INSET)
                .onFocusChanged { hasFocus = it.hasFocus }
                .railEnter(edgeGuard) { selectedRequester }
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items.forEachIndexed { index, item ->
                val active = index == selectedIndex
                // Expanded: room on both sides for the ring (screen edge on one, the panel divider on the other).
                val itemWidth = if (expanded) TvDimens.RailExpandedWidth - ITEM_INSET - EXPANDED_END else TvDimens.RailItem
                Box(Modifier.height(TvDimens.RailItem).width(itemWidth)) {
                    if (active) {
                        Box(
                            Modifier
                                .align(Alignment.CenterStart)
                                .offset(x = (-6).dp)
                                .size(width = 2.dp, height = 24.dp)
                                .background(TvColors.Purple),
                        )
                    }
                    TvFocusable(
                        onClick = { onSelect(index) },
                        shape = TvShapes.Rail,
                        description = item.description,
                        // A wide row does not grow: the ring and the fill carry its focus.
                        focusScale = if (expanded) 1f else TvDimens.FocusScale,
                        drawRing = false,
                        focusAllowed = hasFocus,
                        modifier = Modifier
                            .height(TvDimens.RailItem)
                            .width(itemWidth)
                            .then(if (index == maxOf(selectedIndex, 0)) Modifier.focusRequester(selectedRequester) else Modifier)
                            .semantics { selected = active },
                    ) { focus ->
                        // The pill (selected fill, focus fill and ring) grows from the icon square to the full row with the
                        // panel: the row is laid out at its width at once, only its drawing follows `open`.
                        Box(
                            Modifier.matchParentSize().drawBehind {
                                val item = TvDimens.RailItem.toPx()
                                val w = item + (size.width - item) * open.coerceIn(0f, 1f)
                                val f = focus.value
                                val left = if (rtl) size.width - w else 0f
                                val radius = CornerRadius(TvShapes.Rail.topStart.toPx(size, this))
                                // Selected keeps its fill; any other row gets a quiet tonal fill as it takes focus.
                                val fill = if (active) 1f else f
                                if (fill > 0f) drawRoundRect(TvColors.Alt2, Offset(left, 0f), Size(w, size.height), radius, alpha = fill)
                                if (f > 0f) drawFocusRing(TvShapes.Rail, f, w, fromEnd = rtl)
                            },
                        )
                        Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(TvDimens.RailItem), contentAlignment = Alignment.Center) {
                                Icon(
                                    item.icon, null,
                                    tint = if (active) TvColors.Purple else item.tint ?: TvColors.TextSecondary,
                                    modifier = Modifier.size(TvDimens.RailIcon),
                                )
                            }
                            // Always composed (opening the rail then only re-measures it), faded in with the panel.
                            Text(
                                item.description,
                                style = TvType.Label,
                                color = if (active) TvColors.Text else TvColors.TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 4.dp, end = 12.dp).graphicsLayer { alpha = open },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Suppress("DEPRECATION")
private fun Modifier.railEnter(guard: RailEdgeGuard?, target: () -> FocusRequester): Modifier = focusProperties {
    enter = { dir ->
        when {
            dir == FocusDirection.Left && guard != null && !guard.allowEntry() -> FocusRequester.Cancel
            dir in RAIL_DPAD -> target()
            else -> FocusRequester.Default
        }
    }
}

private val RAIL_DPAD = setOf(FocusDirection.Left, FocusDirection.Right, FocusDirection.Up, FocusDirection.Down)
private val ITEM_INSET = (TvDimens.RailWidth - TvDimens.RailItem) / 2
private val EXPANDED_END = 14.dp

/** Rail panel (left-to-right geometry): solid panel with its divider, then a shade over the content while expanded. */
private fun DrawScope.drawPanel(open: Float) {
    val panel = size.width * TvDimens.RailExpandedWidth.value / (TvDimens.RailExpandedWidth + TvDimens.RailShade).value
    if (open > 0f) {
        drawRect(
            Brush.horizontalGradient(0f to TvColors.Body.copy(alpha = 0.92f * open), 0.25f to TvColors.Body.copy(alpha = 0.7f * open), 1f to TvColors.Body.copy(alpha = 0.6f * open), startX = panel, endX = size.width),
            Offset(panel, 0f), Size(size.width - panel, size.height),
        )
    }
    drawRect(TvColors.Base, Offset.Zero, Size(panel, size.height))
    drawRect(TvColors.Divider, Offset(panel - 1f, 0f), Size(1f, size.height))
}
