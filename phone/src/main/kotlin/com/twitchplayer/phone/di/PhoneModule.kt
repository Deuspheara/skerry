package com.twitchplayer.phone.di

import com.twitchplayer.phone.ui.theme.SharedPreferencesThemePreference
import com.twitchplayer.phone.ui.theme.ThemePreference
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** What only the phone app binds, next to `productionModules()`. */
val phoneModule = module {
    single<ThemePreference> { SharedPreferencesThemePreference(androidContext()) }
}

/** The phone's whole Koin graph, in load order: the shared production modules plus [phoneModule]. Start-up and the graph tests use this one list. */
fun phoneModules(): List<org.koin.core.module.Module> = com.twitchplayer.tv.core.di.productionModules(com.twitchplayer.phone.phonePlatformConfig()) + phoneModule
