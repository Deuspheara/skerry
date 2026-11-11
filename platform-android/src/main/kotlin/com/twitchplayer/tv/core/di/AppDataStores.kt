package com.twitchplayer.tv.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.twitchplayer.tv.core.platform.StorageNames

/** The two preference files, created once per process by the DataStore delegates. */
internal val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(StorageNames.SETTINGS_DATASTORE)
internal val Context.vodProgressStore: DataStore<Preferences> by preferencesDataStore(StorageNames.VOD_PROGRESS_DATASTORE)
