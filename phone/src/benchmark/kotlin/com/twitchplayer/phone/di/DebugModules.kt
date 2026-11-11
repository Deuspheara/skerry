package com.twitchplayer.phone.di

import android.content.Context
import com.twitchplayer.tv.demo.demoModule
import com.twitchplayer.tv.demo.registerDemoImageComponents
import org.koin.core.module.Module

/** The benchmark build always runs on the fictional demo data (no account, no network), so every measurement sees the same screens. */
object DebugModules {
    fun registerImageComponents(builder: coil3.ComponentRegistry.Builder) {
        registerDemoImageComponents(builder)
    }

    @Suppress("UNUSED_PARAMETER")
    fun overrides(context: Context): List<Module> = listOf(demoModule)
}
