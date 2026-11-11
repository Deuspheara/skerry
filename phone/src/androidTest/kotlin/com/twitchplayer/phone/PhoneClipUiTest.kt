package com.twitchplayer.phone

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.twitchplayer.phone.feature.player.ClipRoute
import com.twitchplayer.phone.feature.player.STAGE_TAG
import com.twitchplayer.phone.ui.theme.PhoneTheme
import com.twitchplayer.phone.ui.theme.ThemeMode
import com.twitchplayer.tv.feature.player.data.stream.ClipResolver
import com.twitchplayer.tv.feature.player.data.stream.ClipSource
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/** The clip page on its own (no navigation): the error pages and their Retry, through a resolver the test controls. */
@RunWith(AndroidJUnit4::class)
class PhoneClipUiTest {
    @get:Rule val rule = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun string(id: Int) = context.getString(id)

    /** Fails with [failure] the first [failures] times, then answers with the bundled demo clip. */
    private class ScriptedResolver(private val failure: Throwable, private val failures: Int) : ClipResolver {
        var calls = 0
            private set

        override suspend fun resolve(slug: String): List<ClipSource> {
            calls++
            if (calls <= failures) throw failure
            return listOf(ClipSource(720, 60f, "asset:///demo_stream.mp4"))
        }
    }

    private fun show(resolver: ClipResolver) {
        rule.setContent { PhoneTheme(ThemeMode.Dark, reducedMotion = true) { ClipRoute("AbCd-1_x", "A great play", onBack = {}, resolver = resolver) } }
    }

    private fun waitForText(text: String) = rule.waitUntil(WAIT_MS) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    @Test fun aClipTheNetworkCouldNotFetchSaysSoAndOffersRetry() {
        show(ScriptedResolver(IOException("down"), failures = 1))

        waitForText(string(R.string.vod_failed_net_line))
        rule.onNodeWithText(string(R.string.state_retry)).assertIsDisplayed()
    }

    @Test fun aClipThatIsGoneSaysItIsUnavailableAndStillOffersRetry() {
        show(ScriptedResolver(IllegalStateException("gone"), failures = 1))

        waitForText(string(R.string.clip_unavailable_line))
        rule.onNodeWithText(string(R.string.state_retry)).assertIsDisplayed()
    }

    @Test fun retryResolvesTheClipAgainAndThePlayerAppearsOnceItAnswers() {
        val resolver = ScriptedResolver(IOException("down"), failures = 1)
        show(resolver)
        waitForText(string(R.string.state_retry))

        rule.onNodeWithText(string(R.string.state_retry)).performClick()

        rule.waitUntil(WAIT_MS) { rule.onAllNodesWithText(string(R.string.state_retry)).fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithTag(STAGE_TAG).assertIsDisplayed()
        assertEquals(2, resolver.calls)
    }

    private companion object {
        const val WAIT_MS = 20_000L
    }
}
