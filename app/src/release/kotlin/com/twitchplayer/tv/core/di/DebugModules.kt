package com.twitchplayer.tv.core.di

import android.content.Context
import org.koin.core.module.Module

/** Release builds ship no demo data. */
object DebugModules {
    @Suppress("UNUSED_PARAMETER")
    fun registerImageComponents(builder: coil3.ComponentRegistry.Builder) = Unit

    fun overrides(context: Context): List<Module> = emptyList()
}
