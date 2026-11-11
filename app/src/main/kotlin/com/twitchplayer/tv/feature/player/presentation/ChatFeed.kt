package com.twitchplayer.tv.feature.player.presentation

import com.twitchplayer.tv.core.presentation.batch
import com.twitchplayer.tv.feature.player.data.ChatAssets
import com.twitchplayer.tv.feature.player.data.ChatAssetsSource
import com.twitchplayer.tv.feature.player.data.ChatSource
import com.twitchplayer.tv.feature.player.data.chat.ChannelNotice
import com.twitchplayer.tv.feature.player.data.chat.ChatEvent
import com.twitchplayer.tv.feature.player.data.chat.RoomState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicReference

/**
 * The chat buffer of the channel being watched, kept apart from the player state: events come in from the
 * transport, are grouped into one list update per [batchMs] (a burst costs the UI one change, not one per message),
 * resolved against the channel's current assets off the main thread, and the newest [maxLines] are kept.
 */
class ChatFeed(
    private val source: ChatSource,
    private val assets: ChatAssetsSource,
    private val batchMs: Long = 250,
    private val maxLines: Int = MAX_LINES,
    private val mapper: CoroutineDispatcher = Dispatchers.Default,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutableLines = MutableStateFlow<ImmutableList<ChatLine>>(persistentListOf())
    val lines: StateFlow<ImmutableList<ChatLine>> = mutableLines.asStateFlow()

    private val mutableRoom = MutableStateFlow(RoomState())

    /** The room's modes (slow, followers-only...): from [setRoom] at the start, then live. */
    val room: StateFlow<RoomState> = mutableRoom.asStateFlow()

    private val mutableNotices = MutableSharedFlow<ChannelNotice>(extraBufferCapacity = 8)

    /** Raids, title or category changes and the end of the stream, as they arrive. */
    val notices: SharedFlow<ChannelNotice> = mutableNotices

    fun setRoom(state: RoomState) {
        mutableRoom.value = state
    }

    fun clear() {
        mutableLines.value = persistentListOf()
        mutableRoom.value = RoomState()
    }

    /** Streams until cancelled. Asset changes (late providers, live emote edits) apply to the messages that follow. */
    suspend fun run(channelId: String, userId: String, selfLogin: String?) = coroutineScope {
        val current = AtomicReference(ChatAssets.Empty)
        val firstAssets = CompletableDeferred<Unit>()
        launch {
            assets.assets(channelId).collect {
                current.set(it)
                firstAssets.complete(Unit)
            }
        }
        // Chat starts with the badges and emotes when they are quick; a slow provider must not hold messages back.
        withTimeoutOrNull(ASSETS_WAIT_MS) { firstAssets.await() }
        source.events(channelId, userId).batch(batchMs).collect { batch ->
            val now = clock()
            // Lines are resolved off the main thread; the other events keep their place between them.
            val steps = withContext(mapper) {
                val a = current.get()
                batch.map { event -> if (event is ChatEvent.Message || event is ChatEvent.Notice) event.toLine(a, selfLogin, now) else event }
            }
            var list = mutableLines.value
            var known: HashSet<String>? = null
            for (step in steps) {
                when (step) {
                    is ChatLine -> {
                        // Ids are LazyColumn keys: a repeated id (a replayed event after a reconnect) must never reach the list.
                        val ids = known ?: list.mapTo(HashSet()) { it.id }.also { known = it }
                        if (ids.add(step.id)) list = (list + step).takeLast(maxLines).toImmutableList()
                    }

                    is ChatEvent.Deleted -> list = list.filter { it.id != step.messageId }.toImmutableList().also { known = null }

                    is ChatEvent.UserCleared -> list = list.filter { it.login != step.login.lowercase() }.toImmutableList().also { known = null }

                    ChatEvent.Cleared -> {
                        list = persistentListOf()
                        known = null
                    }

                    is ChatEvent.Room -> mutableRoom.value = step.state

                    is ChatEvent.Channel -> mutableNotices.tryEmit(step.notice)

                    else -> Unit
                }
            }
            mutableLines.value = list
        }
    }

    companion object {
        /** Bounded buffer (docs/ARCHITECTURE.md: 100-200). */
        const val MAX_LINES = 200
        const val ASSETS_WAIT_MS = 4_000L
    }
}
