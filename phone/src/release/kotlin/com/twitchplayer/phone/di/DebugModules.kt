package com.twitchplayer.phone.di

import android.content.Context
import org.koin.core.module.Module

/** Release builds ship no demo data. */
object DebugModules {
    @Suppress("UNUSED_PARAMETER")
    fun registerImageComponents(builder: coil3.ComponentRegistry.Builder) = Unit

    @Suppress("UNUSED_PARAMETER")
    fun overrides(context: Context): List<Module> = emptyList()
}
