package com.twitchplayer.phone.feature.auth

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.os.Build
import android.os.PersistableBundle

/**
 * The sign-in code on the clipboard. It is short-lived and only useful on twitch.tv/activate, but a clipboard is readable by other apps and
 * shown in the system's clipboard preview, so the copy is marked sensitive (Android 13+: the preview and clipboard history hide it) and is
 * taken back when the dialog closes or the code expires, **only while it is still the primary clip** (a viewer who copied something else
 * since keeps it). Android 10+ lets only the focused app read the clipboard: when the app is in the background the primary clip cannot be
 * read, so nothing is cleared then (the sensitive flag still applies).
 */
object CodeClipboard {
    /** The clip to put on the clipboard for [code], flagged sensitive on Android 13+. */
    fun clip(label: String, code: String): ClipData = ClipData.newPlainText(label, code).also { clip ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
        }
    }

    /** True when [current] is exactly the clip [clip] put there for [code]: one item, the code as its text. */
    fun isOurs(current: ClipData?, code: String): Boolean = current != null && current.itemCount == 1 && current.getItemAt(0).text?.toString() == code

    /** Clears the clipboard when it still holds [code] and says whether it did. */
    fun clearIfOurs(manager: ClipboardManager, code: String): Boolean {
        if (!isOurs(manager.primaryClip, code)) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            manager.clearPrimaryClip()
        } else {
            manager.setPrimaryClip(ClipData.newPlainText("", ""))
        }
        return true
    }
}
