package com.twitchplayer.tv.feature.player.presentation

import com.twitchplayer.tv.core.data.model.ChatMode
import com.twitchplayer.tv.core.data.model.ChatSettings
import com.twitchplayer.tv.core.data.model.LiveStream
import com.twitchplayer.tv.core.data.model.PlayerEngine
import com.twitchplayer.tv.core.data.repository.SendResult
import com.twitchplayer.tv.core.data.repository.SessionRepository
import com.twitchplayer.tv.core.data.repository.SessionState
import com.twitchplayer.tv.core.data.repository.SettingsRepository
import com.twitchplayer.tv.core.data.repository.TwitchRepository
import com.twitchplayer.tv.core.ui.ChannelKeyBus
import com.twitchplayer.tv.feature.player.data.ChatAssetsSource
import com.twitchplayer.tv.feature.player.data.ChatSource
import com.twitchplayer.tv.feature.player.data.chat.ChannelNotice
import com.twitchplayer.tv.feature.player.data.chat.RoomState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Timings and the dispatcher the chat mapping runs on: production uses the defaults, tests shorten or replace them. */
class PlayerTuning(
    /** The control bar hides after this long without input. */
    val idleMs: Long = 4_000L,
    /** Chat events are grouped into one list update per this interval. */
    val chatBatchMs: Long = 250L,
    val chatMapper: CoroutineDispatcher = Dispatchers.Default,
)

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerPresenter(
    initialLogin: String,
    private val repository: TwitchRepository,
    private val session: SessionRepository,
    private val settings: SettingsRepository,
    chatAssets: ChatAssetsSource,
    chatSource: ChatSource,
    channelKeys: ChannelKeyBus,
    private val scope: CoroutineScope,
    /** The engine a fresh install starts with (`AppConfig.defaultPlayerEngine`), until the saved setting is read. */
    defaultEngine: PlayerEngine,
    tuning: PlayerTuning = PlayerTuning(),
) {
    private val idleMs = tuning.idleMs
    private val mutableState = MutableStateFlow(PlayerUiState(login = initialLogin, active = initialLogin.isNotEmpty()))
    val state: StateFlow<PlayerUiState> = mutableState.asStateFlow()

    private val chatFeed = ChatFeed(chatSource, chatAssets, tuning.chatBatchMs, mapper = tuning.chatMapper)
    val chat: StateFlow<ImmutableList<ChatLine>> = chatFeed.lines

    /** Slow / followers-only / subscribers-only / emote-only / unique-chat modes of the room, live. */
    val room: StateFlow<RoomState> = chatFeed.room

    /** Playback statistics are shown over the native player (Settings). */
    val showStats: StateFlow<Boolean> = settings.settings.map { it.showStats }.distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, false)

    private val mutableSent = MutableSharedFlow<SendResult>(extraBufferCapacity = 4)

    /** The outcome of each message the viewer sent, for the screen to tell them when it did not go out. */
    val sent: SharedFlow<SendResult> = mutableSent

    /** Look and behaviour of the chat panel; changes apply live, so the effect of a setting is visible while adjusting it. */
    val chatSettings: StateFlow<ChatSettings> = settings.settings.map { it.chat }.distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, ChatSettings())

    /** Which player draws the video (Settings); the screen falls back to the WebView one per channel when the native one fails. */
    val playerEngine: StateFlow<PlayerEngine> = settings.settings.map { it.playerEngine }.distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, defaultEngine)

    /** The quality picked in the native player, re-applied on every stream (null = default policy). */
    val preferredQuality: StateFlow<String?> = settings.settings.map { it.preferredQuality }.distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, null)

    private fun setPreferredQuality(id: String) {
        scope.launch { settings.setPreferredQuality(id) }
    }

    private val mutableCommands = MutableSharedFlow<PlayerCommand>(extraBufferCapacity = 8)
    val commands: SharedFlow<PlayerCommand> = mutableCommands

    private var idleJob: Job? = null
    private var screenKeyed = initialLogin.isNotEmpty()

    init {
        scope.launch {
            val mode = settings.settings.first().chatMode
            mutableState.update { it.copy(chatMode = mode) }
        }
        if (initialLogin.isNotEmpty()) scope.launch { loadChannels(initialLogin) }
        scope.launch { channelKeys.events.collect { if (!mutableState.value.minimized) cycleChannel(it) } }
        observeChat()
        scope.launch { chatFeed.notices.collect(::onNotice) }
        wake()
    }

    private var noticeJob: Job? = null
    private val adActive = MutableStateFlow(false)

    /** An ad break is on screen: it holds the top corner, so a notice's visible time only counts once the ad is over. */
    private fun setAdActive(active: Boolean) {
        adActive.value = active
    }

    /** Everything the viewer or the screen can do; [state] and the streams above are what they see. */
    fun onEvent(event: PlayerEvent) {
        when (event) {
            is PlayerEvent.Attach -> attach(event.login)
            PlayerEvent.Detach -> detach()
            is PlayerEvent.SetMinimized -> setMinimized(event.minimized)
            PlayerEvent.Close -> close()
            PlayerEvent.Wake -> wake()
            PlayerEvent.TogglePause -> togglePause()
            PlayerEvent.ToggleMute -> toggleMute()
            PlayerEvent.ToggleFullscreen -> toggleFullscreen()
            PlayerEvent.EnsureChatVisible -> ensureChatVisible()
            PlayerEvent.CycleChat -> cycleChat()
            is PlayerEvent.UpdateChatSettings -> updateChatSettings(event.transform)
            PlayerEvent.OpenChatSettings -> openChatSettings()
            PlayerEvent.CloseChatSettings -> closeChatSettings()
            PlayerEvent.OpenZapper -> openZapper()
            PlayerEvent.CloseZapper -> closeZapper()
            PlayerEvent.HideOverlay -> hideOverlay()
            PlayerEvent.Previous -> previous()
            PlayerEvent.Next -> next()
            is PlayerEvent.Zap -> zap(event.stream)
            is PlayerEvent.SendChat -> sendChat(event.text)
            is PlayerEvent.SetAdActive -> setAdActive(event.active)
            is PlayerEvent.SetPreferredQuality -> setPreferredQuality(event.id)
        }
    }

    private fun showNotice(notice: ChannelNotice) {
        mutableState.update { it.copy(notice = notice) }
        noticeJob?.cancel()
        // "Stream ended" stays: nothing else will say why the picture stopped.
        if (notice !is ChannelNotice.Offline) {
            noticeJob = scope.launch {
                var visibleMs = 0L
                while (visibleMs < NOTICE_MS) {
                    adActive.first { !it }
                    delay(NOTICE_TICK_MS)
                    if (!adActive.value) visibleMs += NOTICE_TICK_MS
                }
                mutableState.update { if (it.notice == notice) it.copy(notice = null) else it }
            }
        }
    }

    private fun onNotice(notice: ChannelNotice) {
        when (notice) {
            is ChannelNotice.Updated -> {
                mutableState.update { s ->
                    s.copy(current = s.current?.copy(title = notice.title ?: s.current.title, categoryName = notice.category ?: s.current.categoryName))
                }
                showNotice(notice)
            }

            is ChannelNotice.Raid -> scope.launch {
                val follow = settings.settings.first().followRaids && notice.toLogin.isNotEmpty()
                showNotice(notice.copy(following = follow))
                if (follow) {
                    delay(RAID_FOLLOW_DELAY_MS)
                    runCatching { repository.liveStream(notice.toLogin) }.getOrNull()?.let(::zap)
                }
            }

            ChannelNotice.Offline -> showNotice(notice)
        }
    }

    /** Sends a chat message; the result (or the reason it did not go out) comes back on [sent]. Own messages arrive through chat itself. */
    private fun sendChat(text: String) {
        val message = text.trim()
        val channelId = mutableState.value.current?.channelId
        if (message.isEmpty() || channelId == null) return
        scope.launch { mutableSent.emit(repository.sendChat(channelId, message)) }
        wake()
    }

    private suspend fun loadChannels(login: String) {
        val live = runCatching { repository.follows().live }.getOrDefault(emptyList())
        val current = live.firstOrNull { it.login.equals(login, true) } ?: runCatching { repository.liveStream(login) }.getOrNull()
        val list = if (current != null && live.none { it.channelId == current.channelId }) listOf(current) + live else live
        mutableState.update { it.copy(zapList = list.toImmutableList(), current = current ?: it.current) }
    }

    /**
     * One chat connection per channel. It opens the first time chat is shown for that channel and then stays open
     * while chat is hidden (button, full screen, mini player), so reopening shows the current messages at once with
     * no reconnect; the buffer is capped at [ChatFeed.MAX_LINES]. A channel switch or the end of the session clears it.
     */
    private fun observeChat() {
        scope.launch {
            mutableState.map { if (it.active) it.login else "" }.distinctUntilChanged().collectLatest { login ->
                chatFeed.clear()
                if (login.isEmpty()) return@collectLatest
                mutableState.first { it.chatVisible }
                val me = (session.state.value as? SessionState.SignedIn)?.account ?: return@collectLatest
                try {
                    val channelId = mutableState.value.current?.takeIf { it.login.equals(login, true) }?.channelId
                        ?: repository.liveStream(login)?.also { live -> mutableState.update { it.copy(current = live) } }?.channelId
                        ?: return@collectLatest
                    coroutineScope {
                        launch { repository.roomState(channelId)?.let(chatFeed::setRoom) }
                        chatFeed.run(channelId, me.userId, me.login)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Chat is best effort: the stream keeps playing without it.
                }
            }
        }
    }

    /**
     * The player screen is on top for [login]. A new session starts when none is running; a mini player coming
     * back to full screen (same channel) just resumes; a different channel picked from elsewhere replaces it.
     * Zapping inside the player never changes what the screen was opened for, so returning from a channel page
     * keeps the zapped channel.
     */
    private fun attach(login: String) {
        val s = mutableState.value
        if (!s.active || (!screenKeyed && !s.login.equals(login, true))) {
            open(login)
        } else {
            mutableState.update { it.copy(minimized = false) }
        }
        screenKeyed = true
        wake()
    }

    /** No player screen is left in the back stack (a running session is now only a mini player). */
    private fun detach() {
        screenKeyed = false
    }

    /** The player screen is hidden behind another screen (mini player) or back on top. */
    private fun setMinimized(minimized: Boolean) {
        if (mutableState.value.minimized == minimized) return
        mutableState.update { it.copy(minimized = minimized, zapperOpen = false) }
        if (!minimized) wake()
    }

    /** Ends the session: the embed is released and chat disconnects. */
    private fun close() {
        screenKeyed = false
        idleJob?.cancel()
        mutableState.update { PlayerUiState(chatMode = it.chatMode) }
    }

    private fun open(login: String) {
        mutableState.update {
            PlayerUiState(login = login, chatMode = it.chatMode, active = true, zapList = it.zapList, current = null)
        }
        scope.launch { loadChannels(login) }
    }

    /** Any key: show the bar and restart the ~4 s hide timer (kept visible while the zapper is open). */
    private fun wake() {
        mutableState.update { it.copy(controlsVisible = true) }
        idleJob?.cancel()
        idleJob = scope.launch {
            delay(idleMs)
            if (!mutableState.value.zapperOpen) mutableState.update { it.copy(controlsVisible = false) }
        }
    }

    private fun togglePause() {
        val paused = !mutableState.value.paused
        mutableState.update { it.copy(paused = paused) }
        mutableCommands.tryEmit(if (paused) PlayerCommand.Pause else PlayerCommand.Play)
        wake()
    }

    private fun toggleMute() {
        mutableState.update { it.copy(muted = !it.muted) }
        mutableCommands.tryEmit(PlayerCommand.ToggleMute)
        wake()
    }

    private fun toggleFullscreen() {
        mutableState.update { it.copy(fullscreen = !it.fullscreen) }
        wake()
    }

    /** Writing a message shows the chat first: the viewer sees their message arrive. */
    private fun ensureChatVisible() {
        if (mutableState.value.chatMode == ChatMode.Hidden) showChat(ChatMode.Compact)
    }

    private fun cycleChat() = showChat(ChatMode.entries[(mutableState.value.chatMode.ordinal + 1) % ChatMode.entries.size])

    private fun updateChatSettings(transform: (ChatSettings) -> ChatSettings) {
        scope.launch { settings.updateChat(transform) }
        wake()
    }

    /** Session only: the chat button and Back never change the saved default. */
    private fun showChat(mode: ChatMode) {
        mutableState.update { it.copy(chatMode = mode, fullscreen = false, chatSettingsOpen = it.chatSettingsOpen && mode != ChatMode.Hidden) }
        wake()
    }

    private fun openChatSettings() {
        mutableState.update { it.copy(chatSettingsOpen = it.effectiveChat != ChatMode.Hidden) }
        wake()
    }
    private fun closeChatSettings() {
        mutableState.update { it.copy(chatSettingsOpen = false) }
        wake()
    }

    private fun openZapper() {
        idleJob?.cancel()
        mutableState.update { it.copy(zapperOpen = true, controlsVisible = true) }
        loadMoreRows()
    }

    private var moreRowsFor: String? = null
    private var moreRowsJob: Job? = null

    /** Category and recommendation rows for the playing channel; loaded once per channel, when the rows first open. */
    private fun loadMoreRows() {
        val s = mutableState.value
        val key = s.login.lowercase() + "|" + s.current?.categoryId
        if (key == moreRowsFor) return
        moreRowsFor = key
        moreRowsJob?.cancel()
        moreRowsJob = scope.launch {
            val current = s.current
            val playing = s.login
            fun List<LiveStream>.others() = filter { !it.login.equals(playing, true) }.toImmutableList()
            val category = async {
                current?.categoryId?.takeIf { it.isNotEmpty() }?.let { id ->
                    runCatching { repository.categoryStreams(id) }.getOrNull()?.let { ZapRow(ZapRowKind.Category(current.categoryName), it.others()) }
                }
            }
            val forYou = async { runCatching { repository.recommended() }.getOrNull()?.let { ZapRow(ZapRowKind.ForYou, it.others()) } }
            mutableState.update { it.copy(moreRows = listOfNotNull(category.await(), forYou.await()).toImmutableList()) }
        }
    }

    private fun closeZapper() {
        mutableState.update { it.copy(zapperOpen = false) }
        wake()
    }

    /** Back to the video alone (layer 0): suggestions and the bottom overlay both close. */
    private fun hideOverlay() {
        idleJob?.cancel()
        mutableState.update { it.copy(zapperOpen = false, controlsVisible = false) }
    }

    private fun previous() = step(-1, wrap = false)
    private fun next() = step(1, wrap = false)

    private fun cycleChannel(delta: Int) = step(delta, wrap = true)

    private fun step(delta: Int, wrap: Boolean) {
        val s = mutableState.value
        val list = s.zapList
        if (list.size < 2 && !wrap) return
        if (list.size < 2) return
        val index = list.indexOfFirst { it.login.equals(s.login, true) }
        val target = if (wrap) cycleIndex(index, delta, list.size) else (index + delta).takeIf { it in list.indices } ?: return
        zap(list[target])
    }

    private fun zap(stream: LiveStream) {
        if (stream.login.equals(mutableState.value.login, true)) return
        mutableState.update { it.copy(login = stream.login, current = stream, paused = false, notice = null) }
        wake()
    }

    /**
     * Back: closes the topmost layer first (suggestions, chat options, the bottom overlay, then chat).
     * Returns false when the screen should be left.
     */
    fun handleBack(): Boolean {
        val s = mutableState.value
        return when {
            s.zapperOpen -> {
                closeZapper()
                true
            }

            s.chatSettingsOpen -> {
                closeChatSettings()
                true
            }

            s.controlsVisible -> {
                hideOverlay()
                true
            }

            // The overlay is already hidden here: closing chat must not bring it back.
            s.effectiveChat != ChatMode.Hidden -> {
                mutableState.update { it.copy(chatMode = ChatMode.Hidden, fullscreen = false, chatSettingsOpen = false) }
                true
            }

            else -> false
        }
    }

    private companion object {
        const val NOTICE_MS = 12_000L
        const val NOTICE_TICK_MS = 250L

        /** A followed raid starts after the banner has been readable for a moment (Twitch's own page waits too). */
        const val RAID_FOLLOW_DELAY_MS = 6_000L
    }
}
