package com.twitchplayer.tv.feature.player

import com.twitchplayer.tv.core.data.model.AppSettings
import com.twitchplayer.tv.core.data.model.ChatMode
import com.twitchplayer.tv.core.data.model.ChatTextSize
import com.twitchplayer.tv.core.data.model.PlayerEngine
import com.twitchplayer.tv.core.data.repository.SendResult
import com.twitchplayer.tv.core.data.repository.SettingsRepository
import com.twitchplayer.tv.core.data.repository.TwitchRepository
import com.twitchplayer.tv.core.ui.ChannelKeyBus
import com.twitchplayer.tv.feature.player.data.ChatAssets
import com.twitchplayer.tv.feature.player.data.ChatAssetsSource
import com.twitchplayer.tv.feature.player.data.ChatSource
import com.twitchplayer.tv.feature.player.data.chat.ChatEvent
import com.twitchplayer.tv.feature.player.data.chat.ChatFragment
import com.twitchplayer.tv.feature.player.data.chat.ChatMessage
import com.twitchplayer.tv.feature.player.data.chat.ChatMessageContent
import com.twitchplayer.tv.feature.player.presentation.PlayerCommand
import com.twitchplayer.tv.feature.player.presentation.PlayerEvent
import com.twitchplayer.tv.feature.player.presentation.PlayerPresenter
import com.twitchplayer.tv.feature.player.presentation.PlayerTuning
import com.twitchplayer.tv.feature.player.presentation.ZapRowKind
import com.twitchplayer.tv.testing.FakeRepository
import com.twitchplayer.tv.testing.FakeSession
import com.twitchplayer.tv.testing.FakeSettings
import com.twitchplayer.tv.testing.stream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerPresenterTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val repo = FakeRepository(live = listOf(stream("a"), stream("b"), stream("c")))
    private val chatFlow = MutableSharedFlow<ChatEvent>(extraBufferCapacity = 256)
    private val keys = ChannelKeyBus()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun message(id: String) = ChatEvent.Message(ChatMessage(id, "b", "u", "nick", "nick", ChatMessageContent("hi $id", listOf(ChatFragment("text", "hi $id")))))

    private fun TestScope.vm(login: String = "b", chat: ChatMode = ChatMode.Hidden, settingsOverride: SettingsRepository? = null, repository: TwitchRepository = repo): PlayerPresenter = PlayerPresenter(
        login, repository, FakeSession(), settingsOverride ?: FakeSettings(AppSettings(chatMode = chat)),
        chatAssets = ChatAssetsSource { flowOf(ChatAssets.Empty) },
        chatSource = ChatSource { _, _ -> chatFlow as Flow<ChatEvent> },
        channelKeys = keys, scope = backgroundScope, defaultEngine = PlayerEngine.Native,
        tuning = PlayerTuning(idleMs = 4_000, chatBatchMs = 250, chatMapper = dispatcher),
    ).also { vm -> vm.state.onEach { }.launchIn(backgroundScope) }

    @Test fun controlsHideAfterFourSecondsAndAnyKeyWakesThem() = runTest(dispatcher) {
        val vm = vm()
        assertTrue(vm.state.value.controlsVisible)
        advanceTimeBy(3_999)
        runCurrent()
        assertTrue(vm.state.value.controlsVisible)
        advanceTimeBy(2)
        runCurrent()
        assertFalse(vm.state.value.controlsVisible)
        vm.onEvent(PlayerEvent.Wake)
        assertTrue(vm.state.value.controlsVisible)
        advanceTimeBy(4_001)
        runCurrent()
        assertFalse(vm.state.value.controlsVisible)
    }

    @Test fun zapperKeepsControlsVisibleAndBackClosesItFirst() = runTest(dispatcher) {
        val vm = vm()
        vm.onEvent(PlayerEvent.OpenZapper)
        advanceTimeBy(10_000)
        runCurrent()
        assertTrue(vm.state.value.zapperOpen)
        assertTrue(vm.state.value.controlsVisible)
        assertTrue(vm.handleBack())
        assertFalse(vm.state.value.zapperOpen)
        assertTrue(vm.handleBack(), "then the bottom overlay")
        assertFalse(vm.state.value.controlsVisible)
        assertFalse(vm.handleBack(), "nothing left to close: the screen is left")
    }

    @Test fun openingTheRowsLoadsTheCategoryAndRecommendationsWithoutThePlayingChannel() = runTest(dispatcher) {
        repo.live = listOf(stream("a", category = "chess"), stream("b", category = "chess"), stream("c", category = "cook"))
        val vm = vm(login = "b")
        runCurrent()
        assertEquals(listOf(ZapRowKind.Following), vm.state.value.zapRows.map { it.kind })
        vm.onEvent(PlayerEvent.OpenZapper)
        runCurrent()
        val rows = vm.state.value.zapRows
        assertEquals(listOf(ZapRowKind.Following, ZapRowKind.Category("chess"), ZapRowKind.ForYou), rows.map { it.kind })
        assertEquals(listOf("a"), rows[1].streams.map { it.login })
        assertEquals(listOf("a", "c"), rows[2].streams.map { it.login })
    }

    @Test fun backClosesChatBeforeLeaving() = runTest(dispatcher) {
        val vm = vm(chat = ChatMode.Compact)
        runCurrent()
        assertEquals(ChatMode.Compact, vm.state.value.chatMode)
        assertTrue(vm.handleBack(), "the bottom overlay goes first")
        assertFalse(vm.state.value.controlsVisible)
        assertTrue(vm.handleBack())
        assertEquals(ChatMode.Hidden, vm.state.value.chatMode)
        assertFalse(vm.state.value.controlsVisible, "closing chat does not bring the overlay back")
        assertFalse(vm.handleBack())
    }

    @Test fun chatButtonCyclesModesWithoutMovingAnythingElse() = runTest(dispatcher) {
        val vm = vm(chat = ChatMode.Hidden)
        runCurrent()
        val seen = (1..3).map {
            vm.onEvent(PlayerEvent.CycleChat)
            vm.state.value.chatMode
        }
        assertEquals(listOf(ChatMode.Compact, ChatMode.Overlay, ChatMode.Hidden), seen)
    }

    @Test fun backClosesChatOptionsBeforeTheChatItself() = runTest(dispatcher) {
        val vm = vm(chat = ChatMode.Compact)
        runCurrent()
        vm.onEvent(PlayerEvent.OpenChatSettings)
        assertTrue(vm.state.value.chatSettingsOpen)
        assertTrue(vm.handleBack())
        assertFalse(vm.state.value.chatSettingsOpen)
        assertEquals(ChatMode.Compact, vm.state.value.chatMode, "chat stays")
        assertTrue(vm.handleBack())
        assertFalse(vm.state.value.controlsVisible)
        assertTrue(vm.handleBack())
        assertEquals(ChatMode.Hidden, vm.state.value.chatMode)
    }

    @Test fun chatOptionsCannotOpenWhileChatIsHiddenAndUpdatesAreSaved() = runTest(dispatcher) {
        val vm = vm(chat = ChatMode.Hidden)
        runCurrent()
        vm.onEvent(PlayerEvent.OpenChatSettings)
        assertFalse(vm.state.value.chatSettingsOpen)
        vm.onEvent(PlayerEvent.UpdateChatSettings { it.copy(textSize = ChatTextSize.Large) })
        runCurrent()
        assertEquals(ChatTextSize.Large, vm.chatSettings.value.textSize)
    }

    @Test fun theChatButtonNeverChangesTheSavedMode() = runTest(dispatcher) {
        val saved = FakeSettings(AppSettings(chatMode = ChatMode.Compact))
        val vm = vm(chat = ChatMode.Compact, settingsOverride = saved)
        runCurrent()
        vm.onEvent(PlayerEvent.CycleChat)
        runCurrent()
        assertEquals(ChatMode.Compact, saved.settings.value.chatMode, "the chat button is for this viewing session only")
    }

    @Test fun setChatModeShowsExactlyThatModeForThisSessionOnly() = runTest(dispatcher) {
        val saved = FakeSettings(AppSettings(chatMode = ChatMode.Compact))
        val vm = vm(chat = ChatMode.Compact, settingsOverride = saved)
        runCurrent()
        vm.onEvent(PlayerEvent.SetChatMode(ChatMode.Overlay))
        runCurrent()
        assertEquals(ChatMode.Overlay, vm.state.value.chatMode)
        vm.onEvent(PlayerEvent.SetChatMode(ChatMode.Hidden))
        runCurrent()
        assertEquals(ChatMode.Hidden, vm.state.value.chatMode)
        assertEquals(ChatMode.Compact, saved.settings.value.chatMode, "like the chat button, it never changes the saved default")
    }

    /** Settings that answer after [delayMs]: the real store reads from disk, the fake one answers at once. */
    private class LateSettings(private val inner: FakeSettings, private val delayMs: Long) : SettingsRepository by inner {
        override val settings = flow {
            delay(delayMs)
            emitAll(inner.settings)
        }
    }

    @Test fun theSavedChatModeLoadingLateNeverOverwritesAModeChosenBeforeIt() = runTest(dispatcher) {
        val vm = vm(chat = ChatMode.Compact, settingsOverride = LateSettings(FakeSettings(AppSettings(chatMode = ChatMode.Compact)), delayMs = 1_000))
        // Chosen while the saved default is still loading: a screen restoring its own choice does this.
        vm.onEvent(PlayerEvent.SetChatMode(ChatMode.Hidden))
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(ChatMode.Hidden, vm.state.value.chatMode)
    }

    @Test fun everyChoiceMadeBeforeTheSavedDefaultLoadsSurvivesItWhateverTheDefaultIs() = runTest(dispatcher) {
        // The placeholder before settings load is Hidden: a choice equal to it, or to the default, must still win over the late default.
        for (default in ChatMode.entries) {
            for (chosen in ChatMode.entries) {
                val vm = vm(chat = default, settingsOverride = LateSettings(FakeSettings(AppSettings(chatMode = default)), delayMs = 1_000))
                vm.onEvent(PlayerEvent.SetChatMode(chosen))
                advanceTimeBy(2_000)
                runCurrent()
                assertEquals(chosen, vm.state.value.chatMode, "default $default, chosen $chosen")
            }
        }
    }

    @Test fun fullscreenSuppressesChatWithoutLosingTheChoice() = runTest(dispatcher) {
        val vm = vm(chat = ChatMode.Compact)
        runCurrent()
        vm.onEvent(PlayerEvent.ToggleFullscreen)
        assertEquals(ChatMode.Hidden, vm.state.value.effectiveChat)
        assertEquals(ChatMode.Compact, vm.state.value.chatMode)
        vm.onEvent(PlayerEvent.ToggleFullscreen)
        assertEquals(ChatMode.Compact, vm.state.value.effectiveChat)
    }

    @Test fun previousAndNextStopAtTheEnds() = runTest(dispatcher) {
        val vm = vm(login = "a")
        runCurrent()
        assertFalse(vm.state.value.canPrevious)
        assertTrue(vm.state.value.canNext)
        vm.onEvent(PlayerEvent.Previous)
        assertEquals("a", vm.state.value.login)
        vm.onEvent(PlayerEvent.Next)
        vm.onEvent(PlayerEvent.Next)
        vm.onEvent(PlayerEvent.Next)
        assertEquals("c", vm.state.value.login)
        assertFalse(vm.state.value.canNext)
    }

    @Test fun hardwareChannelKeysWrapAround() = runTest(dispatcher) {
        val vm = vm(login = "c")
        runCurrent()
        keys.emit(1)
        runCurrent()
        assertEquals("a", vm.state.value.login)
        keys.emit(-1)
        runCurrent()
        assertEquals("c", vm.state.value.login)
    }

    @Test fun pauseAndMuteEmitCommandsForTheEmbed() = runTest(dispatcher) {
        val vm = vm()
        val commands = mutableListOf<PlayerCommand>()
        vm.commands.onEach { commands += it }.launchIn(backgroundScope)
        vm.onEvent(PlayerEvent.TogglePause)
        vm.onEvent(PlayerEvent.TogglePause)
        vm.onEvent(PlayerEvent.ToggleMute)
        assertEquals(listOf(PlayerCommand.Pause, PlayerCommand.Play, PlayerCommand.ToggleMute), commands)
        assertTrue(vm.state.value.muted)
        assertFalse(vm.state.value.paused)
    }

    @Test fun chatLinesAreBatchedAndBounded() = runTest(dispatcher) {
        val vm = vm(chat = ChatMode.Compact)
        runCurrent()
        advanceTimeBy(10)
        runCurrent()
        repeat(250) { chatFlow.emit(message("m$it")) }
        runCurrent()
        assertEquals(0, vm.chat.value.size, "nothing is published before the batch window ends")
        advanceTimeBy(251)
        runCurrent()
        assertEquals(200, vm.chat.value.size, "bounded to the 200 newest lines")
        assertEquals("m249", vm.chat.value.last().id)
    }

    @Test fun hiddenChatKeepsItsMessagesAndConnection() = runTest(dispatcher) {
        val vm = vm(chat = ChatMode.Compact)
        runCurrent()
        repeat(3) { chatFlow.emit(message("m$it")) }
        advanceTimeBy(251)
        runCurrent()
        assertEquals(3, vm.chat.value.size)
        vm.onEvent(PlayerEvent.CycleChat)
        vm.onEvent(PlayerEvent.CycleChat) // Compact -> Overlay -> Hidden
        assertEquals(ChatMode.Hidden, vm.state.value.effectiveChat)
        assertEquals(3, vm.chat.value.size, "hiding chat keeps the buffer")
        chatFlow.emit(message("m3"))
        advanceTimeBy(251)
        runCurrent()
        assertEquals("m3", vm.chat.value.last().id, "still connected while hidden: reopening is instant and current")
        vm.onEvent(PlayerEvent.CycleChat)
        assertEquals(4, vm.chat.value.size)
    }

    @Test fun noChatConnectionUntilChatIsFirstShown() = runTest(dispatcher) {
        val vm = vm(chat = ChatMode.Hidden)
        runCurrent()
        assertEquals(0, chatFlow.subscriptionCount.value)
        vm.onEvent(PlayerEvent.CycleChat)
        runCurrent()
        assertEquals(1, chatFlow.subscriptionCount.value)
    }

    @Test fun switchingChannelClearsTheChat() = runTest(dispatcher) {
        val vm = vm(chat = ChatMode.Compact)
        runCurrent()
        chatFlow.emit(message("m0"))
        advanceTimeBy(251)
        runCurrent()
        assertEquals(1, vm.chat.value.size)
        vm.onEvent(PlayerEvent.Next)
        runCurrent()
        assertEquals(0, vm.chat.value.size)
    }

    @Test fun upOnTheTopLayerClosesEverything() = runTest(dispatcher) {
        val vm = vm()
        vm.onEvent(PlayerEvent.OpenZapper)
        vm.onEvent(PlayerEvent.HideOverlay)
        assertFalse(vm.state.value.zapperOpen)
        assertFalse(vm.state.value.controlsVisible)
        vm.onEvent(PlayerEvent.Wake)
        assertTrue(vm.state.value.controlsVisible)
    }

    @Test fun miniPlayerKeepsTheSessionButDropsChat() = runTest(dispatcher) {
        val vm = vm(chat = ChatMode.Compact)
        runCurrent()
        assertEquals(ChatMode.Compact, vm.state.value.effectiveChat)
        vm.onEvent(PlayerEvent.SetMinimized(true))
        assertTrue(vm.state.value.active)
        assertEquals(ChatMode.Compact, vm.state.value.chatMode, "the chat choice is kept, only its drawing is suppressed")
        assertEquals(ChatMode.Hidden, vm.state.value.effectiveChat)
        vm.onEvent(PlayerEvent.SetMinimized(false))
        assertEquals(ChatMode.Compact, vm.state.value.effectiveChat)
    }

    @Test fun overlayChatSurvivesLikeTheOtherModes() = runTest(dispatcher) {
        val vm = vm(chat = ChatMode.Overlay)
        runCurrent()
        assertEquals(ChatMode.Overlay, vm.state.value.effectiveChat)
        vm.onEvent(PlayerEvent.HideOverlay)
        assertTrue(vm.handleBack(), "Back hides the overlay chat before leaving")
        assertEquals(ChatMode.Hidden, vm.state.value.chatMode)
    }

    @Test fun closeEndsTheSessionAndKeepsTheChatChoice() = runTest(dispatcher) {
        val vm = vm(chat = ChatMode.Compact)
        runCurrent()
        vm.onEvent(PlayerEvent.Close)
        assertFalse(vm.state.value.active)
        assertEquals("", vm.state.value.login)
        assertEquals(ChatMode.Compact, vm.state.value.chatMode)
    }

    @Test fun attachStartsASessionWhenNoneIsRunning() = runTest(dispatcher) {
        val vm = vm(login = "")
        runCurrent()
        assertFalse(vm.state.value.active)
        vm.onEvent(PlayerEvent.Attach("b"))
        assertTrue(vm.state.value.active)
        assertEquals("b", vm.state.value.login)
        assertFalse(vm.state.value.minimized)
    }

    @Test fun returningFromTheMiniPlayerResumesTheSameChannel() = runTest(dispatcher) {
        val vm = vm(login = "")
        vm.onEvent(PlayerEvent.Attach("a"))
        vm.onEvent(PlayerEvent.Zap(stream("c")))
        vm.onEvent(PlayerEvent.Detach) // dock: the player screen left the back stack
        vm.onEvent(PlayerEvent.SetMinimized(true))
        vm.onEvent(PlayerEvent.Attach("c")) // expanded from the mini player: same channel, no restart
        assertEquals("c", vm.state.value.login)
        assertFalse(vm.state.value.minimized)
    }

    @Test fun watchingAnotherChannelFromElsewhereReplacesTheMiniPlayer() = runTest(dispatcher) {
        val vm = vm(login = "")
        vm.onEvent(PlayerEvent.Attach("a"))
        vm.onEvent(PlayerEvent.Zap(stream("c")))
        vm.onEvent(PlayerEvent.Detach)
        vm.onEvent(PlayerEvent.SetMinimized(true))
        vm.onEvent(PlayerEvent.Attach("a")) // the viewer picked "a" while "c" was in the corner
        assertEquals("a", vm.state.value.login)
    }

    @Test fun zappingInsideThePlayerSurvivesAChannelPageDetour() = runTest(dispatcher) {
        val vm = vm(login = "")
        vm.onEvent(PlayerEvent.Attach("a"))
        vm.onEvent(PlayerEvent.Zap(stream("c")))
        vm.onEvent(PlayerEvent.SetMinimized(true)) // channel page pushed over the player screen (still in the back stack)
        vm.onEvent(PlayerEvent.Attach("a")) // popping back: the screen is keyed "a" but the viewer zapped to "c"
        assertEquals("c", vm.state.value.login)
    }

    @Test fun channelKeysAreIgnoredWhileMinimized() = runTest(dispatcher) {
        val vm = vm(login = "a")
        runCurrent()
        vm.onEvent(PlayerEvent.SetMinimized(true))
        keys.emit(1)
        runCurrent()
        assertEquals("a", vm.state.value.login)
    }

    @Test fun aNoticeLeavesAfterItsTimeAndAnAdBreakHoldsItsClock() = runTest(dispatcher) {
        val vm = vm(chat = ChatMode.Compact)
        runCurrent()
        advanceTimeBy(10)
        runCurrent()
        chatFlow.emit(ChatEvent.Channel(com.twitchplayer.tv.feature.player.data.chat.ChannelNotice.Updated("New title", "Chess")))
        advanceTimeBy(300)
        runCurrent()
        assertTrue(vm.state.value.notice is com.twitchplayer.tv.feature.player.data.chat.ChannelNotice.Updated)
        assertEquals("New title", vm.state.value.current?.title, "the header follows the channel's new title")
        // An ad break owns the corner: the notice's visible time does not run meanwhile.
        vm.onEvent(PlayerEvent.SetAdActive(true))
        advanceTimeBy(30_000)
        runCurrent()
        assertTrue(vm.state.value.notice != null, "still waiting for the ad to end")
        vm.onEvent(PlayerEvent.SetAdActive(false))
        advanceTimeBy(11_000)
        runCurrent()
        assertTrue(vm.state.value.notice != null, "11 of its 12 s")
        advanceTimeBy(1_500)
        runCurrent()
        assertEquals(null, vm.state.value.notice)
    }

    @Test fun aRaidIsFollowedAfterTheBannerOnlyWhenTheSettingIsOn() = runTest(dispatcher) {
        val raid = ChatEvent.Channel(com.twitchplayer.tv.feature.player.data.chat.ChannelNotice.Raid("B", "C", "c", 120))
        val off = vm(chat = ChatMode.Compact)
        runCurrent()
        advanceTimeBy(10)
        runCurrent()
        chatFlow.emit(raid)
        advanceTimeBy(300)
        runCurrent()
        assertEquals(false, (off.state.value.notice as com.twitchplayer.tv.feature.player.data.chat.ChannelNotice.Raid).following)
        advanceTimeBy(7_000)
        runCurrent()
        assertEquals("b", off.state.value.login)
    }

    @Test fun aFollowedRaidZapsToTheRaidedChannel() = runTest(dispatcher) {
        val on = vm(chat = ChatMode.Compact, settingsOverride = FakeSettings(AppSettings(chatMode = ChatMode.Compact, followRaids = true)))
        runCurrent()
        advanceTimeBy(10)
        runCurrent()
        chatFlow.emit(ChatEvent.Channel(com.twitchplayer.tv.feature.player.data.chat.ChannelNotice.Raid("B", "C", "c", 120)))
        advanceTimeBy(300)
        runCurrent()
        assertEquals(true, (on.state.value.notice as com.twitchplayer.tv.feature.player.data.chat.ChannelNotice.Raid).following)
        assertEquals("b", on.state.value.login, "not before the banner was readable")
        advanceTimeBy(6_100)
        runCurrent()
        assertEquals("c", on.state.value.login)
        assertEquals(null, on.state.value.notice, "a zap clears the old channel's notice")
    }

    @Test fun sendChatPostsTheTrimmedTextToTheCurrentChannelAndReportsTheOutcome() = runTest(dispatcher) {
        val posted = mutableListOf<Pair<String, String>>()
        val repository = object : TwitchRepository by repo {
            override suspend fun sendChat(channelId: String, text: String): SendResult {
                posted += channelId to text
                return SendResult.Dropped("slow mode")
            }
        }
        val vm = vm(repository = repository)
        val outcomes = mutableListOf<SendResult>()
        vm.sent.onEach { outcomes += it }.launchIn(backgroundScope)

        vm.onEvent(PlayerEvent.SendChat("  hello  "))
        vm.onEvent(PlayerEvent.SendChat("   "))

        assertEquals(listOf("b" to "hello"), posted, "blank messages are never sent")
        assertEquals(listOf<SendResult>(SendResult.Dropped("slow mode")), outcomes)
    }

    @Test fun sendChatDoesNothingWithoutACurrentChannel() = runTest(dispatcher) {
        val posted = mutableListOf<String>()
        val repository = object : TwitchRepository by repo {
            override suspend fun liveStream(login: String) = null
            override suspend fun sendChat(channelId: String, text: String): SendResult {
                posted += text
                return SendResult.Sent
            }
        }
        val vm = vm(login = "", repository = repository)
        vm.onEvent(PlayerEvent.SendChat("hi"))
        assertTrue(posted.isEmpty())
    }

    @Test fun writingShowsTheChatFirstButOnlyWhenItIsHidden() = runTest(dispatcher) {
        val hidden = vm(chat = ChatMode.Hidden)
        hidden.onEvent(PlayerEvent.EnsureChatVisible)
        assertEquals(ChatMode.Compact, hidden.state.value.chatMode)

        val overlay = vm(chat = ChatMode.Overlay)
        overlay.onEvent(PlayerEvent.EnsureChatVisible)
        assertEquals(ChatMode.Overlay, overlay.state.value.chatMode, "a visible chat keeps its mode")
    }

    @Test fun chatOptionsAndTheZapperOpenAndCloseOnTheirEvents() = runTest(dispatcher) {
        val vm = vm(chat = ChatMode.Compact)
        vm.onEvent(PlayerEvent.OpenChatSettings)
        assertTrue(vm.state.value.chatSettingsOpen)
        vm.onEvent(PlayerEvent.CloseChatSettings)
        assertFalse(vm.state.value.chatSettingsOpen)

        vm.onEvent(PlayerEvent.OpenZapper)
        assertTrue(vm.state.value.zapperOpen)
        vm.onEvent(PlayerEvent.CloseZapper)
        assertFalse(vm.state.value.zapperOpen)
        assertTrue(vm.state.value.controlsVisible, "closing the rows brings the control bar back (and restarts its timer)")
    }

    @Test fun thePickedQualityIsSavedAndComesBackAsThePreferredOne() = runTest(dispatcher) {
        val settings = FakeSettings()
        val vm = vm(settingsOverride = settings)
        assertEquals(null, vm.preferredQuality.value)
        vm.onEvent(PlayerEvent.SetPreferredQuality("720p60"))
        assertEquals("720p60", settings.settings.value.preferredQuality)
        assertEquals("720p60", vm.preferredQuality.first { it != null })
    }

    @Test fun theEngineStartsOnTheBuildDefaultUntilTheSavedSettingIsRead() = runTest(dispatcher) {
        val never = object : SettingsRepository by FakeSettings() {
            override val settings: Flow<AppSettings> = flow { awaitCancellation() }
        }
        val presenter = PlayerPresenter(
            "b", repo, FakeSession(), never, ChatAssetsSource { flowOf(ChatAssets.Empty) }, ChatSource { _, _ -> chatFlow as Flow<ChatEvent> },
            keys, backgroundScope, PlayerEngine.WebView, PlayerTuning(chatMapper = dispatcher),
        )
        assertEquals(PlayerEngine.WebView, presenter.playerEngine.value)

        val saved = vm(settingsOverride = FakeSettings(AppSettings(playerEngine = PlayerEngine.Native)))
        assertEquals(PlayerEngine.Native, saved.playerEngine.value, "the saved choice replaces the default at once")
    }
}
