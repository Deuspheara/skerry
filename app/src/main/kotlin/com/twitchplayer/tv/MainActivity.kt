package com.twitchplayer.tv

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.twitchplayer.tv.app.TwitchTvApp
import com.twitchplayer.tv.core.data.model.AppSettings
import com.twitchplayer.tv.core.data.repository.SettingsRepository
import com.twitchplayer.tv.core.designsystem.theme.TvTheme
import com.twitchplayer.tv.core.ui.AppLocales
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val settings: SettingsRepository by inject()

    // Before Android 13 the in-app language is applied here; from 13 on the platform does it.
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(AppLocales.wrap(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val current by settings.settings.collectAsState(initial = AppSettings())
            TvTheme(animationsEnabled = current.animationsEnabled) {
                TwitchTvApp(onQuit = ::finish)
            }
        }
    }
}
