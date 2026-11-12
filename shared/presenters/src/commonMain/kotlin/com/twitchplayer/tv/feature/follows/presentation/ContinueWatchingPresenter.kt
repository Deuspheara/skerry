package com.twitchplayer.tv.feature.follows.presentation

import com.twitchplayer.tv.core.data.model.WatchedReplay
import com.twitchplayer.tv.core.data.repository.TwitchRepository
import com.twitchplayer.tv.feature.vod.data.VodProgress
import com.twitchplayer.tv.feature.vod.data.VodProgressStore
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** A replay the viewer started and did not finish, with where they stopped. */
data class ContinueWatchingItem(val replay: WatchedReplay, val progress: VodProgress)

/**
 * "Continue watching": the replays with a saved position worth resuming, most recently watched first. Positions live on the device
 * ([VodProgressStore]); the title, picture and channel of each come from one batched `videos` call, remembered while this presenter
 * lives. A replay Twitch no longer returns (deleted, expired) is not listed. When the call fails the entries already known stay.
 */
class ContinueWatchingPresenter(
    private val repository: TwitchRepository,
    store: VodProgressStore,
    scope: CoroutineScope,
    private val limit: Int = LIMIT,
) {
    private val details = HashMap<String, WatchedReplay>()
    private val mutableItems = MutableStateFlow<ImmutableList<ContinueWatchingItem>>(persistentListOf())
    val items: StateFlow<ImmutableList<ContinueWatchingItem>> = mutableItems.asStateFlow()

    init {
        scope.launch {
            store.progress
                .map { all -> resumable(all, limit) }
                .distinctUntilChanged()
                .collectLatest { entries ->
                    val missing = entries.map { it.first }.filter { it !in details }
                    if (missing.isNotEmpty()) {
                        try {
                            repository.replaysByIds(missing).forEach { details[it.replay.id] = it }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            // Best effort: what is known stays on screen.
                        }
                    }
                    mutableItems.value = entries.mapNotNull { (id, progress) -> details[id]?.let { ContinueWatchingItem(it, progress) } }.toImmutableList()
                }
        }
    }

    companion object {
        const val LIMIT = 10

        /** The entries worth offering, newest first: past the first seconds, before the credits, at most [limit]. */
        fun resumable(all: Map<String, VodProgress>, limit: Int = LIMIT): List<Pair<String, VodProgress>> = all.entries.filter { it.value.resumable }.sortedByDescending { it.value.savedAtMs }.take(limit).map { it.key to it.value }
    }
}
