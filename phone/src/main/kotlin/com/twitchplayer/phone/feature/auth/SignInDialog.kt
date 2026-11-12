package com.twitchplayer.phone.feature.auth

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.twitchplayer.phone.R
import com.twitchplayer.phone.ui.components.PhoneIcons
import com.twitchplayer.tv.core.network.auth.DeviceCodeResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Device-code sign-in: open Twitch's activation page (the URL Twitch returns already carries the code), or type the big code
 * there from another device. The countdown is a deadline on [clock] (see [SignInCountdown]), kept across a rotation, so it is right
 * after the app was in the background. The page is only opened when its address is a twitch.tv one ([isTwitchUrl]).
 */
@Composable
fun SignInDialog(code: DeviceCodeResponse, onCancel: () -> Unit, clock: ElapsedClock = ElapsedClock.Real) {
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboard.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val deadline = rememberSaveable(code.deviceCode) { SignInCountdown.deadlineFor(code.expiresIn, clock) }
    val countdown = remember(deadline, clock) { SignInCountdown(deadline, clock) }
    val remaining by produceState(initialValue = countdown.remainingSeconds(), countdown) {
        while (true) {
            value = countdown.remainingSeconds()
            if (value == 0) break
            delay(TICK_MS)
        }
    }
    var openFailed by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    // The code stays on the clipboard only while this dialog is open and the code valid.
    val everCopied = remember { mutableStateOf(false) }
    val context = LocalContext.current
    DisposableEffect(code.userCode) {
        onDispose { if (everCopied.value) clearCopiedCode(context, code.userCode) }
    }
    DisposableEffect(remaining == 0) {
        if (remaining == 0 && everCopied.value) clearCopiedCode(context, code.userCode)
        onDispose { }
    }
    LaunchedEffect(copied) {
        if (copied) {
            delay(COPIED_MS)
            copied = false
        }
    }
    val spelled = code.userCode.toList().joinToString(" ")
    val copyLabel = stringResource(R.string.phone_signin_copy)
    val copiedLabel = stringResource(R.string.phone_signin_copied)
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.dialog_signin)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.phone_signin_step_open), style = MaterialTheme.typography.bodyMedium)
                Button(
                    onClick = { openFailed = !(isTwitchUrl(code.verificationUri) && runCatching { uriHandler.openUri(code.verificationUri) }.isSuccess) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(PhoneIcons.OpenInNew, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text(stringResource(R.string.phone_signin_open))
                }
                if (openFailed) {
                    Text(stringResource(R.string.phone_signin_open_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Text(stringResource(R.string.phone_signin_step_code), style = MaterialTheme.typography.bodyMedium)
                // The code is centred between two 48 dp slots (an empty one and the copy button), so it is centred in the box.
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.size(COPY_SLOT))
                    Text(
                        text = code.userCode,
                        style = MaterialTheme.typography.headlineLarge,
                        // The code is the point of this dialog: full-contrast text, not the dimmer body colour.
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = CODE_LETTER_SPACING,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).padding(vertical = 12.dp).semantics { contentDescription = spelled },
                    )
                    IconButton(
                        onClick = {
                            scope.launch { clipboard.setClipEntry(ClipEntry(CodeClipboard.clip(copyLabel, code.userCode))) }
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            copied = true
                            everCopied.value = true
                        },
                        modifier = Modifier.size(COPY_SLOT),
                    ) {
                        if (copied) {
                            Icon(Icons.Filled.Check, contentDescription = copiedLabel, tint = MaterialTheme.colorScheme.primary)
                        } else {
                            Icon(PhoneIcons.Copy, contentDescription = copyLabel)
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (remaining > 0) {
                        LinearProgressIndicator(progress = { remaining / code.expiresIn.coerceAtLeast(1).toFloat() }, modifier = Modifier.fillMaxWidth())
                        Text(
                            text = if (copied) copiedLabel else stringResource(R.string.phone_signin_expires, countdownText(remaining)),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (copied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    } else {
                        Text(stringResource(R.string.phone_signin_expired), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.dialog_cancel)) } },
    )
}

private const val TICK_MS = 500L
private const val COPIED_MS = 2_000L
private val COPY_SLOT = 48.dp
private val CODE_LETTER_SPACING = 4.sp

private fun clearCopiedCode(context: Context, code: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    runCatching { CodeClipboard.clearIfOurs(manager, code) }
}
