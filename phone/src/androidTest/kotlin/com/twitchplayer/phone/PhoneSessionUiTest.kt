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
 * The Following tab and the session: the bar always has the same four tabs, each signed-out screen says its own thing, and signing out and
 * in again leaves the same result whichever page the viewer was on (Following at its root, no Settings or channel page from before).
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

    private fun launch(session: SessionRepository) {
        context.getSharedPreferences(SharedPreferencesThemePreference.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        loaded = listOf(demoModule, module { single<SessionRepository> { session } })
        loadKoinModules(phoneModules() + loaded)
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After fun close() {
        scenario?.close()
        if (loaded.isNotEmpty()) unloadKoinModules(loaded)
    }

    private fun tab(label: Int) = isSelectable() and hasContentDescription(string(label))

    private fun present(label: Int) = rule.onAllNodes(tab(label)).fetchSemanticsNodes().isNotEmpty()

    private fun waitForText(text: String) = rule.waitUntil(WAIT_MS) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun described(description: String) = rule.onAllNodes(hasContentDescription(description, substring = true)).fetchSemanticsNodes().isNotEmpty()

    private fun fourTabs() = present(R.string.nav_home) && present(R.string.phone_nav_discover) && present(R.string.sec_following) && present(R.string.nav_browse)

    @Test fun theBarHasTheSameFourTabsWhileTheSessionLoadsAndWhateverTheAnswer() {
        val session = SwitchableSession(SessionState.Loading)
        launch(session)
        rule.waitUntil(WAIT_MS) { present(R.string.nav_home) }
        assertTrue("four tabs while loading", fourTabs())

        session.state.value = SessionState.SignedOut
        rule.waitForIdle()
        assertTrue("four tabs signed out", fourTabs())

        session.state.value = SessionState.SignedIn(Account("1", "camille"))
        rule.waitForIdle()
        assertTrue("four tabs signed in", fourTabs())
    }

    @Test fun eachSignedOutTabSaysItsOwnThing() {
        launch(SwitchableSession(SessionState.SignedOut))
        waitForText(string(R.string.phone_signedout_home_title))

        rule.onNode(tab(R.string.sec_following)).performClick()
        waitForText(string(R.string.phone_signedout_following_title))

        rule.onNode(tab(R.string.nav_browse)).performClick()
        waitForText(string(R.string.phone_signedout_browse_title))
    }

    @Test fun signingOutFromSettingsOnFollowingAndInAgainFindsFollowingAtItsRoot() {
        val session = SwitchableSession(SessionState.SignedIn(Account("1", "camille")))
        launch(session)
        rule.waitUntil(WAIT_MS) { present(R.string.sec_following) }

        rule.onNode(tab(R.string.sec_following)).performClick()
        rule.waitUntil(WAIT_MS) { described("Lunaria") }
        rule.onNodeWithContentDescription(string(R.string.tile_account)).performClick()
        waitForText(string(R.string.phone_settings_signed_in))

        session.state.value = SessionState.SignedOut
        // Following and its pages are gone: the tab is still in the bar and says to sign in; no Settings page is left on its stack.
        waitForText(string(R.string.phone_signedout_following_title))
        assertTrue(rule.onAllNodesWithText(string(R.string.phone_settings_signed_in)).fetchSemanticsNodes().isEmpty())
        assertTrue(fourTabs())

        session.state.value = SessionState.SignedIn(Account("1", "camille"))
        rule.waitUntil(WAIT_MS) { described("Lunaria") }
        assertTrue(rule.onAllNodesWithText(string(R.string.phone_settings_signed_in)).fetchSemanticsNodes().isEmpty())
        rule.onNode(tab(R.string.sec_following)).assertIsDisplayed()
    }

    private companion object {
        const val WAIT_MS = 20_000L
    }
}
