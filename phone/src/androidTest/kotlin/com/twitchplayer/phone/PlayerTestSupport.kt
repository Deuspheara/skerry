package com.twitchplayer.phone

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.media3.ui.PlayerView

private const val SUPPORT_WAIT_MS = 20_000L

/** A node whose click has this accessibility label (the streamer row under the player, the expand tap on the strip). */
fun hasClickLabel(label: String) = SemanticsMatcher("click label '$label'") { it.config.getOrNull(SemanticsActions.OnClick)?.label == label }

/** Home -> a tap on the first live stream: the player opens with its chat (no channel page in between). */
fun ComposeTestRule.openFirstStreamPlayer(context: Context) {
    waitUntil(SUPPORT_WAIT_MS) { onAllNodes(hasContentDescription("Lunaria", substring = true)).fetchSemanticsNodes().isNotEmpty() }
    onNodeWithContentDescription("Lunaria", substring = true).performClick()
    waitUntil(SUPPORT_WAIT_MS) { onAllNodesWithText(context.getString(R.string.phone_player_chat_hint)).fetchSemanticsNodes().isNotEmpty() }
}

/** The streamer row under the player leads to the channel page; the player shrinks to its strip above the page. */
fun ComposeTestRule.openChannelPageFromPlayer(context: Context) {
    openFirstStreamPlayer(context)
    onNode(hasClickLabel(context.getString(R.string.phone_player_open_channel))).performClick()
    waitUntil(SUPPORT_WAIT_MS) { onAllNodesWithText(context.getString(R.string.watch)).fetchSemanticsNodes().isNotEmpty() }
}

/** Every Media3 picture view under [root]: the identity of the one the page created, to prove it is never recreated. */
fun playerViews(root: View): List<PlayerView> = buildList {
    if (root is PlayerView) add(root)
    if (root is ViewGroup) for (i in 0 until root.childCount) addAll(playerViews(root.getChildAt(i)))
}
