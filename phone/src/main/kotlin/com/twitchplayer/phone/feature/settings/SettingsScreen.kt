package com.twitchplayer.phone.feature.settings

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twitchplayer.phone.BuildConfig
import com.twitchplayer.phone.R
import com.twitchplayer.phone.ui.theme.ThemeMode
import com.twitchplayer.phone.ui.theme.ThemePreference
import com.twitchplayer.tv.core.data.model.AnimatedEmoteLimit
import com.twitchplayer.tv.core.data.model.ChatDensity
import com.twitchplayer.tv.core.data.model.ChatMode
import com.twitchplayer.tv.core.data.model.ChatSettings
import com.twitchplayer.tv.core.data.model.ChatTextSize
import com.twitchplayer.tv.core.ui.AppLocales
import com.twitchplayer.tv.feature.settings.presentation.SettingsEvent
import com.twitchplayer.tv.feature.settings.presentation.SettingsViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/** Settings: account, appearance (theme, language, animations), chat, playback and about. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel(),
    themePreference: ThemePreference = koinInject(),
) {
    val state by viewModel.presenter.state.collectAsStateWithLifecycle()
    val themeMode by themePreference.mode.collectAsStateWithLifecycle()
    val onEvent = viewModel.presenter::onEvent
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    var dialog by rememberSaveable { mutableStateOf<SettingsDialog?>(null) }
    val activity = LocalActivity.current
    val context = LocalContext.current

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.phone_nav_settings)) },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item { Header(R.string.tile_account) }
            item {
                val account = state.account
                ListItem(
                    headlineContent = { Text(if (account != null) account else stringResource(R.string.state_signin_title)) },
                    supportingContent = { if (account != null) Text(stringResource(R.string.phone_settings_signed_in)) },
                    trailingContent = {
                        if (account != null) {
                            OutlinedButton(onClick = { dialog = SettingsDialog.SignOut }) { Text(stringResource(R.string.sign_out)) }
                        } else {
                            Button(onClick = onSignIn) { Text(stringResource(R.string.state_signin)) }
                        }
                    },
                    colors = transparentItem(),
                )
            }
            item { Header(R.string.phone_settings_appearance) }
            item { ValueRow(R.string.phone_settings_theme, stringResource(themeMode.label())) { dialog = SettingsDialog.Theme } }
            item {
                val tag = remember(state, activity) { AppLocales.current(context) }
                ValueRow(R.string.tile_language, if (tag == null) stringResource(R.string.lang_system) else AppLocales.nativeName(tag)) { dialog = SettingsDialog.Language }
            }
            item { SwitchRow(R.string.tile_animations, state.animationsEnabled) { onEvent(SettingsEvent.ToggleAnimations) } }

            item { Header(R.string.tile_chat) }
            item {
                ValueRow(R.string.phone_settings_chat_mode, stringResource(state.chatMode.label())) { dialog = SettingsDialog.ChatMode }
            }
            item { ValueRow(R.string.chat_opt_text, stringResource(state.chat.textSize.label())) { dialog = SettingsDialog.ChatTextSize } }
            item { ValueRow(R.string.chat_opt_spacing, stringResource(state.chat.density.label())) { dialog = SettingsDialog.ChatDensity } }
            item {
                ValueRow(R.string.chat_opt_background, stringResource(R.string.phone_percent, state.chat.backgroundOpacity)) { dialog = SettingsDialog.ChatBackground }
            }
            item { SwitchRow(R.string.chat_opt_badges, state.chat.showBadges) { onEvent(SettingsEvent.UpdateChat { it.copy(showBadges = !it.showBadges) }) } }
            item { SwitchRow(R.string.chat_opt_time, state.chat.showTimestamps) { onEvent(SettingsEvent.UpdateChat { it.copy(showTimestamps = !it.showTimestamps) }) } }
            item { SwitchRow(R.string.chat_opt_animated, state.chat.animatedEmotes) { onEvent(SettingsEvent.UpdateChat { it.copy(animatedEmotes = !it.animatedEmotes) }) } }
            if (state.chat.animatedEmotes) {
                item { ValueRow(R.string.chat_opt_limit, state.chat.animatedLimit.count.toString()) { dialog = SettingsDialog.ChatLimit } }
            }

            item { Header(R.string.phone_settings_playback) }
            item { SwitchRow(R.string.tile_stats, state.showStats) { onEvent(SettingsEvent.ToggleStats) } }
            item { SwitchRow(R.string.tile_raids, state.followRaids) { onEvent(SettingsEvent.ToggleFollowRaids) } }

            item { Header(R.string.phone_settings_about) }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.app_name)) },
                    supportingContent = { Text(stringResource(R.string.phone_about_version, BuildConfig.VERSION_NAME)) },
                    colors = transparentItem(),
                )
            }
            item {
                Text(
                    stringResource(R.string.phone_about_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            item { ValueRow(R.string.phone_licenses, null) { dialog = SettingsDialog.Licenses } }
        }
    }

    val update = { transform: (ChatSettings) -> ChatSettings -> onEvent(SettingsEvent.UpdateChat(transform)) }
    when (dialog) {
        SettingsDialog.SignOut -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.dialog_signout_title, state.account.orEmpty())) },
            confirmButton = {
                TextButton(onClick = {
                    dialog = null
                    onSignOut()
                }) { Text(stringResource(R.string.dialog_signout)) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.dialog_cancel)) } },
        )

        SettingsDialog.Theme -> ChoiceDialog(
            title = R.string.phone_settings_theme,
            options = ThemeMode.entries.map { it to stringResource(it.label()) },
            selected = themeMode,
            onSelect = { themePreference.set(it) },
            onDismiss = { dialog = null },
        )

        SettingsDialog.Language -> {
            val current = remember { AppLocales.current(context) }
            ChoiceDialog(
                title = R.string.tile_language,
                options = listOf<Pair<String?, String>>(null to stringResource(R.string.lang_system)) + AppLocales.supported.map { it to AppLocales.nativeName(it) },
                selected = current,
                onSelect = { tag -> activity?.let { AppLocales.set(it, tag) } },
                onDismiss = { dialog = null },
            )
        }

        SettingsDialog.ChatMode -> ChoiceDialog(
            title = R.string.phone_settings_chat_mode,
            options = ChatMode.entries.map { it to stringResource(it.label()) },
            selected = state.chatMode,
            onSelect = { onEvent(SettingsEvent.SetChatMode(it)) },
            onDismiss = { dialog = null },
        )

        SettingsDialog.ChatTextSize -> ChoiceDialog(
            title = R.string.chat_opt_text,
            options = ChatTextSize.entries.map { it to stringResource(it.label()) },
            selected = state.chat.textSize,
            onSelect = { size -> update { it.copy(textSize = size) } },
            onDismiss = { dialog = null },
        )

        SettingsDialog.ChatDensity -> ChoiceDialog(
            title = R.string.chat_opt_spacing,
            options = ChatDensity.entries.map { it to stringResource(it.label()) },
            selected = state.chat.density,
            onSelect = { density -> update { it.copy(density = density) } },
            onDismiss = { dialog = null },
        )

        SettingsDialog.ChatBackground -> ChoiceDialog(
            title = R.string.chat_opt_background,
            options = ChatSettings.BACKGROUND_OPACITIES.map { it to stringResource(R.string.phone_percent, it) },
            selected = state.chat.backgroundOpacity,
            onSelect = { value -> update { it.copy(backgroundOpacity = value) } },
            onDismiss = { dialog = null },
        )

        SettingsDialog.ChatLimit -> ChoiceDialog(
            title = R.string.chat_opt_limit,
            options = AnimatedEmoteLimit.entries.map { it to it.count.toString() },
            selected = state.chat.animatedLimit,
            onSelect = { limit -> update { it.copy(animatedLimit = limit) } },
            onDismiss = { dialog = null },
        )

        SettingsDialog.Licenses -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.phone_licenses)) },
            text = { Text(stringResource(R.string.phone_licenses_body)) },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.voice_close)) } },
        )

        null -> Unit
    }
}

/** Which settings dialog is open (saved, so a rotation keeps it). */
private enum class SettingsDialog { SignOut, Theme, Language, ChatMode, ChatTextSize, ChatDensity, ChatBackground, ChatLimit, Licenses }

