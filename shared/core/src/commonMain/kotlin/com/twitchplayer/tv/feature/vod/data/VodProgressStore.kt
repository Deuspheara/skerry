package com.twitchplayer.tv.feature.vod.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.twitchplayer.tv.core.platform.StorageNames
import com.twitchplayer.tv.core.platform.WallClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Where the viewer stopped in one replay. */
data class VodProgress(
    val positionMs: Long,
    val durationMs: Long,
    /** When it was saved (epoch ms, 0 = unknown). */
    val savedAtMs: Long = 0L,
) {
    /** 0..1 of the replay already watched; 0 when the length is unknown. */
    val fraction: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    /** Worth offering "resume": not the first seconds, not the credits. */
    val resumable: Boolean get() = positionMs >= MIN_RESUME_MS && (durationMs <= 0 || positionMs <= durationMs - END_MARGIN_MS)

    companion object {
        const val MIN_RESUME_MS = 15_000L
        const val END_MARGIN_MS = 15_000L
    }
}

/** Last position per replay, kept on the device. Watched-to-the-end replays are forgotten. */
interface VodProgressStore {
    val progress: Flow<Map<String, VodProgress>>

    /** Stores [positionMs]; a position within the last seconds of a known length clears the entry instead. */
    suspend fun save(vodId: String, positionMs: Long, durationMs: Long)
    suspend fun clear(vodId: String)
}

class DataStoreVodProgressStore(
    private val store: DataStore<Preferences>,
    private val maxEntries: Int = MAX_ENTRIES,
    private val clock: WallClock = WallClock.System,
) : VodProgressStore {
    override val progress: Flow<Map<String, VodProgress>> = store.data.map { prefs ->
        buildMap { prefs.asMap().forEach { (key, value) -> decode(key.name, value as? String)?.let { put(it.first, it.second.first.copy(savedAtMs = it.second.second)) } } }
    }

    override suspend fun save(vodId: String, positionMs: Long, durationMs: Long) {
        val progress = VodProgress(positionMs.coerceAtLeast(0), durationMs.coerceAtLeast(0))
        if (durationMs > 0 && positionMs > durationMs - VodProgress.END_MARGIN_MS) return clear(vodId)
        if (positionMs <= 0) return
        store.edit { prefs ->
            prefs[stringPreferencesKey(PREFIX + vodId)] = "${progress.positionMs}:${progress.durationMs}:${clock.nowMillis()}"
            // Bounded: the oldest entries go first.
            val entries = prefs.asMap().mapNotNull { (key, value) -> decode(key.name, value as? String)?.let { key to it.second.second } }
            entries.sortedBy { it.second }.take((entries.size - maxEntries).coerceAtLeast(0)).forEach { prefs.remove(it.first) }
        }
    }

    override suspend fun clear(vodId: String) {
        store.edit { it.remove(stringPreferencesKey(PREFIX + vodId)) }
    }

    private companion object {
        const val PREFIX = StorageNames.VOD_PROGRESS_KEY_PREFIX
        const val MAX_ENTRIES = 150

        /** "vod_123" + "pos:dur:ts" -> id to (progress to savedAt); null for anything else. */
        fun decode(key: String, value: String?): Pair<String, Pair<VodProgress, Long>>? {
            if (!key.startsWith(PREFIX) || value == null) return null
            val parts = value.split(':')
            val position = parts.getOrNull(0)?.toLongOrNull() ?: return null
            val duration = parts.getOrNull(1)?.toLongOrNull() ?: return null
            return key.removePrefix(PREFIX) to (VodProgress(position, duration) to (parts.getOrNull(2)?.toLongOrNull() ?: 0L))
        }
    }
}
