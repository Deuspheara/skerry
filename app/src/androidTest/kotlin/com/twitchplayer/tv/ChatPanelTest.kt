package com.twitchplayer.tv

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.twitchplayer.tv.core.data.model.ChatSettings
import com.twitchplayer.tv.feature.player.presentation.ChatLine
import com.twitchplayer.tv.feature.player.presentation.ChatLineKind
import com.twitchplayer.tv.feature.player.presentation.ChatPiece
import com.twitchplayer.tv.feature.player.ui.ChatPanel
import com.twitchplayer.tv.feature.player.ui.toStyle
import kotlinx.collections.immutable.toImmutableList
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Chat remote behaviour, with the real [ChatPanel] next to a stand-in "control bar" button: the panel never takes
 * focus by itself, Right enters it, Up / Down browse history, a pill counts what arrives meanwhile, OK returns to live
 * and Left gives focus back. Messages are plain text, so nothing here needs the network.
 */
@RunWith(AndroidJUnit4::class)
class ChatPanelTest {
    @get:Rule val rule = createComposeRule()

    private fun line(n: Int) = ChatLine("m$n", ChatLineKind.Normal, "nick", 0xFFBF94FF, emptyList(), listOf(ChatPiece.Text("message number $n")), "00:00")
    private var lines by mutableStateOf((0 until 60).map(::line).toImmutableList())
    private val control = FocusRequester()
    private val chat = FocusRequester()

    private fun host() = rule.setContent {
        Row {
            Box(
                Modifier.size(40.dp).testTag("control").focusRequester(control)
                    .focusProperties { right = chat }.focusable(),
            )
            ChatPanel(
                lines,
                remember { ChatSettings().toStyle() },
                Modifier.width(240.dp).fillMaxHeight(),
                focusRequester = chat,
                onLeave = { control.requestFocus() },
            )
        }
    }

    private fun press(key: Key) {
        rule.onRoot().performKeyInput { pressKey(key) }
        rule.waitForIdle()
    }
    private fun addMessages(count: Int) {
        val start = lines.size
        lines = (lines + (start until start + count).map(::line)).toImmutableList()
        rule.waitForIdle()
    }
    private fun pill(text: String) = hasContentDescription(text)

    @Test fun theChatNeverTakesFocusWhileMessagesArrive() {
        host()
        rule.runOnIdle { control.requestFocus() }
        addMessages(5)
        rule.onNodeWithTag("control").assertIsFocused()
        rule.onNodeWithContentDescription("Chat messages").assertIsNotFocused()
    }

    @Test fun rightEntersTheChatAndLeftGivesFocusBack() {
        host()
        rule.runOnIdle { control.requestFocus() }
        press(Key.DirectionRight)
        rule.onNodeWithContentDescription("Chat messages").assertIsFocused()
        press(Key.DirectionLeft)
        rule.onNodeWithTag("control").assertIsFocused()
    }

    @Test fun followsNewMessagesWhileLive() {
        host()
        rule.onNode(hasText("message number 59", substring = true)).assertExists()
        addMessages(3)
        rule.waitUntil(3_000) { rule.onAllNodes(hasText("message number 62", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodes(pill("Back to live")).assertCountEquals(0)
    }

    @Test fun browsingHistoryHoldsPositionCountsNewMessagesAndOkReturnsToLive() {
        host()
        rule.runOnIdle { control.requestFocus() }
        press(Key.DirectionRight)
        repeat(3) { press(Key.DirectionUp) }
        rule.onNode(pill("Back to live")).assertExists()

        addMessages(4)
        rule.onNode(pill("4 new messages")).assertExists()
        rule.onAllNodes(hasText("message number 63", substring = true)).assertCountEquals(0)

        press(Key.DirectionCenter)
        rule.waitUntil(3_000) { rule.onAllNodes(hasText("message number 63", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodes(pill("Back to live")).assertCountEquals(0)
        rule.onAllNodes(hasContentDescription("4 new messages")).assertCountEquals(0)
    }

    @Test fun aClearedListStartsLiveAgain() {
        host()
        rule.runOnIdle { control.requestFocus() }
        press(Key.DirectionRight)
        press(Key.DirectionUp)
        rule.onNode(pill("Back to live")).assertExists()
        lines = emptyList<ChatLine>().toImmutableList()
        rule.waitForIdle()
        lines = (0 until 5).map { line(1000 + it) }.toImmutableList()
        rule.waitUntil(3_000) { rule.onAllNodes(hasText("message number 1004", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodes(pill("Back to live")).assertCountEquals(0)
        addMessages(1)
        rule.waitUntil(3_000) { rule.onAllNodes(hasText("message number 5", substring = true)).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun scrollingBackDownToTheBottomResumesLive() {
        host()
        rule.runOnIdle { control.requestFocus() }
        press(Key.DirectionRight)
        press(Key.DirectionUp)
        rule.onNode(pill("Back to live")).assertExists()
        repeat(4) { press(Key.DirectionDown) }
        rule.waitUntil(3_000) { rule.onAllNodes(pill("Back to live")).fetchSemanticsNodes().isEmpty() }
    }
}
