package com.twitchplayer.tv.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.twitchplayer.tv.R
import com.twitchplayer.tv.core.designsystem.component.LocalDialogClose
import com.twitchplayer.tv.core.designsystem.component.TvButton
import com.twitchplayer.tv.core.designsystem.component.TvDialog
import com.twitchplayer.tv.core.designsystem.icon.TvIcons
import com.twitchplayer.tv.core.designsystem.theme.TvColors
import com.twitchplayer.tv.core.designsystem.theme.TvType
import com.twitchplayer.tv.core.network.auth.DeviceCodeResponse

/** Quit confirmation: power icon + two buttons, focus on "stay" so a stray OK never quits. */
@Composable
fun QuitDialog(onStay: () -> Unit, onQuit: () -> Unit) {
    val stay = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { stay.requestFocus() } }
    TvDialog(description = stringResource(R.string.dialog_quit), onDismiss = onStay, width = 460.dp) {
        Icon(TvIcons.Power, null, tint = TvColors.Live, modifier = Modifier.size(48.dp))
        val close = LocalDialogClose.current
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvButton(stringResource(R.string.dialog_stay), null, { close(onStay) }, Modifier.focusRequester(stay), primary = true)
            TvButton(stringResource(R.string.dialog_quit_confirm), TvIcons.Power, onQuit)
        }
    }
}

/** Sign-out confirmation: same shape as Quit, focus on "cancel" so a stray OK never signs out. */
@Composable
fun SignOutDialog(account: String, onCancel: () -> Unit, onSignOut: () -> Unit) {
    val cancel = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { cancel.requestFocus() } }
    TvDialog(description = stringResource(R.string.dialog_signout), onDismiss = onCancel, width = 460.dp) {
        Icon(TvIcons.LogOut, null, tint = TvColors.Text, modifier = Modifier.size(40.dp))
        Text(stringResource(R.string.dialog_signout_title, account), style = TvType.Tile, color = TvColors.Text, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        val close = LocalDialogClose.current
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvButton(stringResource(R.string.dialog_cancel), null, { close(onCancel) }, Modifier.focusRequester(cancel), primary = true)
            TvButton(stringResource(R.string.sign_out), TvIcons.LogOut, { close(onSignOut) })
        }
    }
}

/** Device-code dialog: the one place where on-screen text is unavoidable (URL + code). */
@Composable
fun SignInDialog(code: DeviceCodeResponse, onCancel: () -> Unit) {
    val close = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { close.requestFocus() } }
    TvDialog(description = stringResource(R.string.dialog_signin), onDismiss = onCancel, width = 360.dp) {
        Text(code.verificationUri.removePrefix("https://"), style = TvType.Title, color = TvColors.TextSecondary)
        Text(code.userCode, style = TvType.Display, color = TvColors.Text)
        val dismiss = LocalDialogClose.current
        TvButton(stringResource(R.string.voice_close), TvIcons.Close, { dismiss(onCancel) }, Modifier.focusRequester(close), primary = true)
    }
}
