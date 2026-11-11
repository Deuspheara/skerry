package com.twitchplayer.tv.core.di

import com.twitchplayer.tv.core.platform.StorageNames
import org.koin.core.qualifier.named

/** Qualifiers of the two preference stores each platform provides. */
object DataStores {
    val Settings = named(StorageNames.SETTINGS_DATASTORE)
    val VodProgress = named(StorageNames.VOD_PROGRESS_DATASTORE)
}
