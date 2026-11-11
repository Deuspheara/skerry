package com.twitchplayer.phone.di

import android.content.Context
import com.twitchplayer.tv.demo.demoModule
import com.twitchplayer.tv.demo.registerDemoImageComponents
import org.koin.core.module.Module
import java.io.File

/**
 * Debug builds can run on deterministic demo data (screenshots, UI tests) without a Twitch account:
 * `adb shell run-as com.twitchplayer.phone touch files/demo` then restart the app. Add `files/demo-offline` (every request fails) or
 * `files/demo-empty` (nothing followed or live) to see those states ([offlineModule], [emptyModule]).
 */
object DebugModules {
    fun registerImageComponents(builder: coil3.ComponentRegistry.Builder) {
        // Added before the network fetcher: the demo emote URLs include a https://static-cdn.jtvnw.net shape.
        registerDemoImageComponents(builder)
    }

    fun overrides(context: Context): List<Module> {
        fun flag(name: String) = File(context.filesDir, name).exists()
        return when {
            !flag("demo") -> emptyList()
            flag("demo-offline") -> listOf(demoModule, offlineModule)
            flag("demo-empty") -> listOf(demoModule, emptyModule)
            else -> listOf(demoModule)
        }
    }
}
