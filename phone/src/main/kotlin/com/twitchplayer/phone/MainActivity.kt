package com.twitchplayer.phone

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.twitchplayer.phone.ui.PhoneRoot
import com.twitchplayer.tv.core.ui.AppLocales

class MainActivity : ComponentActivity() {
    /** Before Android 13 the in-app language is applied here; from 13 on the system's per-app language does it. */
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(AppLocales.wrap(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PhoneRoot() }
    }
}
