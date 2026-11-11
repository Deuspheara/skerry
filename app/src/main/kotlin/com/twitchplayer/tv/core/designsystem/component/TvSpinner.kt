package com.twitchplayer.tv.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.twitchplayer.tv.core.designsystem.theme.LocalMotion
import com.twitchplayer.tv.core.designsystem.theme.TvColors

/** Loading indicator: rotation runs in the layer phase only; static arc when motion is reduced. */
@Composable
fun TvSpinner(modifier: Modifier = Modifier) {
    val enabled = LocalMotion.current.enabled
    val rotation = if (enabled) {
        rememberInfiniteTransition(label = "spinner").animateFloat(
            initialValue = 0f, targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
            label = "rotation",
        )
    } else null
    Canvas(
        modifier
            .size(40.dp)
            .graphicsLayer { rotationZ = rotation?.value ?: 0f },
    ) {
        val stroke = 3.dp.toPx()
        drawArc(
            color = TvColors.Purple, startAngle = 0f, sweepAngle = 270f, useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
            size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
    }
}
