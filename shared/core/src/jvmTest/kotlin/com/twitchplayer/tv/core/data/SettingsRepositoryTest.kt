package com.twitchplayer.tv.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.twitchplayer.tv.core.data.model.AnimatedEmoteLimit
import com.twitchplayer.tv.core.data.model.ChatMode
import com.twitchplayer.tv.core.data.model.ChatSettings
import com.twitchplayer.tv.core.data.model.ChatTextSize
import com.twitchplayer.tv.core.data.model.PlayerEngine
import com.twitchplayer.tv.core.data.repository.DataStoreSettingsRepository
import com.twitchplayer.tv.core.platform.AppConfig
import com.twitchplayer.tv.core.platform.SettingsDefaults
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsRepositoryTest {
    private val folder = createTempDirectory("settings").toFile()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @AfterTest fun stop() {
        scope.cancel()
        folder.deleteRecursively()
    }

    private var files = 0

    private fun store(): DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) { File(folder, "settings${files++}.preferences_pb") }

    @Test fun lowEndDevicesStartWithFewAnimatedEmotesThroughSettingsDefaults() = runBlocking {
        val lowEnd = SettingsDefaults(ChatSettings(animatedLimit = AnimatedEmoteLimit.Few))
        val settings = DataStoreSettingsRepository(store(), chatDefaults = lowEnd.chat).settings.first()
        assertEquals(AnimatedEmoteLimit.Few, settings.chat.animatedLimit)
        assertEquals(AnimatedEmoteLimit.Some, DataStoreSettingsRepository(store()).settings.first().chat.animatedLimit)
    }

    @Test fun theBuildsPlayerEngineIsTheDefaultUntilTheViewerChoosesOne() = runBlocking {
        val config = AppConfig(clientId = "cid", defaultPlayerEngine = PlayerEngine.WebView)
        val repo = DataStoreSettingsRepository(store(), defaultPlayerEngine = config.defaultPlayerEngine)
        assertEquals(PlayerEngine.WebView, repo.settings.first().playerEngine)
        repo.setPlayerEngine(PlayerEngine.Native)
        assertEquals(PlayerEngine.Native, repo.settings.first().playerEngine)
    }

    @Test fun unknownStoredValuesFallBackToTheDefaults() = runBlocking {
        val store = store()
        store.edit {
            it[stringPreferencesKey("chat_text_size")] = "Gigantic"
            it[stringPreferencesKey("chat_mode")] = "Sideways"
            it[stringPreferencesKey("player_engine")] = "Quantum"
        }
        val settings = DataStoreSettingsRepository(store, defaultPlayerEngine = PlayerEngine.WebView).settings.first()
        assertEquals(ChatTextSize.Medium, settings.chat.textSize)
        assertEquals(ChatMode.Compact, settings.chatMode)
        assertEquals(PlayerEngine.WebView, settings.playerEngine)
    }
}
