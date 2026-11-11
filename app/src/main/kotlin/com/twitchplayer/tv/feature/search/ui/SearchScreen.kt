package com.twitchplayer.tv.feature.search.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.twitchplayer.tv.R
import com.twitchplayer.tv.core.data.model.Category
import com.twitchplayer.tv.core.data.model.ChannelRef
import com.twitchplayer.tv.core.designsystem.component.BottomFade
import com.twitchplayer.tv.core.designsystem.component.Chip
import com.twitchplayer.tv.core.designsystem.component.SectionHeader
import com.twitchplayer.tv.core.designsystem.component.TopBar
import com.twitchplayer.tv.core.designsystem.icon.TvIcons
import com.twitchplayer.tv.core.designsystem.theme.TvColors
import com.twitchplayer.tv.core.designsystem.theme.TvDimens
import com.twitchplayer.tv.core.designsystem.theme.TvShapes
import com.twitchplayer.tv.core.designsystem.theme.TvType
import com.twitchplayer.tv.core.ui.FocusMemory
import com.twitchplayer.tv.core.ui.redirectFocusOnExit
import com.twitchplayer.tv.core.ui.rememberFocusMemory
import com.twitchplayer.tv.feature.search.presentation.ResultsUi
import com.twitchplayer.tv.feature.search.presentation.SearchUiState
import com.twitchplayer.tv.feature.search.presentation.SearchViewModel
import com.twitchplayer.tv.feature.search.presentation.VoiceState
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SearchRoute(
    onChannel: (ChannelRef) -> Unit,
    onCategory: (Category) -> Unit,
    viewModel: SearchViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.startVoice()
    }
    SearchScreen(
        state = state,
        onKey = viewModel::append,
        onSpace = viewModel::space,
        onDelete = viewModel::delete,
        onClear = viewModel::clear,
        onVoice = {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                viewModel.startVoice()
            } else {
                permission.launch(Manifest.permission.RECORD_AUDIO)
            }
        },
        onRecent = viewModel::setQuery,
        onChannel = {
            viewModel.commitQuery()
            onChannel(it)
        },
        onCategory = {
            viewModel.commitQuery()
            onCategory(it)
        },
        onCancelVoice = viewModel::cancelVoice,
    )
}

@Composable
private fun SearchScreen(
    state: SearchUiState,
    onKey: (String) -> Unit,
    onSpace: () -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
    onVoice: () -> Unit,
    onRecent: (String) -> Unit,
    onChannel: (ChannelRef) -> Unit,
    onCategory: (Category) -> Unit,
    onCancelVoice: () -> Unit,
) {
    val mic = remember { FocusRequester() }
    val firstKey = remember { FocusRequester() }
    val keyboardGroup = remember { FocusRequester() }
    val resultsMemory = rememberFocusMemory()
    androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { firstKey.requestFocus() } }
    Box(
        Modifier
            .fillMaxSize()
            // Hardware / system keyboards type straight into the query.
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when {
                    event.key == Key.Backspace -> {
                        onDelete()
                        true
                    }

                    event.utf16CodePoint > 0x20 && Character.isLetterOrDigit(event.utf16CodePoint) -> {
                        onKey(String(Character.toChars(event.utf16CodePoint)))
                        true
                    }

                    else -> false
                }
            },
    ) {
        Row(
            Modifier.fillMaxSize().padding(start = TvDimens.ContentStart, top = 89.dp, end = TvDimens.ContentEnd),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(Modifier.width(534.dp)) {
                QueryBox(state.query)
                Spacer12()
                Recents(state.recents, onRecent)
                Spacer12()
                Keyboard(
                    onKey,
                    onSpace,
                    onDelete,
                    onClear,
                    onVoice,
                    micModifier = Modifier.focusRequester(mic),
                    firstKeyModifier = Modifier.focusRequester(firstKey),
                    modifier = Modifier
                        .focusRequester(keyboardGroup)
                        // Right from the last key column enters the results (no geometric neighbour there).
                        .redirectFocusOnExit(FocusDirection.Right) { resultsMemory.focusRequester }
                        .focusRestorer(firstKey)
                        .focusGroup(),
                )
            }
            Results(
                state.results,
                onChannel,
                onCategory,
                Modifier.weight(1f),
                keyboardGroup,
                resultsMemory,
            )
        }
        BottomFade(Modifier.align(Alignment.BottomStart))
        TopBar(Modifier.align(Alignment.TopStart))
        if (state.voice != VoiceState.Idle) VoiceDialog(state.voice, onCancelVoice)
    }
}

