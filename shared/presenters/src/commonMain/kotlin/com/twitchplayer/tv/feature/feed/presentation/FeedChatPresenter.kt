package com.twitchplayer.tv.feature.feed.presentation

import com.twitchplayer.tv.core.data.model.ChatSettings
import com.twitchplayer.tv.core.data.repository.SessionRepository
import com.twitchplayer.tv.core.data.repository.SessionState
import com.twitchplayer.tv.core.data.repository.SettingsRepository
import com.twitchplayer.tv.feature.player.data.ChatAssetsSource
import com.twitchplayer.tv.feature.player.data.ChatSource
import com.twitchplayer.tv.feature.player.data.LiteChatAssets
import com.twitchplayer.tv.feature.player.presentation.ChatFeed
import com.twitchplayer.tv.feature.player.presentation.ChatLine
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The few chat lines laid over a Discover page: a read-only connection to one channel, and nothing else. Next to the player's chat it has no
 * emote picker and no viewer-emote paging, no room modes or notices, no stream list to zap through, no live emote connection (the third-party
 * emotes are read once, from the cache when fresh) and only a short buffer of lines. It starts when it is created and stops with [scope].
 * [channelId] comes with the stream the feed already has, so there is no lookup either.
 */
class FeedChatPresenter(
    channelId: String,
    session: SessionRepository,
    settings: SettingsRepository,
    chatSource: ChatSource,
    assets: LiteChatAssets,
    scope: CoroutineScope,
    mapper: CoroutineDispatcher = Dispatchers.Default,
) {
    private val feed = ChatFeed(chatSource, ChatAssetsSource { assets.lite(it) }, maxLines = MAX_LINES, mapper = mapper)

    /** The newest lines, oldest first. */
    val lines: StateFlow<ImmutableList<ChatLine>> = feed.lines

    /** The viewer's chat look (text size, badges...): the same settings as the player's chat. */
    val chatSettings: StateFlow<ChatSettings> = settings.settings.map { it.chat }.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, ChatSettings())

    init {
        scope.launch {
            try {
                // The connection is signed with the stored token: wait for the session to have read it (bounded).
                session.awaitReady()
                val me = (session.state.value as? SessionState.SignedIn)?.account ?: return@launch
                feed.run(channelId, me.userId, me.login)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Best effort: the picture keeps playing without chat.
            }
        }
    }

    private companion object {
        const val MAX_LINES = 12
    }
}
