package com.twitchplayer.tv.core.di

import com.twitchplayer.tv.feature.player.data.sharedChatModule
import com.twitchplayer.tv.feature.player.data.stream.sharedStreamModule
import org.koin.core.module.Module

/**
 * The production Koin graph, in load order. Each app (`TwitchPlayerApp`) starts Koin with exactly this list and the graph tests verify
 * exactly this list, so a module cannot be present in the tests and forgotten at startup (an unregistered presenter factory
 * fails at the first `koinViewModel()`, and R8 then removes everything after the failing call).
 */
fun productionModules(config: PlatformConfig): List<Module> = listOf(
    sharedCoreModule(),
    sharedStreamModule(),
    sharedChatModule(),
    sharedPresentersModule(),
    coreModule(config),
    viewModelModule,
)
