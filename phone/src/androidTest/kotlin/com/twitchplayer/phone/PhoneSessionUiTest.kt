package com.twitchplayer.phone

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.twitchplayer.phone.di.phoneModules
import com.twitchplayer.phone.ui.theme.SharedPreferencesThemePreference
import com.twitchplayer.tv.core.data.model.Account
import com.twitchplayer.tv.core.data.repository.SessionRepository
import com.twitchplayer.tv.core.data.repository.SessionState
import com.twitchplayer.tv.core.network.auth.DeviceCodeResponse
import com.twitchplayer.tv.demo.demoModule
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The Following tab and the session: the bar does not change shape while the session loads, and signing out and in again leaves the same
 * result whichever page the viewer was on (Following at its root, no Settings or channel page from before).
 */
@RunWith(AndroidJUnit4::class)
class PhoneSessionUiTest {
    @get:Rule val rule = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null
    private var loaded: List<Module> = emptyList()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun string(id: Int) = context.getString(id)

    private class SwitchableSession(initial: SessionState) : SessionRepository {
        override val state = MutableStateFlow(initial)
        override fun current(): String? = null
        override suspend fun refresh(rejected: String?): String? = null
        override fun start() = Unit
        override suspend fun signIn(onCode: (DeviceCodeResponse) -> Unit): Boolean = false
        override suspend fun signOut() {
            state.value = SessionState.SignedOut
        }
    }

    private fun launch(session: SessionRepository, hintSignedIn: Boolean) {
        context.getSharedPreferences(SharedPreferencesThemePreference.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("phone_session_hint", Context.MODE_PRIVATE).edit().putBoolean("signed_in", hintSignedIn).commit()
        loaded = listOf(demoModule, module { single<SessionRepository> { session } })
        loadKoinModules(phoneModules() + loaded)
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After fun close() {
        scenario?.close()
        if (loaded.isNotEmpty()) unloadKoinModules(loaded)
        context.getSharedPreferences("phone_session_hint", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun tab(label: Int) = isSelectable() and hasContentDescription(string(label))

    private fun present(label: Int) = rule.onAllNodes(tab(label)).fetchSemanticsNodes().isNotEmpty()

    private fun waitForText(text: String) = rule.waitUntil(WAIT_MS) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun described(description: String) = rule.onAllNodes(hasContentDescription(description, substring = true)).fetchSemanticsNodes().isNotEmpty()

    @Test fun whileTheSessionLoadsTheBarKeepsLastTimesThreeTabsThenFollowsTheAnswer() {
        val session = SwitchableSession(SessionState.Loading)
        launch(session, hintSignedIn = true)

        rule.waitUntil(WAIT_MS) { present(R.string.nav_home) }
        assertTrue("three tabs from the first frame, as last time", present(R.string.sec_following))

        session.state.value = SessionState.SignedOut
        rule.waitUntil(WAIT_MS) { !present(R.string.sec_following) }
        assertTrue(present(R.string.nav_home) && present(R.string.nav_browse))
    }

    @Test fun aViewerWhoWasSignedOutSeesTwoTabsWhileTheSessionLoadsAndThreeOnceItIsSignedIn() {
        val session = SwitchableSession(SessionState.Loading)
        launch(session, hintSignedIn = false)

        rule.waitUntil(WAIT_MS) { present(R.string.nav_home) }
        assertTrue(!present(R.string.sec_following))

        session.state.value = SessionState.SignedIn(Account("1", "camille"))
        rule.waitUntil(WAIT_MS) { present(R.string.sec_following) }
    }

    @Test fun signingOutFromSettingsOnFollowingAndInAgainFindsFollowingAtItsRoot() {
        val session = SwitchableSession(SessionState.SignedIn(Account("1", "camille")))
        launch(session, hintSignedIn = true)
        rule.waitUntil(WAIT_MS) { present(R.string.sec_following) }

        rule.onNode(tab(R.string.sec_following)).performClick()
        rule.waitUntil(WAIT_MS) { described("Lunaria") }
        rule.onNodeWithContentDescription(string(R.string.tile_account)).performClick()
        waitForText(string(R.string.phone_settings_signed_in))

        session.state.value = SessionState.SignedOut
        // Whatever page it was: Following and its pages are gone, the viewer is on Home (signed out).
        rule.waitUntil(WAIT_MS) { !present(R.string.sec_following) }
        waitForText(string(R.string.state_signin_title))
        assertTrue(rule.onAllNodesWithText(string(R.string.phone_settings_signed_in)).fetchSemanticsNodes().isEmpty())

        session.state.value = SessionState.SignedIn(Account("1", "camille"))
        rule.waitUntil(WAIT_MS) { present(R.string.sec_following) }
        rule.onNode(tab(R.string.sec_following)).performClick()
        rule.waitUntil(WAIT_MS) { described("Lunaria") }
        // No Settings page was left on Following's stack.
        assertTrue(rule.onAllNodesWithText(string(R.string.phone_settings_signed_in)).fetchSemanticsNodes().isEmpty())
        rule.onNode(tab(R.string.sec_following)).assertIsDisplayed()
    }

    @Test fun signingOutOnFollowingReturnsToHomeWhateverPageItWasOn() {
        val session = SwitchableSession(SessionState.SignedIn(Account("1", "camille")))
        launch(session, hintSignedIn = true)
        rule.waitUntil(WAIT_MS) { present(R.string.sec_following) }
        rule.onNode(tab(R.string.sec_following)).performClick()
        rule.waitUntil(WAIT_MS) { described("Lunaria") }

        session.state.value = SessionState.SignedOut

        rule.waitUntil(WAIT_MS) { !present(R.string.sec_following) }
        rule.onNode(tab(R.string.nav_home)).assertIsDisplayed()
        waitForText(string(R.string.state_signin_title))
    }

    private companion object {
        const val WAIT_MS = 20_000L
    }
}
