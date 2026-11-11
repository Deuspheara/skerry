package com.twitchplayer.tv.core.ui

import android.app.Activity
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Keeps the TV from going to sleep (or starting its screensaver) while this is composed. Playback through the WebView
 * player produces no wake lock of its own, so without this the TV's idle timer fires in the middle of a stream.
 */
@Composable
fun KeepScreenOn(enabled: Boolean = true) {
    val context = LocalContext.current
    DisposableEffect(enabled, context) {
        var base = context
        while (base is ContextWrapper && base !is Activity) base = base.baseContext
        val window = (base as? Activity)?.window
        if (enabled) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { if (enabled) window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}
