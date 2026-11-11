package com.twitchplayer.tv.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.twitchplayer.tv.core.designsystem.theme.LocalMotion
import com.twitchplayer.tv.core.designsystem.theme.TvDimens
import kotlinx.coroutines.launch

/**
 * Base for every D-pad focusable surface: animated scale on the whole element, caller modifier included (draw-phase
 * scale: no layout shift, and bring-into-view sees the resting bounds), optional ring, and a [FocusProgress] handed to content for extra focus visuals.
 * The animation state is only read in draw/layer phases: focus movement does not recompose.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvFocusable(
    onClick: () -> Unit,
    shape: CornerBasedShape,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    role: Role = Role.Button,
    focusScale: Float = TvDimens.FocusScale,
    drawRing: Boolean = true,
    ringStrength: Float = 1f,
    clipContent: Boolean = false,
    /**
     * A second opinion on focus from a parent that tracks it reliably. Compose can leave a node's own focus state stale
     * (true) after a cold start where the window was not focused yet; the parent group's state is correct, so the
     * element only shows focus while both agree.
     */
    focusAllowed: Boolean = true,
    /** Drawn over the element (alpha scaled by the press amount) while OK is held down: a short tonal response. */
    pressOverlay: Color = Color.Unspecified,
    content: @Composable BoxScope.(FocusProgress) -> Unit,
) {
    val motion = LocalMotion.current
    val scope = rememberCoroutineScope()
    val anim = remember { Animatable(0f) }
    val press = remember { Animatable(0f) }
    val progress = remember { FocusProgress({ anim.value }, { press.value }) }
    val interaction = remember { MutableInteractionSource() }
    if (pressOverlay.isSpecified) {
        LaunchedEffect(interaction) {
            interaction.interactions.collectLatest {
                when (it) {
                    is PressInteraction.Press -> press.animateTo(1f, motion.press())
                    is PressInteraction.Release, is PressInteraction.Cancel -> press.animateTo(0f, motion.press())
                }
            }
        }
    }
    // A dialog owns focus while it is open: the element under it must not keep its ring behind the scrim.
    val windowFocused = androidx.compose.ui.platform.LocalWindowInfo.current.isWindowFocused
    var focused by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(focused, windowFocused, focusAllowed) {
        val on = focused && windowFocused && focusAllowed
        // Both ways decelerate: the card focus leaves settles while the next one grows, so moving along a row reads
        // as one motion instead of a lag (an ease-in exit keeps the old card large for most of its run).
        anim.animateTo(if (on) 1f else 0f, motion.fast())
    }
    Box(
        Modifier
            // Focused on top of its neighbours: its scale and ring are never covered by the next sibling.
            .zIndex(if (focused && focusAllowed) 1f else 0f)
            // The scale wraps the caller's modifier too, so backgrounds and borders grow with the ring and the content
            // (applied after them, a tile's own background stayed at its resting size inside the scaled ring).
            // Scaled in the draw phase, not with graphicsLayer scale: a layer transform also scales the bounds the
            // focusable reports to bring-into-view, so an anchored list re-scrolled a few px to follow the growing card
            // on every move (the whole Following grid shook). The plain layer below keeps the content cached.
            .drawWithContent {
                // Reduced motion: nothing grows; the element lightens and the ring thickens instead (see focusLift).
                val s = if (motion.enabled) 1f + (focusScale - 1f) * anim.value else 1f
                // Depth: three stacked translucent outlines under the raised element (no blur, no offscreen layer).
                if (motion.enabled && anim.value > 0f) scale(s) { drawFocusShadow(shape, anim.value) }
                scale(s) { this@drawWithContent.drawContent() }
            }
            .graphicsLayer()
            .then(modifier)
            .then(if (motion.enabled) Modifier else Modifier.focusLift(progress, shape))
            .then(if (pressOverlay.isSpecified) Modifier.pressTone(progress, shape, pressOverlay) else Modifier)
            .then(if (drawRing) Modifier.focusRing(progress, shape, ringStrength, thick = !motion.enabled) else Modifier)
            .onFocusChanged { state -> focused = state.isFocused }
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = role,
                onLongClick = onLongClick,
                onClick = onClick,
            )
            .then(if (clipContent) Modifier.clip(shape) else Modifier),
    ) { content(progress) }
}


/** A cheap soft shadow: wider, fainter outlines drawn below the element, sliding down 4 dp as focus settles. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFocusShadow(shape: CornerBasedShape, amount: Float) {
    val dy = 4.dp.toPx() * amount
    for (i in 1..3) {
        val grow = i * 3.dp.toPx() * amount
        val outline = shape.createOutline(androidx.compose.ui.geometry.Size(size.width + 2 * grow, size.height + 2 * grow), layoutDirection, this)
        translate(-grow, dy - grow) {
            drawOutline(outline, Color.Black, alpha = 0.16f * amount / i)
        }
    }
}
