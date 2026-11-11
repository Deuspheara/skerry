package com.twitchplayer.tv.core.designsystem.component

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.twitchplayer.tv.core.designsystem.theme.TvColors
import com.twitchplayer.tv.core.designsystem.theme.TvDimens
import com.twitchplayer.tv.core.designsystem.theme.TvType
import kotlinx.coroutines.delay
import java.util.Date

/**
 * Fixed top strip: content scrolls under a gradient and the clock sits at the right safe margin.
 * Drawn over the scrolling content, never inside it.
 */
@Composable
fun TopBar(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(TvDimens.TopBar + 16.dp)
            // Solid behind the clock, then a short fade: scrolled content never shows through the time.
            .background(Brush.verticalGradient(0.75f to TvColors.Body, 1f to Color.Transparent)),
    ) {
        Clock(Modifier.align(Alignment.TopEnd).padding(top = TvDimens.SafeTop + 5.dp, end = TvDimens.SafeHorizontal))
    }
}

@Composable
private fun Clock(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val format = remember(context) { DateFormat.getTimeFormat(context) }
    val time by produceState(initialValue = format.format(Date())) {
        while (true) {
            value = format.format(Date())
            delay(60_000L - System.currentTimeMillis() % 60_000L)
        }
    }
    Text(time, style = TvType.Clock, color = TvColors.TextSecondary, modifier = modifier.clearAndSetSemantics { })
}

/** Soft bottom fade matching the 27 dp bottom safe margin. */
@Composable
fun BottomFade(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(Brush.verticalGradient(0f to Color.Transparent, 0.5f to TvColors.Body, 1f to TvColors.Body)),
    )
}