@Composable
private fun Spacer12() = Box(Modifier.height(12.dp))

@Composable
private fun QueryBox(query: String) {
    val searchLabel = stringResource(R.string.search_field)
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(TvColors.Base, TvShapes.Chip)
            .border(0.5.dp, TvColors.Divider, TvShapes.Chip)
            .padding(horizontal = 12.dp)
            .semantics(mergeDescendants = true) { contentDescription = if (query.isEmpty()) searchLabel else query },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(TvIcons.Search, null, tint = if (query.isEmpty()) TvColors.TextSecondary else TvColors.Purple, modifier = Modifier.size(20.dp))
        if (query.isEmpty()) {
            // Cursor first, then the hint, so typing starts where the cursor is.
            Box(Modifier.width(1.5.dp).height(22.dp).background(TvColors.Purple))
            Text(searchLabel, style = TvType.Query, color = TvColors.TextSecondary.copy(alpha = 0.6f), maxLines = 1, softWrap = false)
        } else {
            Text(query, style = TvType.Query, color = TvColors.Text, maxLines = 1, softWrap = false)
            Box(Modifier.width(1.5.dp).height(22.dp).background(TvColors.Purple))
        }
    }
}

@Composable
private fun Recents(recents: List<String>, onRecent: (String) -> Unit) {
    // The row keeps its height when empty so the keyboard never shifts.
    LazyRow(Modifier.fillMaxWidth().height(36.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp, vertical = 2.dp)) {
        items(recents, key = { it }) { recent ->
            Chip(recent, { onRecent(recent) }, compact = true, leadingIcon = TvIcons.Clock)
        }
    }
}

@Composable
private fun Results(
    results: ResultsUi,
    onChannel: (ChannelRef) -> Unit,
    onCategory: (Category) -> Unit,
    modifier: Modifier,
    keyboardGroup: FocusRequester,
    memory: FocusMemory,
) {
    val rowModifier = Modifier
    // Left from the results goes back to the keyboard, which restores its last used key.
    LazyColumn(modifier.redirectFocusOnExit(FocusDirection.Left) { keyboardGroup }.focusGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when (results) {
            is ResultsUi.Trends -> {
                item(key = "h-trend", contentType = "header") { ResultsHeader(TvIcons.Flame, stringResource(R.string.sec_trends), noResult = results.noResult) }
                items(results.channels, key = { "t:" + it.id }, contentType = { "channel" }) { channel ->
                    ChannelResultRow(channel, { onChannel(channel) }, with(memory) { rowModifier.track("c:${channel.id}", default = channel === results.channels.first()) })
                }
            }

            is ResultsUi.Found -> {
                if (results.channels.isNotEmpty()) {
                    item(key = "h-ch", contentType = "header") { ResultsHeader(TvIcons.User, stringResource(R.string.sec_channels)) }
                    items(results.channels, key = { "c:" + it.id }, contentType = { "channel" }) { channel ->
                        ChannelResultRow(channel, { onChannel(channel) }, with(memory) { rowModifier.track("c:${channel.id}", default = channel === results.channels.first()) })
                    }
                }
                if (results.categories.isNotEmpty()) {
                    item(key = "h-ca", contentType = "header") { ResultsHeader(TvIcons.Grid, stringResource(R.string.sec_categories)) }
                    items(results.categories, key = { "k:" + it.id }, contentType = { "category" }) { category ->
                        val first = results.channels.isEmpty() && category === results.categories.first()
                        CategoryResultRow(category, { onCategory(category) }, with(memory) { rowModifier.track("k:${category.id}", default = first) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultsHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, noResult: Boolean = false) {
    Column {
        Box(Modifier.height(40.dp), contentAlignment = Alignment.CenterStart) { SectionHeader(icon, description) }
        if (noResult) {
            Row(Modifier.height(40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(TvIcons.SearchOff, null, tint = TvColors.TextSecondary, modifier = Modifier.size(28.dp))
                Text(stringResource(R.string.state_no_result), style = TvType.Label, color = TvColors.TextSecondary)
            }
        }
    }
}