@Composable
private fun Header(title: Int) {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp).semantics { heading() },
        )
    }
}

@Composable
private fun transparentItem() = ListItemDefaults.colors(containerColor = Color.Transparent)

@Composable
private fun ValueRow(title: Int, value: String?, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = value?.let { { Text(it) } },
        colors = transparentItem(),
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
    )
}

@Composable
private fun SwitchRow(title: Int, checked: Boolean, onToggle: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        colors = transparentItem(),
        modifier = Modifier.selectable(selected = checked, role = Role.Switch, onClick = onToggle),
    )
}

/** A radio list in a dialog: choosing applies at once and closes the dialog. */
@Composable
private fun <T> ChoiceDialog(title: Int, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            androidx.compose.foundation.lazy.LazyColumn(Modifier.selectableGroup()) {
                items(options.size) { index ->
                    val (value, label) = options[index]
                    Row(
                        Modifier.fillMaxWidth().selectable(
                            selected = value == selected,
                            role = Role.RadioButton,
                            onClick = {
                                onSelect(value)
                                onDismiss()
                            },
                        ).padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = value == selected, onClick = null)
                        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) } },
    )
}

private fun ThemeMode.label(): Int = when (this) {
    ThemeMode.Dark -> R.string.phone_theme_dark
    ThemeMode.Light -> R.string.phone_theme_light
    ThemeMode.System -> R.string.phone_theme_system
}

private fun ChatMode.label(): Int = when (this) {
    ChatMode.Hidden -> R.string.chat_hidden
    ChatMode.Compact -> R.string.chat_compact
    ChatMode.Overlay -> R.string.chat_overlay
}

private fun ChatTextSize.label(): Int = when (this) {
    ChatTextSize.Small -> R.string.opt_small
    ChatTextSize.Medium -> R.string.opt_medium
    ChatTextSize.Large -> R.string.opt_large
}

private fun ChatDensity.label(): Int = when (this) {
    ChatDensity.Compact -> R.string.opt_tight
    ChatDensity.Standard -> R.string.opt_normal
    ChatDensity.Relaxed -> R.string.opt_loose
}
