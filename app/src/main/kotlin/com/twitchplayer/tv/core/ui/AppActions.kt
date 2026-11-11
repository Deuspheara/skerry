package com.twitchplayer.tv.core.ui

import androidx.compose.runtime.staticCompositionLocalOf

/** App-level actions reachable from any screen without threading callbacks through every layer. */
interface AppActions {
    fun signIn()
    fun signOut()
    fun requestQuit()
}

val LocalAppActions = staticCompositionLocalOf<AppActions> {
    object : AppActions {
        override fun signIn() = Unit
        override fun signOut() = Unit
        override fun requestQuit() = Unit
    }
}
