package com.twitchplayer.phone

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.twitchplayer.phone.feature.auth.CodeClipboard
import com.twitchplayer.phone.feature.auth.SignInDialog
import com.twitchplayer.tv.core.network.auth.DeviceCodeResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The sign-in code on the real clipboard: marked sensitive, and taken back when the dialog closes, but only while it is still what the
 * clipboard holds. Runs in a focused activity (Android 10+ lets only the focused app read the clipboard).
 */
@RunWith(AndroidJUnit4::class)
class CodeClipboardTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val manager get() = rule.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val code = DeviceCodeResponse("device", "ABCDEFGH", "https://www.twitch.tv/activate?device-code=ABCDEFGH", 1800, 5)

    private fun primaryText(): String? = manager.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()

    @Test fun theCopiedCodeIsMarkedSensitiveOnAndroid13AndLater() {
        val clip = CodeClipboard.clip("label", "ABCDEFGH")

        assertEquals("ABCDEFGH", clip.getItemAt(0).text.toString())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            assertTrue(clip.description.extras!!.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE))
        }
    }

    @Test fun theCodeIsClearedWhenItIsStillTheClipboardContent() {
        rule.runOnUiThread { manager.setPrimaryClip(CodeClipboard.clip("label", "ABCDEFGH")) }

        val cleared = rule.runOnUiThread { CodeClipboard.clearIfOurs(manager, "ABCDEFGH") }

        assertTrue(cleared)
        rule.runOnUiThread { assertFalse("still holds the code: ${primaryText()}", primaryText() == "ABCDEFGH") }
    }

    @Test fun somethingTheViewerCopiedSinceIsLeftAlone() {
        rule.runOnUiThread { manager.setPrimaryClip(ClipData.newPlainText("note", "milk, eggs")) }

        val cleared = rule.runOnUiThread { CodeClipboard.clearIfOurs(manager, "ABCDEFGH") }

        assertFalse(cleared)
        rule.runOnUiThread { assertEquals("milk, eggs", primaryText()) }
    }

    @Test fun anEmptyClipboardIsLeftAlone() {
        rule.runOnUiThread { manager.clearPrimaryClip() }

        assertFalse(rule.runOnUiThread { CodeClipboard.clearIfOurs(manager, "ABCDEFGH") })
    }

    @Test fun closingTheDialogTakesTheCopiedCodeBack() {
        var open by mutableStateOf(true)
        rule.setContent { if (open) SignInDialog(code, onCancel = { open = false }) }
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.phone_signin_copy)).performClick()
        rule.waitUntil(WAIT_MS) { rule.runOnUiThread { primaryText() } == "ABCDEFGH" }
        assertNotNull(rule.runOnUiThread { primaryText() })

        rule.runOnUiThread { open = false }
        rule.waitForIdle()

        rule.runOnUiThread { assertFalse("the code is still on the clipboard", primaryText() == "ABCDEFGH") }
    }

    private companion object {
        const val WAIT_MS = 5_000L
    }
}
