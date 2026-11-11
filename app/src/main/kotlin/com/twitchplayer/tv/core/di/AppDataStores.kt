package com.twitchplayer.tv.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/** The two preference files, created once per process by the DataStore delegates. */
internal val Context.settingsStore: DataStore<Preferences> by preferencesDataStore("settings")
internal val Context.vodProgressStore: DataStore<Preferences> by preferencesDataStore("vod_progress")
