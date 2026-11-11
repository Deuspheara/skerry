package com.twitchplayer.tv.core.di

import android.content.Context
import com.twitchplayer.tv.demo.demoModule
import org.koin.core.module.Module

/** The benchmark build (release-like: R8, not debuggable) always runs on the deterministic demo data. */
object DebugModules {
    fun registerImageComponents(builder: coil3.ComponentRegistry.Builder) {
        builder.add(com.twitchplayer.tv.demo.DemoArtFetcher.Factory())
        builder.add(com.twitchplayer.tv.demo.DemoEmoteFetcher.Factory())
    }

    @Suppress("UNUSED_PARAMETER")
    fun overrides(context: Context): List<Module> = listOf(demoModule)
}
