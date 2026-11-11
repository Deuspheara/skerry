package com.twitchplayer.tv.feature.player

import androidx.lifecycle.viewModelScope
import com.twitchplayer.tv.core.data.model.AppSettings
import com.twitchplayer.tv.core.data.model.ChatMode
import com.twitchplayer.tv.core.data.model.ChatTextSize
import com.twitchplayer.tv.core.ui.ChannelKeyBus
import com.twitchplayer.tv.feature.player.data.ChatAssets
import com.twitchplayer.tv.feature.player.data.ChatAssetsSource
import com.twitchplayer.tv.feature.player.data.ChatSource
import com.twitchplayer.tv.feature.player.data.chat.ChatEvent
import com.twitchplayer.tv.feature.player.data.chat.ChatFragment
import com.twitchplayer.tv.feature.player.data.chat.ChatMessage
import com.twitchplayer.tv.feature.player.data.chat.ChatMessageContent
import com.twitchplayer.tv.feature.player.presentation.PlayerCommand
import com.twitchplayer.tv.feature.player.presentation.PlayerViewModel
import com.twitchplayer.tv.feature.player.presentation.ZapRowKind
import com.twitchplayer.tv.testing.FakeRepository
import com.twitchplayer.tv.testing.FakeSession
import com.twitchplayer.tv.testing.FakeSettings
import com.twitchplayer.tv.testing.stream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val repo = FakeRepository(live = listOf(stream("a"), stream("b"), stream("c")))
    private val chatFlow = MutableSharedFlow<ChatEvent>(extraBufferCapacity = 256)
    private val keys = ChannelKeyBus()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun message(id: String) = ChatEvent.Message(ChatMessage(id, "b", "u", "nick", "nick", ChatMessageContent("hi $id", listOf(ChatFragment("text", "hi $id")))))

    private fun TestScope.vm(login: String = "b", chat: ChatMode = ChatMode.Hidden, settingsOverride: FakeSettings? = null): PlayerViewModel = PlayerViewModel(
        login, repo, FakeSession(), settingsOverride ?: FakeSettings(AppSettings(chatMode = chat)),
        chatAssets = ChatAssetsSource { flowOf(ChatAssets.Empty) },
        chatSource = ChatSource { _, _ -> chatFlow as Flow<ChatEvent> },
        channelKeys = keys, idleMs = 4_000, chatBatchMs = 250, chatMapper = dispatcher,
    ).also { vm ->
        vm.state.onEach { }.launchIn(backgroundScope)
        // The chat batcher ticks forever: stop the VM's scope when the test body ends, or runTest never finishes.
        backgroundScope.coroutineContext[Job]!!.invokeOnCompletion { vm.viewModelScope.cancel() }
    }

    @Test fun controlsHideAfterFourSecondsAndAnyKeyWakesThem() = runTest(dispatcher) {
        val vm = vm()
        assertTrue(vm.state.value.controlsVisible)
        advanceTimeBy(3_999)
        runCurrent()
        assertTrue(vm.state.value.controlsVisible)
        advanceTimeBy(2)
        runCurrent()
        assertFalse(vm.state.value.controlsVisible)
        vm.wake()
        assertTrue(vm.state.value.controlsVisible)
        advanceTimeBy(4_001)
        runCurrent()
        assertFalse(vm.state.value.controlsVisible)
    }

    @Test fun zapperKeepsControlsVisibleAndBackClosesItFirst() = runTest(dispatcher) {
        val vm = vm()
        vm.openZapper()
        advanceTimeBy(10_000)
        runCurrent()
        assertTrue(vm.state.value.zapperOpen)
        assertTrue(vm.state.value.controlsVisible)
        assertTrue(vm.handleBack())
        assertFalse(vm.state.value.zapperOpen)
        assertTrue("then the bottom overlay", vm.handleBack())
        assertFalse(vm.state.value.controlsVisible)
        assertFalse("nothing left to close: the screen is left", vm.handleBack())
    }

    @Test fun openingTheRowsLoadsTheCategoryAndRecommendationsWithoutThePlayingChannel() = runTest(dispatcher) {
        repo.live = listOf(stream("a", category = "chess"), stream("b", category = "chess"), stream("c", category = "cook"))
        val vm = vm(login = "b")
        runCurrent()
        assertEquals(listOf(ZapRowKind.Following), vm.state.value.zapRows.map { it.kind })
        vm.openZapper()
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
        assertTrue("the bottom overlay goes first", vm.handleBack())
        assertFalse(vm.state.value.controlsVisible)
        assertTrue(vm.handleBack())
        assertEquals(ChatMode.Hidden, vm.state.value.chatMode)
        assertFalse("closing chat does not bring the overlay back", vm.state.value.controlsVisible)
        assertFalse(vm.handleBack())
    }

    @Test fun chatButtonCyclesModesWithoutMovingAnythingElse() = runTest(dispatcher) {
        val vm = vm(chat = ChatMode.Hidden)
        runCurrent()
        val seen = (1..3).map {
            vm.cycleChat()
            vm.state.value.chatMode
        }
        assertEquals(listOf(ChatMode.Compact, ChatMode.Overlay, ChatMode.Hidden), seen)
    }

    @Test fun backClosesChatOptionsBeforeTheChatItself() = runTest(dispatcher) {
        val vm = vm(chat = ChatMode.Compact)
        runCurrent()
        vm.openChatSettings()
        assertTrue(vm.state.value.chatSettingsOpen)
        assertTrue(vm.handleBack())
        assertFalse(vm.state.value.chatSettingsOpen)
        assertEquals("chat stays", ChatMode.Compact, vm.state.value.chatMode)
        assertTrue(vm.handleBack())
        assertFalse(vm.state.value.controlsVisible)
        assertTrue(vm.handleBack())
        assertEquals(ChatMode.Hidden, vm.state.value.chatMode)
    }

    @Test fun chatOptionsCannotOpenWhileChatIsHiddenAndUpdatesAreSaved() = runTest(dispatcher) {
        val vm = vm(chat = ChatMode.Hidden)
        runCurrent()
        vm.openChatSettings()
        assertFalse(vm.state.value.chatSettingsOpen)
        vm.updateChatSettings { it.copy(textSize = ChatTextSize.Large) }
        runCurrent()
        assertEquals(ChatTextSize.Large, vm.chatSettings.value.textSize)
    }

    @Test fun theChatButtonNeverChangesTheSavedMode() = runTest(dispatcher) {
        val saved = FakeSettings(AppSettings(chatMode = ChatMode.Compact))
        val vm = vm(chat = ChatMode.Compact, settingsOverride = saved)
        runCurrent()
        vm.cycleChat()
        runCurrent()
        assertEquals("the chat button is for this viewing session only", ChatMode.Compact, saved.settings.value.chatMode)
    }

    @Test fun fullscreenSuppressesChatWithoutLosingTheChoice() = runTest(dispatcher) {
        val vm = vm(chat = ChatMode.Compact)
        runCurrent()
        vm.toggleFullscreen()
        assertEquals(ChatMode.Hidden, vm.state.value.effectiveChat)
        assertEquals(ChatMode.Compact, vm.state.value.chatMode)
        vm.toggleFullscreen()
        assertEquals(ChatMode.Compact, vm.state.value.effectiveChat)
    }

    @Test fun previousAndNextStopAtTheEnds() = runTest(dispatcher) {
        val vm = vm(login = "a")
        runCurrent()
        assertFalse(vm.state.value.canPrevious)
        assertTrue(vm.state.value.canNext)
        vm.previous()
        assertEquals("a", vm.state.value.login)
        vm.next()
        vm.next()
        vm.next()
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
        vm.togglePause()
        vm.togglePause()
        vm.toggleMute()
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
        assertEquals("nothing is published before the batch window ends", 0, vm.chat.value.size)
        advanceTimeBy(251)
        runCurrent()
        assertEquals("bounded to the 200 newest lines", 200, vm.chat.value.size)
        assertEquals("m249", vm.chat.value.last().id)
    }

    @Test fun hiddenChatKeepsItsMessagesAndConnection() = runTest(dispatcher) {
        val vm = vm(chat = ChatMode.Compact)
        runCurrent()
        repeat(3) { chatFlow.emit(message("m$it")) }
        advanceTimeBy(251)
        runCurrent()
        assertEquals(3, vm.chat.value.size)
        vm.cycleChat()
        vm.cycleChat() // Compact -> Overlay -> Hidden
        assertEquals(ChatMode.Hidden, vm.state.value.effectiveChat)
        assertEquals("hiding chat keeps the buffer", 3, vm.chat.value.size)
        chatFlow.emit(message("m3"))
        advanceTimeBy(251)
        runCurrent()
        assertEquals("still connected while hidden: reopening is instant and current", "m3", vm.chat.value.last().id)
        vm.cycleChat()
        assertEquals(4, vm.chat.value.size)
    }

    @Test fun noChatConnectionUntilChatIsFirstShown() = runTest(dispatcher) {
        val vm = vm(chat = ChatMode.Hidden)
        runCurrent()
        assertEquals(0, chatFlow.subscriptionCount.value)
        vm.cycleChat()
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
        vm.next()
        runCurrent()
        assertEquals(0, vm.chat.value.size)
    }

    @Test fun upOnTheTopLayerClosesEverything() = runTest(dispatcher) {
        val vm = vm()
        vm.openZapper()
        vm.hideOverlay()
        assertFalse(vm.state.value.zapperOpen)
        assertFalse(vm.state.value.controlsVisible)
        vm.wake()
        assertTrue(vm.state.value.controlsVisible)
    }

    @Test fun miniPlayerKeepsTheSessionButDropsChat() = runTest(dispatcher) {
        val vm = vm(chat = ChatMode.Compact)
        runCurrent()
        assertEquals(ChatMode.Compact, vm.state.value.effectiveChat)
        vm.setMinimized(true)
        assertTrue(vm.state.value.active)
        assertEquals("the chat choice is kept, only its drawing is suppressed", ChatMode.Compact, vm.state.value.chatMode)
        assertEquals(ChatMode.Hidden, vm.state.value.effectiveChat)
        vm.setMinimized(false)
        assertEquals(ChatMode.Compact, vm.state.value.effectiveChat)
    }

    @Test fun overlayChatSurvivesLikeTheOtherModes() = runTest(dispatcher) {
        val vm = vm(chat = ChatMode.Overlay)
        runCurrent()
        assertEquals(ChatMode.Overlay, vm.state.value.effectiveChat)
        vm.hideOverlay()
        assertTrue("Back hides the overlay chat before leaving", vm.handleBack())
        assertEquals(ChatMode.Hidden, vm.state.value.chatMode)
    }

    @Test fun closeEndsTheSessionAndKeepsTheChatChoice() = runTest(dispatcher) {
        val vm = vm(chat = ChatMode.Compact)
        runCurrent()
        vm.close()
        assertFalse(vm.state.value.active)
        assertEquals("", vm.state.value.login)
        assertEquals(ChatMode.Compact, vm.state.value.chatMode)
    }

    @Test fun attachStartsASessionWhenNoneIsRunning() = runTest(dispatcher) {
        val vm = vm(login = "")
        runCurrent()
        assertFalse(vm.state.value.active)
        vm.attach("b")
        assertTrue(vm.state.value.active)
        assertEquals("b", vm.state.value.login)
        assertFalse(vm.state.value.minimized)
    }

    @Test fun returningFromTheMiniPlayerResumesTheSameChannel() = runTest(dispatcher) {
        val vm = vm(login = "")
        vm.attach("a")
        vm.zap(stream("c"))
        vm.detach() // dock: the player screen left the back stack
        vm.setMinimized(true)
        vm.attach("c") // expanded from the mini player: same channel, no restart
        assertEquals("c", vm.state.value.login)
        assertFalse(vm.state.value.minimized)
    }

    @Test fun watchingAnotherChannelFromElsewhereReplacesTheMiniPlayer() = runTest(dispatcher) {
        val vm = vm(login = "")
        vm.attach("a")
        vm.zap(stream("c"))
        vm.detach()
        vm.setMinimized(true)
        vm.attach("a") // the viewer picked "a" while "c" was in the corner
        assertEquals("a", vm.state.value.login)
    }

    @Test fun zappingInsideThePlayerSurvivesAChannelPageDetour() = runTest(dispatcher) {
        val vm = vm(login = "")
        vm.attach("a")
        vm.zap(stream("c"))
        vm.setMinimized(true) // channel page pushed over the player screen (still in the back stack)
        vm.attach("a") // popping back: the screen is keyed "a" but the viewer zapped to "c"
        assertEquals("c", vm.state.value.login)
    }

    @Test fun channelKeysAreIgnoredWhileMinimized() = runTest(dispatcher) {
        val vm = vm(login = "a")
        runCurrent()
        vm.setMinimized(true)
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
        assertEquals("the header follows the channel's new title", "New title", vm.state.value.current?.title)
        // An ad break owns the corner: the notice's visible time does not run meanwhile.
        vm.setAdActive(true)
        advanceTimeBy(30_000)
        runCurrent()
        assertTrue("still waiting for the ad to end", vm.state.value.notice != null)
        vm.setAdActive(false)
        advanceTimeBy(11_000)
        runCurrent()
        assertTrue("11 of its 12 s", vm.state.value.notice != null)
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
        assertEquals("not before the banner was readable", "b", on.state.value.login)
        advanceTimeBy(6_100)
        runCurrent()
        assertEquals("c", on.state.value.login)
        assertEquals("a zap clears the old channel's notice", null, on.state.value.notice)
    }
}
