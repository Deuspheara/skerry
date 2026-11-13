package com.twitchplayer.phone.feature.settings

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twitchplayer.phone.BuildConfig
import com.twitchplayer.phone.R
import com.twitchplayer.phone.ui.components.DetailHeader
import com.twitchplayer.phone.ui.components.PhoneIcons
import com.twitchplayer.phone.ui.components.TitleHeader
import com.twitchplayer.phone.ui.components.clickableItem
import com.twitchplayer.phone.ui.theme.ThemeMode
import com.twitchplayer.phone.ui.theme.ThemePreference
import com.twitchplayer.tv.core.data.model.AnimatedEmoteLimit
import com.twitchplayer.tv.core.data.model.ChatDensity
import com.twitchplayer.tv.core.data.model.ChatMode
import com.twitchplayer.tv.core.data.model.ChatSettings
import com.twitchplayer.tv.core.data.model.ChatTextSize
import com.twitchplayer.tv.core.data.model.FeedAutoplay
import com.twitchplayer.tv.core.ui.AppLocales
import com.twitchplayer.tv.feature.settings.presentation.SettingsEvent
import com.twitchplayer.tv.feature.settings.presentation.SettingsViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Settings, the way the video apps lay them out: a profile card (who is signed in, and the button to sign in or out), then rounded
 * sections whose rows lead with an icon, say their current value and end with a chevron; switches sit in the same rows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(
    onBack: () -> Unit,
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
    val switcher = remember(activity) { activity?.let(::AppLocalesSwitcher) }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { DetailHeader(onBack = onBack, title = stringResource(R.string.phone_nav_settings), behavior = scrollBehavior) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item(key = "account") {
                ProfileCard(state.account, onSignIn = onSignIn, onSignOut = { dialog = SettingsDialog.SignOut })
            }
            item(key = "playback") {
                Section(R.string.phone_settings_playback, Icons.Filled.PlayArrow) {
                    SwitchRow(R.string.tile_stats, state.showStats) { onEvent(SettingsEvent.ToggleStats) }
                    Rule()
                    SwitchRow(R.string.tile_raids, state.followRaids) { onEvent(SettingsEvent.ToggleFollowRaids) }
                    Rule()
                    ValueRow(R.string.phone_settings_feed_autoplay, stringResource(state.feedAutoplay.label())) { dialog = SettingsDialog.FeedAutoplay }
                    Text(
                        stringResource(R.string.phone_settings_feed_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    )
                }
            }
            item(key = "chat") {
                Section(R.string.tile_chat, PhoneIcons.Chat) {
                    ValueRow(R.string.phone_settings_chat_mode, stringResource(state.chatMode.label())) { dialog = SettingsDialog.ChatMode }
                    Rule()
                    ValueRow(R.string.chat_opt_text, stringResource(state.chat.textSize.label())) { dialog = SettingsDialog.ChatTextSize }
                    Rule()
                    ValueRow(R.string.chat_opt_spacing, stringResource(state.chat.density.label())) { dialog = SettingsDialog.ChatDensity }
                    Rule()
                    ValueRow(R.string.chat_opt_background, stringResource(R.string.phone_percent, state.chat.backgroundOpacity)) { dialog = SettingsDialog.ChatBackground }
                    Rule()
                    SwitchRow(R.string.chat_opt_badges, state.chat.showBadges) { onEvent(SettingsEvent.UpdateChat { it.copy(showBadges = !it.showBadges) }) }
                    Rule()
                    SwitchRow(R.string.chat_opt_time, state.chat.showTimestamps) { onEvent(SettingsEvent.UpdateChat { it.copy(showTimestamps = !it.showTimestamps) }) }
                    Rule()
                    SwitchRow(R.string.chat_opt_animated, state.chat.animatedEmotes) { onEvent(SettingsEvent.UpdateChat { it.copy(animatedEmotes = !it.animatedEmotes) }) }
                    if (state.chat.animatedEmotes) {
                        Rule()
                        ValueRow(R.string.chat_opt_limit, state.chat.animatedLimit.count.toString()) { dialog = SettingsDialog.ChatLimit }
                    }
                }
            }
            item(key = "appearance") {
                Section(R.string.phone_settings_appearance, PhoneIcons.Palette) {
                    ValueRow(R.string.phone_settings_theme, stringResource(themeMode.label())) { dialog = SettingsDialog.Theme }
                    Rule()
                    SwitchRow(R.string.tile_animations, state.animationsEnabled) { onEvent(SettingsEvent.ToggleAnimations) }
                }
            }
            item(key = "language") {
                Section(R.string.phone_settings_language, PhoneIcons.Language) {
                    // The section is called Language: the row says which one.
                    val tag = switcher?.current()
                    ValueRow(R.string.tile_language, if (tag == null) stringResource(R.string.lang_system) else AppLocales.nativeName(tag)) { dialog = SettingsDialog.Language }
                }
            }
            item(key = "about") {
                Section(R.string.phone_settings_about, Icons.Filled.Info) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Column {
                            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.bodyLarge)
                            Text(stringResource(R.string.phone_about_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Rule()
                    Text(
                        stringResource(R.string.phone_about_disclaimer),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    )
                    Rule()
                    ValueRow(R.string.phone_licenses, null) { dialog = SettingsDialog.Licenses }
                }
            }
        }
    }

    val update = { transform: (ChatSettings) -> ChatSettings -> onEvent(SettingsEvent.UpdateChat(transform)) }
    when (dialog) {
        SettingsDialog.SignOut -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.dialog_signout_title, state.account.orEmpty())) },
            text = { Text(stringResource(R.string.phone_signout_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        dialog = null
                        onSignOut()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.dialog_signout)) }
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
            val current = remember { switcher?.current() }
            ChoiceDialog(
                title = R.string.tile_language,
                options = languageTags(AppLocales.supported).map { tag -> tag to if (tag == null) stringResource(R.string.lang_system) else AppLocales.nativeName(tag) },
                selected = current,
                onSelect = { tag -> switcher?.let { chooseLanguage(it, tag) } },
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

        SettingsDialog.FeedAutoplay -> ChoiceDialog(
            title = R.string.phone_settings_feed_autoplay,
            options = FeedAutoplay.entries.map { it to stringResource(it.label()) },
            selected = state.feedAutoplay,
            onSelect = { onEvent(SettingsEvent.SetFeedAutoplay(it)) },
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

/** The list of a choice dialog, for UI tests that must scroll it. */
const val CHOICE_LIST_TAG = "settings-choice-list"

/** Which settings dialog is open (saved, so a rotation keeps it). */
private enum class SettingsDialog { SignOut, Theme, Language, ChatMode, ChatTextSize, ChatDensity, ChatBackground, ChatLimit, FeedAutoplay, Licenses }

/** The viewer: a round picture (the initial, or a person), who they are, and the button that signs in or out. */
@Composable
private fun ProfileCard(account: String?, onSignIn: () -> Unit, onSignOut: () -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                val initial = account?.firstOrNull()?.uppercaseChar()
                if (initial != null) {
                    Text(initial.toString(), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                } else {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(32.dp))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(account ?: stringResource(R.string.state_signin_title), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (account != null) Text(stringResource(R.string.phone_settings_signed_in), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (account != null) {
                PillButton(stringResource(R.string.sign_out), filled = false, onClick = onSignOut)
            } else {
                PillButton(stringResource(R.string.state_signin), filled = true, onClick = onSignIn)
            }
        }
    }
}

/** The app's own button: a 40 dp pill, filled for the main action and outlined for the other. */
@Composable
private fun PillButton(label: String, filled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier.minimumInteractiveComponentSize().clip(shape)
            .then(if (filled) Modifier.background(MaterialTheme.colorScheme.primary) else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, shape))
            .clickable(role = Role.Button, onClick = onClick)
            .height(40.dp).padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}

/** A labelled section: a heading above a rounded surface holding its rows. */
@Composable
private fun Section(title: Int, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // The section's icon is in its heading; the rows below are text and values only, so every row starts at the same edge.
        Row(Modifier.padding(horizontal = 16.dp).semantics(mergeDescendants = true) { heading() }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(content = content)
        }
    }
}

@Composable
private fun Rule() = HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)

/** A row that opens a choice: icon, title, the current value under it, and a chevron that mirrors in right-to-left languages. */
@Composable
private fun ValueRow(title: Int, value: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickableItem(onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            if (value != null) Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SwitchRow(title: Int, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().minimumInteractiveComponentSize().selectable(selected = checked, role = Role.Switch, onClick = onToggle).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(title), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** A radio list in a dialog: choosing applies at once and closes the dialog. Every row is at least 48 dp tall. */
@Composable
private fun <T> ChoiceDialog(title: Int, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            LazyColumn(Modifier.selectableGroup().testTag(CHOICE_LIST_TAG)) {
                items(options) { (value, label) ->
                    Row(
                        Modifier.fillMaxWidth().minimumInteractiveComponentSize().selectable(
                            selected = value == selected,
                            role = Role.RadioButton,
                            onClick = {
                                onSelect(value)
                                onDismiss()
                            },
                        ),
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

private fun FeedAutoplay.label(): Int = when (this) {
    FeedAutoplay.WifiOnly -> R.string.phone_feed_autoplay_wifi
    FeedAutoplay.Always -> R.string.phone_feed_autoplay_always
    FeedAutoplay.Off -> R.string.phone_feed_autoplay_off
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
