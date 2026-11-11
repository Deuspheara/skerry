package com.twitchplayer.tv.feature.settings.presentation

import com.twitchplayer.tv.core.data.model.Account
import com.twitchplayer.tv.core.data.model.AppSettings
import com.twitchplayer.tv.core.data.model.ChatMode
import com.twitchplayer.tv.core.data.model.PlayerEngine
import com.twitchplayer.tv.core.data.repository.SessionState
import com.twitchplayer.tv.testing.FakeSession
import com.twitchplayer.tv.testing.FakeSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsPresenterTest {
    private val dispatcher = UnconfinedTestDispatcher()

    private fun presenterFor(settings: FakeSettings, session: FakeSession = FakeSession(), engine: PlayerEngine = PlayerEngine.Fallback, scope: kotlinx.coroutines.CoroutineScope) = SettingsPresenter(settings, session, scope, engine).also { it.state.onEach { }.launchIn(scope) }

    @Test fun theBuildEngineIsShownUntilTheStoredSettingsArrive() = runTest(dispatcher) {
        val presenter = SettingsPresenter(FakeSettings(), FakeSession(), backgroundScope, PlayerEngine.WebView)
        assertEquals(PlayerEngine.WebView, presenter.state.value.playerEngine)
    }

    @Test fun mirrorsTheSettingsAndTheAccountLogin() = runTest(dispatcher) {
        val session = FakeSession()
        val presenter = presenterFor(FakeSettings(AppSettings(animationsEnabled = false, showStats = true)), session, scope = backgroundScope)
        advanceUntilIdle()
        assertEquals("camille", presenter.state.value.account)
        assertFalse(presenter.state.value.animationsEnabled)
        assertTrue(presenter.state.value.showStats)

        session.set(SessionState.SignedOut)
        advanceUntilIdle()
        assertNull(presenter.state.value.account)
        session.set(SessionState.SignedIn(Account("1", "other")))
        advanceUntilIdle()
        assertEquals("other", presenter.state.value.account)
    }

    @Test fun togglesFlipTheirSettings() = runTest(dispatcher) {
        val settings = FakeSettings()
        val presenter = presenterFor(settings, scope = backgroundScope)
        advanceUntilIdle()

        presenter.onEvent(SettingsEvent.ToggleAnimations)
        presenter.onEvent(SettingsEvent.ToggleStats)
        presenter.onEvent(SettingsEvent.ToggleFollowRaids)
        advanceUntilIdle()
        assertFalse(settings.settings.value.animationsEnabled)
        assertTrue(settings.settings.value.showStats)
        assertTrue(settings.settings.value.followRaids)

        presenter.onEvent(SettingsEvent.ToggleAnimations)
        advanceUntilIdle()
        assertTrue(settings.settings.value.animationsEnabled)
    }

    @Test fun enginesToggleBetweenNativeAndWebView() = runTest(dispatcher) {
        val settings = FakeSettings(AppSettings(playerEngine = PlayerEngine.Native))
        val presenter = presenterFor(settings, scope = backgroundScope)
        advanceUntilIdle()
        presenter.onEvent(SettingsEvent.TogglePlayerEngine)
        advanceUntilIdle()
        assertEquals(PlayerEngine.WebView, settings.settings.value.playerEngine)
        presenter.onEvent(SettingsEvent.TogglePlayerEngine)
        advanceUntilIdle()
        assertEquals(PlayerEngine.Native, settings.settings.value.playerEngine)
    }

    @Test fun chatModeIsSetAndCyclesThroughEveryModeBackToTheStart() = runTest(dispatcher) {
        val settings = FakeSettings()
        val presenter = presenterFor(settings, scope = backgroundScope)
        advanceUntilIdle()

        presenter.onEvent(SettingsEvent.SetChatMode(ChatMode.Overlay))
        advanceUntilIdle()
        assertEquals(ChatMode.Overlay, settings.settings.value.chatMode)

        val seen = mutableListOf(settings.settings.value.chatMode)
        repeat(ChatMode.entries.size) {
            presenter.onEvent(SettingsEvent.CycleChatMode)
            advanceUntilIdle()
            seen += settings.settings.value.chatMode
        }
        assertEquals(seen.first(), seen.last())
        assertEquals(ChatMode.entries.toSet(), seen.toSet())
    }

    @Test fun chatOptionsAreUpdatedThroughTheTransform() = runTest(dispatcher) {
        val settings = FakeSettings()
        val presenter = presenterFor(settings, scope = backgroundScope)
        advanceUntilIdle()
        presenter.onEvent(SettingsEvent.UpdateChat { it.copy(showBadges = !it.showBadges) })
        advanceUntilIdle()
        assertEquals(!AppSettings().chat.showBadges, settings.settings.value.chat.showBadges)
    }
}
