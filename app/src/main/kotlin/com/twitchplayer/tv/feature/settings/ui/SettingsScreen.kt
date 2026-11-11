package com.twitchplayer.tv.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.twitchplayer.tv.R
import com.twitchplayer.tv.app.SignOutDialog
import com.twitchplayer.tv.core.data.model.ChatMode
import com.twitchplayer.tv.core.data.model.ChatSettings
import com.twitchplayer.tv.core.designsystem.component.Avatar
import com.twitchplayer.tv.core.designsystem.component.LocalDialogClose
import com.twitchplayer.tv.core.designsystem.component.TileCard
import com.twitchplayer.tv.core.designsystem.component.TopBar
import com.twitchplayer.tv.core.designsystem.component.TvDialog
import com.twitchplayer.tv.core.designsystem.icon.TvIcons
import com.twitchplayer.tv.core.designsystem.theme.LocalMotion
import com.twitchplayer.tv.core.designsystem.theme.TvColors
import com.twitchplayer.tv.core.designsystem.theme.TvDimens
import com.twitchplayer.tv.core.designsystem.theme.TvShapes
import com.twitchplayer.tv.core.designsystem.theme.TvType
import com.twitchplayer.tv.core.ui.AppLocales
import com.twitchplayer.tv.core.ui.LocalAppActions
import com.twitchplayer.tv.core.ui.TvFocusable
import com.twitchplayer.tv.core.ui.focusFill
import com.twitchplayer.tv.core.ui.staggerIn
import com.twitchplayer.tv.feature.player.ui.ChatSettingsPanel
import com.twitchplayer.tv.feature.settings.presentation.SettingsUiState
import com.twitchplayer.tv.feature.settings.presentation.SettingsViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SettingsRoute(viewModel: SettingsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    val actions = LocalAppActions.current
    SettingsScreen(
        state = state,
        onChatMode = viewModel::setChatMode,
        onChatOptions = viewModel::updateChat,
        onAnimations = viewModel::toggleAnimations,
        onPlayerEngine = viewModel::togglePlayerEngine,
        onStats = viewModel::toggleStats,
        onFollowRaids = viewModel::toggleFollowRaids,
        onSignIn = actions::signIn,
        onSignOut = actions::signOut,
        onQuit = actions::requestQuit,
    )
}

