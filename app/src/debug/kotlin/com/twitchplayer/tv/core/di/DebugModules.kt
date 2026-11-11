package com.twitchplayer.tv.core.di

import android.content.Context
import com.twitchplayer.tv.demo.demoModule
import com.twitchplayer.tv.demo.registerDemoImageComponents
import org.koin.core.module.Module
import java.io.File

/**
 * Debug builds can run on deterministic demo data (screenshots, UI tests, benchmarks) without a Twitch
 * account: `adb shell run-as com.twitchplayer.tv touch files/demo` then restart the app.
 */
object DebugModules {
    fun registerImageComponents(builder: coil3.ComponentRegistry.Builder) {
        // Added before the network fetcher: the demo emote URLs include a https://static-cdn.jtvnw.net shape.
        registerDemoImageComponents(builder)
    }

    fun overrides(context: Context): List<Module> = if (File(context.filesDir, "demo").exists()) listOf(demoModule) else emptyList()
}
