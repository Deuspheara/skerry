package com.twitchplayer.tv.core.data.repository

import android.app.ActivityManager
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.twitchplayer.tv.core.data.model.AnimatedEmoteLimit
import com.twitchplayer.tv.core.data.model.AppSettings
import com.twitchplayer.tv.core.data.model.ChatDensity
import com.twitchplayer.tv.core.data.model.ChatMode
import com.twitchplayer.tv.core.data.model.ChatSettings
import com.twitchplayer.tv.core.data.model.ChatTextSize
import com.twitchplayer.tv.core.data.model.PlayerEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore("settings")

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setChatMode(mode: ChatMode)
    suspend fun updateChat(transform: (ChatSettings) -> ChatSettings)
    suspend fun setAnimations(enabled: Boolean)
    suspend fun setPlayerEngine(engine: PlayerEngine)
    suspend fun setPreferredQuality(id: String?)
    suspend fun setShowStats(enabled: Boolean)
    suspend fun setFollowRaids(enabled: Boolean)
    suspend fun addRecentSearch(query: String)

    /** Twitch has no category-follow API: this is a local favourite. */
    suspend fun toggleCategory(id: String)
}

class DataStoreSettingsRepository(
    private val store: DataStore<Preferences>,
    /** What chat settings start from (and fall back to). Low-memory TVs start with fewer animated emotes. */
    private val chatDefaults: ChatSettings = ChatSettings(),
) : SettingsRepository {
    constructor(context: Context) : this(
        context.applicationContext.settingsStore,
        if (context.getSystemService(ActivityManager::class.java).isLowRamDevice) ChatSettings(animatedLimit = AnimatedEmoteLimit.Few) else ChatSettings(),
    )

    override val settings: Flow<AppSettings> = store.data.map { prefs ->
        AppSettings(
            chatMode = prefs[CHAT_MODE]?.let { runCatching { ChatMode.valueOf(it) }.getOrNull() } ?: ChatMode.Compact,
            chat = prefs.toChatSettings(),
            animationsEnabled = prefs[ANIMATIONS] ?: true,
            recentSearches = prefs[RECENTS]?.split(SEPARATOR)?.filter(String::isNotBlank).orEmpty(),
            followedCategories = prefs[CATEGORIES].orEmpty(),
            playerEngine = prefs[PLAYER_ENGINE].toEnum() ?: PlayerEngine.buildDefault(),
            preferredQuality = prefs[PREFERRED_QUALITY]?.takeIf { it.isNotBlank() },
            showStats = prefs[SHOW_STATS] ?: false,
            followRaids = prefs[FOLLOW_RAIDS] ?: false,
        )
    }

    override suspend fun setChatMode(mode: ChatMode) {
        store.edit { it[CHAT_MODE] = mode.name }
    }

    override suspend fun updateChat(transform: (ChatSettings) -> ChatSettings) {
        store.edit { prefs ->
            val next = transform(prefs.toChatSettings())
            prefs[CHAT_TEXT_SIZE] = next.textSize.name
            prefs[CHAT_DENSITY] = next.density.name
            prefs[CHAT_OPACITY] = next.backgroundOpacity
            prefs[CHAT_BADGES] = next.showBadges
            prefs[CHAT_TIMESTAMPS] = next.showTimestamps
            prefs[CHAT_ANIMATED] = next.animatedEmotes
            prefs[CHAT_ANIMATED_LIMIT] = next.animatedLimit.name
        }
    }

    override suspend fun setAnimations(enabled: Boolean) {
        store.edit { it[ANIMATIONS] = enabled }
    }

    override suspend fun setPlayerEngine(engine: PlayerEngine) {
        store.edit { it[PLAYER_ENGINE] = engine.name }
    }

    override suspend fun setPreferredQuality(id: String?) {
        store.edit { if (id == null) it.remove(PREFERRED_QUALITY) else it[PREFERRED_QUALITY] = id }
    }

    override suspend fun setShowStats(enabled: Boolean) {
        store.edit { it[SHOW_STATS] = enabled }
    }

    override suspend fun setFollowRaids(enabled: Boolean) {
        store.edit { it[FOLLOW_RAIDS] = enabled }
    }

    override suspend fun addRecentSearch(query: String) {
        val clean = query.trim().replace(SEPARATOR, " ")
        if (clean.isEmpty()) return
        store.edit { prefs ->
            val current = prefs[RECENTS]?.split(SEPARATOR).orEmpty().filter { it.isNotBlank() && it != clean }
            prefs[RECENTS] = (listOf(clean) + current).take(MAX_RECENTS).joinToString(SEPARATOR)
        }
    }

    override suspend fun toggleCategory(id: String) {
        store.edit { prefs ->
            val current = prefs[CATEGORIES].orEmpty()
            prefs[CATEGORIES] = if (id in current) current - id else current + id
        }
    }

    /** Unknown or missing values fall back to the defaults, so an older or damaged store never breaks chat. */
    private fun Preferences.toChatSettings(): ChatSettings {
        val d = chatDefaults
        return ChatSettings(
            textSize = this[CHAT_TEXT_SIZE].toEnum() ?: d.textSize,
            density = this[CHAT_DENSITY].toEnum() ?: d.density,
            backgroundOpacity = (this[CHAT_OPACITY] ?: d.backgroundOpacity).coerceIn(ChatSettings.BACKGROUND_OPACITIES.min(), 100),
            showBadges = this[CHAT_BADGES] ?: d.showBadges,
            showTimestamps = this[CHAT_TIMESTAMPS] ?: d.showTimestamps,
            animatedEmotes = this[CHAT_ANIMATED] ?: d.animatedEmotes,
            animatedLimit = this[CHAT_ANIMATED_LIMIT].toEnum() ?: d.animatedLimit,
        )
    }

    private inline fun <reified E : Enum<E>> String?.toEnum(): E? = this?.let { name -> enumValues<E>().firstOrNull { it.name == name } }

    private companion object {
        val CHAT_TEXT_SIZE = stringPreferencesKey("chat_text_size")
        val CHAT_DENSITY = stringPreferencesKey("chat_density")
        val CHAT_OPACITY = intPreferencesKey("chat_opacity")
        val CHAT_BADGES = booleanPreferencesKey("chat_badges")
        val CHAT_TIMESTAMPS = booleanPreferencesKey("chat_timestamps")
        val CHAT_ANIMATED = booleanPreferencesKey("chat_animated_emotes")
        val CHAT_ANIMATED_LIMIT = stringPreferencesKey("chat_animated_limit")
        val CHAT_MODE = stringPreferencesKey("chat_mode")
        val ANIMATIONS = booleanPreferencesKey("animations")
        val PLAYER_ENGINE = stringPreferencesKey("player_engine")
        val PREFERRED_QUALITY = stringPreferencesKey("preferred_quality")
        val SHOW_STATS = booleanPreferencesKey("show_stats")
        val FOLLOW_RAIDS = booleanPreferencesKey("follow_raids")
        val RECENTS = stringPreferencesKey("recent_searches")
        val CATEGORIES = stringSetPreferencesKey("followed_categories")
        const val SEPARATOR = "\n"
        const val MAX_RECENTS = 4
    }
}