@Composable
private fun SettingsScreen(
    state: SettingsUiState,
    onChatMode: (ChatMode) -> Unit,
    onChatOptions: ((ChatSettings) -> ChatSettings) -> Unit,
    onAnimations: () -> Unit,
    onPlayerEngine: () -> Unit,
    onStats: () -> Unit,
    onFollowRaids: () -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onQuit: () -> Unit,
) {
    val firstSegment = remember { FocusRequester() }
    val entrance = remember { com.twitchplayer.tv.core.ui.StaggerGate() }
    var chatOptionsOpen by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    var languageOpen by remember { mutableStateOf(false) }
    var playerOptionsOpen by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val currentLanguage = remember { AppLocales.current(context) }
    val onAccount = { if (state.account == null) onSignIn() else confirmSignOut = true }
    androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { firstSegment.requestFocus() } }
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.padding(start = TvDimens.ContentStart - 8.dp, top = TvDimens.TopBar + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Avatar(state.account ?: "?", null, size = 60.dp)
                if (state.account != null) Text(state.account, style = TvType.ProfileName, color = TvColors.Text, maxLines = 1)
            }
            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.staggerIn(0, entrance)) { ChatTile(state.chatMode, onChatMode, { chatOptionsOpen = true }, firstSegment) }
                    TileCard(
                        modifier = Modifier.staggerIn(1, entrance),
                        icon = TvIcons.Sparkles,
                        label = stringResource(R.string.tile_animations),
                        description = stringResource(if (state.animationsEnabled) R.string.cd_animations_on else R.string.cd_animations_off),
                        onClick = onAnimations,
                        width = TILE_WIDTH,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Switch(state.animationsEnabled)
                            Text(stringResource(if (state.animationsEnabled) R.string.opt_on else R.string.opt_off), style = TvType.Label, color = TvColors.TextSecondary)
                        }
                    }
                    val languageName = currentLanguage?.let(AppLocales::nativeName) ?: stringResource(R.string.lang_system)
                    TileCard(
                        modifier = Modifier.staggerIn(2, entrance),
                        icon = TvIcons.Globe,
                        label = stringResource(R.string.tile_language),
                        description = stringResource(R.string.tile_language) + ": " + languageName,
                        onClick = { languageOpen = true },
                        width = TILE_WIDTH,
                    ) { ActionLabel(TvIcons.ChevronRight, languageName) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    val nativeBuilt = com.twitchplayer.tv.BuildConfig.NATIVE_PLAYER_ENABLED
                    val bottomWidth = TILE_WIDTH
                    TileCard(
                        modifier = Modifier.staggerIn(1, entrance),
                        width = bottomWidth,
                        icon = TvIcons.User,
                        label = stringResource(R.string.tile_account),
                        description = state.account?.let { stringResource(R.string.cd_account_signed_in, it) } ?: stringResource(R.string.cd_account_signed_out),
                        onClick = onAccount,
                    ) { ActionLabel(if (state.account == null) TvIcons.ChevronRight else TvIcons.LogOut, stringResource(if (state.account == null) R.string.state_signin else R.string.sign_out)) }
                    // Player options (engine, statistics, raids) open in a dialog: the screen has no room for more tiles.
                    val engineName = stringResource(if (state.playerEngine == com.twitchplayer.tv.core.data.model.PlayerEngine.Native) R.string.engine_native else R.string.engine_webview)
                    TileCard(
                        modifier = Modifier.staggerIn(2, entrance),
                        icon = TvIcons.Quality,
                        label = stringResource(R.string.tile_player),
                        description = stringResource(R.string.tile_player) + if (nativeBuilt) ": $engineName" else "",
                        onClick = { playerOptionsOpen = true },
                        width = bottomWidth,
                    ) { ActionLabel(TvIcons.ChevronRight, if (nativeBuilt) engineName else stringResource(R.string.tile_player)) }
                    TileCard(
                        modifier = Modifier.staggerIn(3, entrance),
                        icon = TvIcons.Power,
                        label = stringResource(R.string.tile_quit),
                        description = stringResource(R.string.tile_quit),
                        onClick = onQuit,
                        width = bottomWidth,
                    ) { ActionLabel(TvIcons.ChevronRight, stringResource(R.string.dialog_quit)) }
                }
            }
        }
        TopBar(Modifier.align(Alignment.TopStart))
    }
    if (confirmSignOut && state.account != null) {
        SignOutDialog(state.account, onCancel = { confirmSignOut = false }, onSignOut = {
            confirmSignOut = false
            onSignOut()
        })
    }
    if (playerOptionsOpen) {
        PlayerOptionsDialog(state, onEngine = onPlayerEngine, onStats = onStats, onFollowRaids = onFollowRaids, onDismiss = { playerOptionsOpen = false })
    }
    if (languageOpen) {
        LanguageDialog(currentLanguage, onDismiss = { languageOpen = false }) { tag ->
            languageOpen = false
            if (tag != currentLanguage) (context as? android.app.Activity)?.let { AppLocales.set(it, tag) }
        }
    }
    if (chatOptionsOpen) {
        TvDialog(description = stringResource(R.string.player_chat_options), onDismiss = { chatOptionsOpen = false }, width = 300.dp, title = stringResource(R.string.player_chat_options), titleInset = 8.dp) {
            ChatSettingsPanel(state.chat, onChange = onChatOptions, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Chat default: a tile whose three modes are separate focusable segments (selected = purple, focus = ring). */
@Composable
private fun ChatTile(mode: ChatMode, onMode: (ChatMode) -> Unit, onOptions: () -> Unit, firstFocus: FocusRequester) {
    Box(
        Modifier
            .width(TILE_WIDTH).height(156.dp)
            .background(TvColors.Tile, TvShapes.Control)
            .semantics { contentDescription = "" },
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.align(Alignment.TopCenter).padding(top = TILE_TOP), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(TvIcons.ChatCompact, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(32.dp))
            Text(stringResource(R.string.tile_chat), style = TvType.Tile, color = TvColors.Text)
            // The focused segment (or the current mode) is named under the row: the icons alone are not enough.
            var focusedLabel by remember { mutableStateOf<String?>(null) }
            val optionsLabel = stringResource(R.string.player_chat_options)
            // 8 dp gaps: a focused segment's scale and ring never run into its neighbours.
            Row(Modifier.focusGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChatMode.entries.forEach { entry ->
                    val label = chatModeLabel(entry)
                    Segment(
                        entry,
                        label,
                        selected = entry == mode,
                        onClick = { onMode(entry) },
                        modifier = (if (entry == mode) Modifier.focusRequester(firstFocus) else Modifier)
                            .onFocusChanged {
                                if (it.hasFocus) {
                                    focusedLabel = label
                                } else if (focusedLabel == label) {
                                    focusedLabel = null
                                }
                            },
                    )
                }
                OptionsSegment(
                    onOptions,
                    Modifier.onFocusChanged {
                        if (it.hasFocus) {
                            focusedLabel = optionsLabel
                        } else if (focusedLabel == optionsLabel) {
                            focusedLabel = null
                        }
                    },
                )
            }
            Text(
                focusedLabel ?: chatModeLabel(mode),
                style = TvType.Meta,
                color = TvColors.TextSecondary,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
    }
}

/** Opens the chat options (text size, spacing, emotes...); an action, so it never looks selected. */
@Composable
private fun OptionsSegment(onClick: () -> Unit, modifier: Modifier = Modifier) {
    TvFocusable(
        onClick = onClick,
        shape = TvShapes.Chip,
        description = stringResource(R.string.player_chat_options),
        modifier = modifier
            .size(40.dp, 28.dp)
            .background(TvColors.Tile, TvShapes.Chip)
            .drawBehind { drawRoundRect(TvColors.ChipLine, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()), cornerRadius = CornerRadius(2.dp.toPx())) },
    ) {
        Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
            Icon(TvIcons.ChatOptions, null, tint = TvColors.TextSecondary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun chatModeLabel(mode: ChatMode): String = stringResource(
    when (mode) {
        ChatMode.Hidden -> R.string.chat_hidden
        ChatMode.Compact -> R.string.chat_compact
        ChatMode.Overlay -> R.string.chat_overlay
    },
)

/** Tile action: small icon plus the verb, so the tile says what OK will do. */
@Composable
private fun ActionLabel(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    // A chevron follows the value ("opens"); any other glyph leads it (what OK does, e.g. sign out).
    val trailing = icon == TvIcons.ChevronRight
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!trailing) Icon(icon, null, tint = TvColors.TextSecondary, modifier = Modifier.size(16.dp))
        Text(text, style = TvType.Label, color = TvColors.TextSecondary, maxLines = 1)
        if (trailing) Icon(icon, null, tint = TvColors.TextSecondary, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun Segment(mode: ChatMode, label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    TvFocusable(
        onClick = onClick,
        shape = TvShapes.Chip,
        description = label,
        role = Role.RadioButton,
        modifier = modifier
            .size(40.dp, 28.dp)
            .background(if (selected) TvColors.Purple else TvColors.Tile, TvShapes.Chip)
            .drawBehind {
                if (selected) {
                    drawRoundRect(androidx.compose.ui.graphics.Color.White, Offset(7.dp.toPx(), size.height - 4.dp.toPx()), Size(size.width - 14.dp.toPx(), 2.dp.toPx()), CornerRadius(1.dp.toPx()))
                } else {
                    drawRoundRect(TvColors.ChipLine, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()), cornerRadius = CornerRadius(2.dp.toPx()))
                }
            }
            .semantics { this.selected = selected },
    ) {
        Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
            Icon(
                when (mode) {
                    ChatMode.Hidden -> TvIcons.ChatOff
                    ChatMode.Compact -> TvIcons.ChatCompact
                    ChatMode.Overlay -> TvIcons.ChatOverlay
                },
                null,
                tint = if (selected) androidx.compose.ui.graphics.Color.White else TvColors.TextSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** On/off switch with a check/minus knob (state never relies on color alone). */
@Composable
private fun Switch(on: Boolean) {
    val motion = LocalMotion.current
    val progress = androidx.compose.animation.core.animateFloatAsState(if (on) 1f else 0f, motion.fast(), label = "switch")
    // The knob travels towards the end: right in left-to-right languages, left in right-to-left ones.
    val direction = if (androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl) -1f else 1f
    Box(
        Modifier
            .size(56.dp, 28.dp)
            .background(if (on) TvColors.Purple else TvColors.Alt2, CircleShape)
            .drawBehind { if (!on) drawCircle(TvColors.ChipLine, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx())) },
    ) {
        Box(
            Modifier
                .padding(2.dp)
                .size(24.dp)
                .graphicsLayer { translationX = progress.value * 28.dp.toPx() * direction }
                .background(TvColors.Text, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (on) TvIcons.Check else TvIcons.Minus, null, tint = if (on) TvColors.Purple else TvColors.Body, modifier = Modifier.size(14.dp))
        }
    }
}

// Three tiles + two 16 dp gaps span the content width (rail edge + 24 dp to the 48 dp right safe margin).
private val TILE_WIDTH = 269.dp

/** Top inset shared by every tile so icons, titles and values line up across a row. */
private val TILE_TOP = 18.dp

/** Two tiles filling the width of three (with the 16 dp gap): the grid has no empty cell. */
private val WIDE_TILE_WIDTH = (TILE_WIDTH * 3 + 16.dp * 2 - 16.dp) / 2

/** Language list: "system" first, then every translation in its own name; focus starts on the current choice. */
@Composable
private fun LanguageDialog(current: String?, onDismiss: () -> Unit, onPick: (String?) -> Unit) {
    val selected = remember { FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { selected.requestFocus() } }
    val options = remember { listOf<String?>(null) + AppLocales.supported }
    val systemLabel = stringResource(R.string.lang_system)
    TvDialog(description = stringResource(R.string.tile_language), onDismiss = onDismiss, width = 340.dp, title = stringResource(R.string.tile_language)) {
        val close = LocalDialogClose.current
        androidx.compose.foundation.lazy.LazyColumn(
            Modifier.fillMaxWidth().height(360.dp)
                .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    // Bottom fade: the list continues below.
                    drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(0.85f to androidx.compose.ui.graphics.Color.Black, 1f to androidx.compose.ui.graphics.Color.Transparent), blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
                },
            state = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = maxOf(0, options.indexOf(current) - 3)),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            // Room for the focus ring and scale at the list's edges.
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp, horizontal = 4.dp),
        ) {
            items(options.size, key = { options[it] ?: "system" }) { index ->
                val tag = options[index]
                val active = tag == current
                TvFocusable(
                    onClick = { close { onPick(tag) } },
                    shape = TvShapes.Chip,
                    role = Role.RadioButton,
                    focusScale = 1f,
                    modifier = Modifier
                        .fillMaxWidth().height(40.dp)
                        .then(if (active) Modifier.focusRequester(selected) else Modifier)
                        .background(if (active) TvColors.Alt2 else TvColors.Base, TvShapes.Chip)
                        .semantics { this.selected = active },
                ) { focus ->
                    Row(
                        Modifier.matchParentSize().focusFill(focus, TvColors.TileFocused, TvShapes.Chip).padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(tag?.let(AppLocales::nativeName) ?: systemLabel, style = TvType.Label, color = TvColors.Text, maxLines = 1, modifier = Modifier.weight(1f))
                        if (active) Icon(TvIcons.Check, null, tint = TvColors.Purple, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

/** Player options: engine (when the build has both), playback statistics, following raids. Each row acts on OK and stays open. */
@Composable
private fun PlayerOptionsDialog(state: SettingsUiState, onEngine: () -> Unit, onStats: () -> Unit, onFollowRaids: () -> Unit, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    val nativeBuilt = com.twitchplayer.tv.BuildConfig.NATIVE_PLAYER_ENABLED
    val title = stringResource(R.string.tile_player)
    TvDialog(description = title, onDismiss = onDismiss, width = 360.dp, title = title) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            var firstRow = true
            fun Modifier.initial(): Modifier = if (firstRow) {
                firstRow = false
                focusRequester(first)
            } else {
                this
            }
            if (nativeBuilt) {
                val engineName = stringResource(if (state.playerEngine == com.twitchplayer.tv.core.data.model.PlayerEngine.Native) R.string.engine_native else R.string.engine_webview)
                OptionRow(stringResource(R.string.player_engine), engineName, null, onEngine, Modifier.initial())
            }
            OptionRow(stringResource(R.string.tile_stats), null, state.showStats, onStats, Modifier.initial())
            OptionRow(stringResource(R.string.tile_raids), null, state.followRaids, onFollowRaids, Modifier.initial())
        }
    }
}

/** A dialog row: [label], then either [value] text or an on / off switch ([on] non-null). */
@Composable
private fun OptionRow(label: String, value: String?, on: Boolean?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val stateText = on?.let { stringResource(if (it) R.string.opt_on else R.string.opt_off) } ?: value.orEmpty()
    TvFocusable(
        onClick = onClick,
        shape = TvShapes.Chip,
        focusScale = 1f,
        description = "$label: $stateText",
        modifier = modifier.fillMaxWidth().height(44.dp).background(TvColors.Base, TvShapes.Chip),
    ) { focus ->
        Row(
            Modifier.matchParentSize().focusFill(focus, TvColors.TileFocused, TvShapes.Chip).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(label, style = TvType.Label, color = TvColors.Text, maxLines = 1, modifier = Modifier.weight(1f))
            if (on != null) Switch(on)
            Text(stateText, style = TvType.Label, color = TvColors.TextSecondary, maxLines = 1)
        }
    }
}
