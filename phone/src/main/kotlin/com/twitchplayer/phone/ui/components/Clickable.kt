package com.twitchplayer.phone.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import com.twitchplayer.phone.ui.theme.LocalReducedMotion

private const val PRESSED_SCALE = 0.97f
private const val PRESS_MS = 90

/**
 * A whole row as one button target: announced as a button and never smaller than the 48 dp touch target. While it is pressed the row
 * shrinks a little and springs back (the scale is read in the draw phase only, so a press recomposes nothing); not with reduced motion.
 */
@Composable
fun Modifier.clickableItem(onClick: () -> Unit): Modifier {
    val reduced = LocalReducedMotion.current
    if (reduced) return minimumInteractiveComponentSize().clickable(role = Role.Button, onClick = onClick)
    val source = remember { MutableInteractionSource() }
    val scale = remember { Animatable(1f) }
    LaunchedEffect(source) {
        source.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> scale.animateTo(PRESSED_SCALE, tween(PRESS_MS))
                is PressInteraction.Release, is PressInteraction.Cancel -> scale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
            }
        }
    }
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }.minimumInteractiveComponentSize().clickable(interactionSource = source, indication = LocalIndication.current, role = Role.Button, onClick = onClick)
}
