package com.twitchplayer.phone

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import com.twitchplayer.phone.feature.player.ActivityPlayerHost
import com.twitchplayer.phone.feature.player.LocalPlayerHost
import com.twitchplayer.phone.ui.PhoneRoot
import com.twitchplayer.tv.core.ui.AppLocales

class MainActivity : ComponentActivity() {
    private val playerHost by lazy(LazyThreadSafetyMode.NONE) { ActivityPlayerHost(this) }

    /** Before Android 13 the in-app language is applied here; from 13 on the system's per-app language does it. */
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(AppLocales.wrap(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CompositionLocalProvider(LocalPlayerHost provides playerHost) { PhoneRoot() } }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        playerHost.onUserLeaveHint()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        playerHost.onPictureInPictureModeChanged(isInPictureInPictureMode)
    }
}
