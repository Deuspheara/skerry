package com.twitchplayer.tv.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

private val TvColorScheme = darkColorScheme(
    primary = TvColors.Purple,
    onPrimary = TvColors.Text,
    secondary = TvColors.Focus,
    background = TvColors.Body,
    onBackground = TvColors.Text,
    surface = TvColors.Base,
    onSurface = TvColors.Text,
    surfaceVariant = TvColors.Alt2,
    onSurfaceVariant = TvColors.TextSecondary,
    border = TvColors.Divider,
)

@Composable
fun TvTheme(animationsEnabled: Boolean = true, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val motion = remember(animationsEnabled) {
        TvMotion(enabled = animationsEnabled && !systemAnimationsDisabled(context))
    }
    CompositionLocalProvider(LocalMotion provides motion) {
        MaterialTheme(colorScheme = TvColorScheme, content = content)
    }
}
