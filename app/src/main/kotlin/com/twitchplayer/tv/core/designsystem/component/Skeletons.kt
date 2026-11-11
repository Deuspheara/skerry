package com.twitchplayer.tv.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.twitchplayer.tv.core.designsystem.theme.TvColors
import com.twitchplayer.tv.core.designsystem.theme.TvDimens
import com.twitchplayer.tv.core.designsystem.theme.TvShapes

/**
 * Loading placeholders: the real cards' geometry in one flat tone that breathes slowly (one shared alpha, see [SkeletonPulse]; still when motion is off). They are never focusable and hidden from accessibility.
 */
/** The tone of the image placeholder behind every real card ([com.twitchplayer.tv.core.ui.TvAsyncImage]): the handover to the loaded card is invisible. */
private val Tone = TvColors.Base

/** One slow breathing value (0.78..1) shared by every block of a skeleton; constant when motion is off. Read in the draw phase only. */
val LocalSkeletonPulse = androidx.compose.runtime.staticCompositionLocalOf<androidx.compose.runtime.State<Float>?> { null }

/** Gives the skeleton blocks inside [content] one shared pulse: a single animation, redrawing only the placeholder shapes. */
@Composable
fun SkeletonPulse(content: @Composable () -> Unit) {
    val motion = com.twitchplayer.tv.core.designsystem.theme.LocalMotion.current
    val pulse: androidx.compose.runtime.State<Float> = if (motion.enabled) {
        androidx.compose.animation.core.rememberInfiniteTransition(label = "skeleton").animateFloat(
            initialValue = 0.78f, targetValue = 1f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                androidx.compose.animation.core.tween(900, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                androidx.compose.animation.core.RepeatMode.Reverse,
            ),
            label = "pulse",
        )
    } else androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    androidx.compose.runtime.CompositionLocalProvider(LocalSkeletonPulse provides pulse) { content() }
}

@Composable
private fun Block(modifier: Modifier, shape: androidx.compose.ui.graphics.Shape = TvShapes.Card) {
    val pulse = LocalSkeletonPulse.current
    Box(
        modifier.then(
            if (pulse == null) Modifier.background(Tone, shape)
            else Modifier.drawBehind {
                drawOutline(shape.createOutline(size, layoutDirection, this), Tone, alpha = pulse.value)
            },
        ),
    )
}

/** Same footprint as [StreamCard]: 16:9 thumbnail, avatar, title and meta lines. */
@Composable
fun SkeletonStreamCard(modifier: Modifier = Modifier, width: Dp? = TvDimens.LiveCardWidth) {
    Column(
        modifier.then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Block(Modifier.fillMaxWidth().aspectRatio(16f / 9f))
        Row(Modifier.padding(end = 4.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Block(Modifier.size(TvDimens.AvatarCard), CircleShape)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Block(Modifier.fillMaxWidth().height(12.dp))
                Block(Modifier.fillMaxWidth(0.7f).height(12.dp))
                Block(Modifier.fillMaxWidth(0.5f).height(10.dp))
            }
        }
    }
}

/** Same footprint as [CategoryCard]: 3:4 poster and a name line. */
@Composable
fun SkeletonCategoryCard(modifier: Modifier = Modifier, width: Dp? = TvDimens.CategoryCardWidth) {
    Column(modifier.then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())) {
        Block(Modifier.fillMaxWidth().aspectRatio(3f / 4f))
        Block(Modifier.padding(start = 2.dp, top = 12.dp).fillMaxWidth(0.6f).height(12.dp))
    }
}

/** A section header (26 dp line) followed by a row of cards that runs off the right edge like [CardRow]. */
@Composable
fun SkeletonSection(modifier: Modifier = Modifier, category: Boolean = false, cards: Int = 7) {
    Column(modifier) {
        Block(Modifier.padding(start = 16.dp, top = 12.dp).size(120.dp, 20.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clipToBounds()
                // Not scrollable and not focusable: the row is only wider than the screen, as the real one is.
                .horizontalScroll(rememberScrollState(), enabled = false)
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(TvDimens.RowGap),
        ) {
            repeat(cards) { if (category) SkeletonCategoryCard() else SkeletonStreamCard() }
        }
    }
}

/** Home: three sections like Following, For you and Categories. */
@Composable
fun SkeletonHome(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().clipToBounds().padding(start = 8.dp, top = TvDimens.TopBar).clearAndSetSemantics { }) {
        SkeletonSection(cards = 5)
        SkeletonSection(cards = 5)
        SkeletonSection(category = true, cards = 8)
    }
}

/** A grid of identical cards laid out with the paddings of the real grids ([columns] wide, [top] below the screen top). */
@Composable
fun SkeletonGrid(
    columns: Int,
    category: Boolean,
    modifier: Modifier = Modifier,
    top: Dp = TvDimens.TopBar,
    showHeader: Boolean = false,
    chips: Boolean = false,
) {
    Column(
        modifier.fillMaxSize().clipToBounds()
            .padding(start = 8.dp, end = TvDimens.ContentEnd - 12.dp)
            .clearAndSetSemantics { },
    ) {
        if (chips) {
            Row(Modifier.padding(start = TvDimens.ContentStart - 8.dp, top = TvDimens.TopBar + 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(5) { Block(Modifier.size(72.dp, 36.dp), TvShapes.Chip) }
            }
        }
        Column(
            Modifier.padding(start = 16.dp, end = 12.dp, top = if (chips) 32.dp else top),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (showHeader) Block(Modifier.padding(top = 12.dp).size(120.dp, 20.dp))
            repeat(if (category) 3 else 2) {
                Row(horizontalArrangement = Arrangement.spacedBy(TvDimens.RowGap)) {
                    repeat(columns) {
                        Box(Modifier.weight(1f)) {
                            if (category) SkeletonCategoryCard(width = null) else SkeletonStreamCard(width = null)
                        }
                    }
                }
            }
        }
    }
}

/** Channel page: avatar, name and action lines, then a section of clip-sized cards. */
@Composable
fun SkeletonChannel(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().clipToBounds().padding(top = TvDimens.SafeTop + 12.dp, start = TvDimens.ContentStart).clearAndSetSemantics { }) {
        Block(Modifier.padding(bottom = 12.dp).size(32.dp), CircleShape)
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Block(Modifier.size(80.dp), CircleShape)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Block(Modifier.size(220.dp, 28.dp))
                Block(Modifier.size(160.dp, 16.dp))
                Block(Modifier.size(420.dp, 14.dp))
                Block(Modifier.padding(top = 4.dp).size(100.dp, 32.dp), TvShapes.Control)
            }
        }
        SkeletonSection(Modifier.padding(top = 8.dp), cards = 5)
    }
}

