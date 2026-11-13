package com.twitchplayer.phone.ui.theme

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color

// Dark: Twitch purple lightened so text and icons reach AA on the near-black surfaces; the dark label colour on the
// filled button reaches 6:1.
internal val DarkScheme = darkColorScheme(
    primary = Color(0xFFA970FF),
    onPrimary = Color(0xFF1B0B3A),
    primaryContainer = Color(0xFF3D1F7A),
    onPrimaryContainer = Color(0xFFE9DDFF),
    secondary = Color(0xFFC8C8D0),
    onSecondary = Color(0xFF18181B),
    tertiary = Color(0xFFBF94FF),
    background = Color(0xFF0E0E10),
    onBackground = Color(0xFFEFEFF1),
    surface = Color(0xFF0E0E10),
    onSurface = Color(0xFFEFEFF1),
    surfaceVariant = Color(0xFF26262C),
    onSurfaceVariant = Color(0xFFC8C8D0),
    surfaceContainerLowest = Color(0xFF0A0A0B),
    surfaceContainerLow = Color(0xFF18181B),
    surfaceContainer = Color(0xFF1F1F23),
    surfaceContainerHigh = Color(0xFF26262C),
    surfaceContainerHighest = Color(0xFF303032),
    outline = Color(0xFF6F6F7A),
    outlineVariant = Color(0xFF3A3A40),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

internal val LightScheme = lightColorScheme(
    primary = Color(0xFF772CE8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEBDDFF),
    onPrimaryContainer = Color(0xFF2A0F5C),
    secondary = Color(0xFF55555E),
    onSecondary = Color.White,
    tertiary = Color(0xFF6A3DC4),
    background = Color(0xFFFAF9FC),
    onBackground = Color(0xFF18181B),
    surface = Color(0xFFFAF9FC),
    onSurface = Color(0xFF18181B),
    surfaceVariant = Color(0xFFE6E4EC),
    onSurfaceVariant = Color(0xFF4A4A53),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF4F2F8),
    surfaceContainer = Color(0xFFEEECF3),
    surfaceContainerHigh = Color(0xFFE8E6EE),
    surfaceContainerHighest = Color(0xFFE2E0E9),
    outline = Color(0xFF777782),
    outlineVariant = Color(0xFFC9C7D1),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
)

@Composable
fun PhoneTheme(mode: ThemeMode, reducedMotion: Boolean, content: @Composable () -> Unit) {
    val dark = mode.isDark(isSystemInDarkTheme())
    val scheme: ColorScheme = if (dark) DarkScheme else LightScheme
    val activity = LocalActivity.current as? ComponentActivity
    // Transparent bars with icons that match the chosen scheme (not only the system one).
    DisposableEffect(activity, dark) {
        val style = if (dark) {
            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        }
        activity?.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        onDispose {}
    }
    CompositionLocalProvider(LocalReducedMotion provides reducedMotion) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
