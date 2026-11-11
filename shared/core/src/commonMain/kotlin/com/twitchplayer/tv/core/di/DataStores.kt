package com.twitchplayer.tv.core.di

import org.koin.core.qualifier.named

/** Qualifiers of the two preference stores each platform provides. */
object DataStores {
    val Settings = named("settings")
    val VodProgress = named("vod_progress")
}
