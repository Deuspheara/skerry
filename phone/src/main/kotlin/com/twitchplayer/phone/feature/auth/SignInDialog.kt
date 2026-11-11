package com.twitchplayer.phone.feature.auth

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
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
 * there from another device. The code's countdown is the grant's own `expires_in`; when it ends the presenter ends the flow and the
 * root tells the viewer.
 */
@Composable
fun SignInDialog(code: DeviceCodeResponse, onCancel: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val remaining by produceState(initialValue = code.expiresIn, code.deviceCode) {
        while (value > 0) {
            delay(ONE_SECOND_MS)
            value -= 1
        }
    }
    val spelled = code.userCode.toList().joinToString(" ")
    val copyLabel = stringResource(R.string.phone_signin_copy)
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.dialog_signin)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.phone_signin_step_open), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { runCatching { uriHandler.openUri(code.verificationUri) } }, modifier = Modifier.fillMaxWidth()) {
                    Icon(PhoneIcons.OpenInNew, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text(stringResource(R.string.phone_signin_open))
                }
                Text(stringResource(R.string.phone_signin_step_code), style = MaterialTheme.typography.bodyMedium)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(start = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = code.userCode,
                        style = MaterialTheme.typography.headlineMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = CODE_LETTER_SPACING,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).semantics { contentDescription = spelled },
                    )
                    IconButton(
                        onClick = { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(copyLabel, code.userCode))) } },
                    ) { Icon(PhoneIcons.Copy, contentDescription = copyLabel) }
                }
                if (remaining > 0) {
                    LinearProgressIndicator(progress = { remaining / code.expiresIn.coerceAtLeast(1).toFloat() }, modifier = Modifier.fillMaxWidth())
                    Text(
                        stringResource(R.string.phone_signin_expires, countdownText(remaining)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(stringResource(R.string.phone_signin_expired), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.dialog_cancel)) } },
    )
}

private const val ONE_SECOND_MS = 1_000L
private val CODE_LETTER_SPACING = 4.sp
