package com.twitchplayer.tv

import android.view.KeyEvent
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.twitchplayer.tv.core.data.model.ChatMode
import com.twitchplayer.tv.core.data.repository.DataStoreSettingsRepository
import com.twitchplayer.tv.core.di.settingsStore
import com.twitchplayer.tv.demo.demoModule
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.loadKoinModules

/**
 * D-pad behaviour on deterministic demo data: default focus, Back closing the topmost layer first,
 * and focus restoration. Real key events are injected so the whole focus/back pipeline is exercised.
 */
@RunWith(AndroidJUnit4::class)
class TvFocusFlowTest {
    @get:Rule val rule = createEmptyComposeRule()
    private lateinit var scenario: ActivityScenario<MainActivity>
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun string(id: Int) = ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)

    @Before fun launch() {
        runBlocking { DataStoreSettingsRepository(ApplicationProvider.getApplicationContext<android.content.Context>().settingsStore).setChatMode(ChatMode.Hidden) }
        loadKoinModules(demoModule)
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After fun close() = scenario.close()

    private fun press(vararg codes: Int) = codes.forEach {
        instrumentation.sendKeyDownUpSync(it)
        rule.waitForIdle()
        Thread.sleep(250)
    }

    private fun waitFor(description: String) = rule.waitUntil(8_000) { rule.onAllNodes(hasContentDescription(description, substring = true)).fetchSemanticsNodes().isNotEmpty() }

    @Test fun homeStartsOnTheFirstFollowedLiveCard() {
        waitFor("Lunaria")
        rule.onNodeWithContentDescription("Lunaria", substring = true).assertIsFocused()
    }

    @Test fun backFromACategoryRestoresFocusOnTheCardItCameFrom() {
        waitFor("Lunaria")
        // Following -> For you -> Categories, then one card to the right.
        press(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)
        rule.onNodeWithContentDescription("Pixel Racers").assertIsFocused()
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        waitFor(string(R.string.cd_back))
        press(KeyEvent.KEYCODE_BACK)
        waitFor("Pixel Racers")
        rule.onNodeWithContentDescription("Pixel Racers").assertIsFocused()
    }

    @Test fun searchOpensOnTheVoiceKey() {
        waitFor("Lunaria")
        press(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_CENTER)
        waitFor(string(R.string.key_voice))
        rule.onNodeWithContentDescription(string(R.string.key_voice)).assertIsFocused()
    }

    @Test fun backClosesTheZapperBeforeLeavingThePlayer() {
        waitFor("Lunaria")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        waitFor(string(R.string.player_pause))
        rule.onNodeWithContentDescription(string(R.string.player_pause)).assertIsFocused()
        press(KeyEvent.KEYCODE_DPAD_UP)
        waitFor(string(R.string.player_playing))
        press(KeyEvent.KEYCODE_BACK)
        rule.onAllNodes(hasContentDescription(string(R.string.player_playing), substring = true)).fetchSemanticsNodes().let { assert(it.isEmpty()) { "zapper should be closed" } }
        rule.onNodeWithContentDescription(string(R.string.player_pause)).assertExists()
        // Chat is hidden in this test, so the zapper was the only overlay: the next Back leaves the player.
        press(KeyEvent.KEYCODE_BACK)
        waitFor(string(R.string.sec_following))
        rule.onNodeWithContentDescription("Lunaria", substring = true).assertIsFocused()
    }

    @Test fun backHidesTheChatBeforeLeavingThePlayerAndFocusStaysOnTheChatButton() {
        waitFor("Lunaria")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        waitFor(string(R.string.player_pause))
        // Pause -> Sound -> Full screen -> Channel + -> Channel - -> Chat: OK shows the chat (hidden -> compact).
        press(*IntArray(5) { KeyEvent.KEYCODE_DPAD_RIGHT })
        rule.onNodeWithContentDescription(string(R.string.player_chat)).assertIsFocused()
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        waitFor(string(R.string.chat_panel))
        rule.onNodeWithContentDescription(string(R.string.chat_panel)).assertIsNotFocused() // showing chat never moves focus into it
        rule.onNodeWithContentDescription(string(R.string.player_chat)).assertIsFocused()
        // Back closes the topmost layer: the bottom overlay first, then the chat.
        press(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_BACK)
        rule.onAllNodes(hasContentDescription(string(R.string.chat_panel))).fetchSemanticsNodes().let { assert(it.isEmpty()) { "chat should be hidden" } }
        rule.onNodeWithContentDescription(string(R.string.player_chat)).assertIsFocused()
        press(KeyEvent.KEYCODE_BACK)
        waitFor(string(R.string.sec_following))
    }

    @Test fun chatOptionsOpenFromSettingsStepTheirValueAndBackReturnsToTheirButton() {
        waitFor("Lunaria")
        press(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_CENTER)
        // Settings starts on the selected chat width (compact); Right, Right reaches the options button.
        waitFor(string(R.string.chat_compact))
        rule.onNodeWithContentDescription(string(R.string.chat_compact)).assertIsFocused()
        press(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_RIGHT)
        rule.onNodeWithContentDescription(string(R.string.player_chat_options)).assertIsFocused()
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        waitFor("${string(R.string.chat_opt_text)}: ${string(R.string.opt_medium)}")
        rule.onNodeWithContentDescription("${string(R.string.chat_opt_text)}: ${string(R.string.opt_medium)}").assertIsFocused()
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        waitFor("${string(R.string.chat_opt_text)}: ${string(R.string.opt_large)}")
        press(KeyEvent.KEYCODE_DPAD_LEFT) // put it back: the store outlives the test
        waitFor("${string(R.string.chat_opt_text)}: ${string(R.string.opt_medium)}")
        press(KeyEvent.KEYCODE_BACK)
        rule.waitUntil(5_000) { rule.onAllNodes(hasContentDescription("${string(R.string.chat_opt_text)}: ", substring = true)).fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithContentDescription(string(R.string.player_chat_options)).assertIsFocused()
    }

    @Test fun backOnHomeAsksBeforeQuittingAndFocusesStay() {
        waitFor("Lunaria")
        press(KeyEvent.KEYCODE_BACK)
        rule.waitUntil(8_000) { rule.onAllNodes(hasText(string(R.string.dialog_stay))).fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(hasText(string(R.string.dialog_stay))).assertIsFocused()
        press(KeyEvent.KEYCODE_BACK)
        rule.waitUntil(8_000) { rule.onAllNodes(hasText(string(R.string.dialog_stay))).fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithContentDescription("Lunaria", substring = true).assertIsFocused()
    }

    @Test fun browseKeepsTheSelectedChipAsTheWayUpAndTheFirstPosterAsTheWayDown() {
        waitFor("Lunaria")
        press(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_CENTER)
        waitFor("Valmoor")
        rule.onNodeWithContentDescription("Valmoor").assertIsFocused() // grid default
        press(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_UP)
        rule.onNode(hasText(string(R.string.filter_all))).assertIsFocused() // selected chip, whatever column we left from
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        rule.onNodeWithContentDescription("Pixel Racers").assertIsFocused() // back to the remembered poster
    }
}
