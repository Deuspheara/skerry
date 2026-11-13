package com.twitchplayer.phone.feature.search

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twitchplayer.phone.R
import com.twitchplayer.phone.ui.components.Avatar
import com.twitchplayer.phone.ui.components.CategoryCard
import com.twitchplayer.phone.ui.components.FilterPill
import com.twitchplayer.phone.ui.components.LiveBadge
import com.twitchplayer.phone.ui.components.MessageState
import com.twitchplayer.phone.ui.components.PhoneIcons
import com.twitchplayer.phone.ui.components.itemMotion
import com.twitchplayer.phone.ui.components.searchMorph
import com.twitchplayer.phone.ui.theme.PhoneColors
import com.twitchplayer.tv.core.data.model.Category
import com.twitchplayer.tv.core.data.model.ChannelRef
import com.twitchplayer.tv.core.ui.formatViewers
import com.twitchplayer.tv.feature.search.presentation.ResultsUi
import com.twitchplayer.tv.feature.search.presentation.SearchEvent
import com.twitchplayer.tv.feature.search.presentation.SearchUiState
import com.twitchplayer.tv.feature.search.presentation.SearchViewModel
import com.twitchplayer.tv.feature.search.presentation.VoiceState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import org.koin.compose.viewmodel.koinViewModel

private const val CATEGORY_COLUMNS = 3
private const val STALE_ALPHA = 0.55f
private const val DIM_MS = 180
private const val STALE_AFTER_MS = 150L
private val PROGRESS_HEIGHT = 2.dp
private const val SKELETON_ROWS = 5
private val PillHeight = 40.dp

/**
 * Search, in the same look as the pill that opens it: a back arrow and a field that is that pill grown (the keyboard is already up),
 * then, while nothing is typed, the recent searches (one by one or all) and the channels live now; once something is typed, a row of
 * filters (All / Channels / Categories) and the results as rows with a picture, a live mark and the viewers.
 */
@Composable
fun SearchRoute(
    morphKey: String,
    onBack: () -> Unit,
    onWatch: (String) -> Unit,
    onChannel: (String) -> Unit,
    onCategory: (Category) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = koinViewModel(),
) {
    val state by viewModel.presenter.state.collectAsStateWithLifecycle()
    val onEvent = viewModel.presenter::onEvent
    val text = rememberTextFieldState(state.query)
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val mic = remember { MicPermissionFlow() }
    var filter by rememberSaveable { mutableStateOf(SearchFilter.All) }
    val focus = remember { FocusRequester() }

    // The field owns what is typed (a lagging presenter state must never overwrite it); the presenter only gets the changes.
    LaunchedEffect(text) {
        snapshotFlow { text.text.toString() }.distinctUntilChanged().collect { onEvent(SearchEvent.SetQuery(it)) }
    }
    // What the voice search heard is put in the field (the presenter searches it).
    val voice = state.voice
    val currentVoice by rememberUpdatedState(voice)
    LaunchedEffect(text) {
        snapshotFlow { currentVoice }.filterIsInstance<VoiceState.Heard>().collect { text.setTextAndPlaceCursorAtEnd(it.text) }
    }
    // The field is the point of the page: focused, keyboard up (unless a query is already there to read the results of).
    LaunchedEffect(Unit) {
        if (text.text.isEmpty()) {
            withFrameNanos { }
            focus.requestFocus()
            keyboard?.show()
        }
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (mic.onPermissionResult(granted) == MicStep.StartVoice) onEvent(SearchEvent.StartVoice)
    }
    val startVoice = {
        val held = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        when (mic.onMicTapped(held)) {
            MicStep.StartVoice -> onEvent(SearchEvent.StartVoice)
            MicStep.AskPermission -> micPermission.launch(Manifest.permission.RECORD_AUDIO)
            MicStep.Nothing -> Unit
        }
    }
    val commit: () -> Unit = {
        onEvent(SearchEvent.CommitQuery)
        keyboard?.hide()
    }
    val searching = text.text.isNotBlank()

    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
        Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
            SearchField(
                text = text,
                focus = focus,
                morphKey = morphKey,
                onBack = onBack,
                onSearch = commit,
                onMic = startVoice,
                onClear = { text.setTextAndPlaceCursorAtEnd("") },
            )
            // Stale-while-revalidate: what answered the previous query stays on screen while the next one is on its way, a little dimmed
            // with a thin progress line once the wait is noticeable (a quick answer changes nothing visible but the rows themselves).
            val waiting = searching && state.searching
            val slow = rememberAfterDelay(waiting)
            val dim by animateFloatAsState(if (slow && state.results is ResultsUi.Found) STALE_ALPHA else 1f, tween(DIM_MS), label = "search-stale")
            Box(Modifier.fillMaxWidth().height(PROGRESS_HEIGHT)) {
                if (slow && searching) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().height(PROGRESS_HEIGHT), color = MaterialTheme.colorScheme.primary, trackColor = Color.Transparent)
                }
            }
            if (searching && state.results is ResultsUi.Found) {
                FilterRow(filter) { filter = it }
            }
            // Rows that answer the previous query are dimmed AND say so to a screen reader, and they do not take taps while the new answer is
            // on its way: tapping a row that is about to be replaced would open the wrong thing.
            val stale = slow && state.results is ResultsUi.Found
            val updating = stringResource(R.string.phone_search_updating)
            LazyColumn(
                Modifier.fillMaxSize().imePadding().graphicsLayer { alpha = dim }
                    .then(if (stale) Modifier.semantics { stateDescription = updating }.blockTouches() else Modifier),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                if (state.voice == VoiceState.Idle && mic.denied) {
                    item(key = "mic-denied") {
                        Text(
                            stringResource(R.string.phone_search_mic_denied),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
                if (searching) {
                    results(state, filter, onWatch, onChannel, onCategory, onRetry = { onEvent(SearchEvent.Retry) }, onOpened = commit)
                } else {
                    suggestions(state, onEvent, onPickRecent = { text.setTextAndPlaceCursorAtEnd(it) }, onOpen = { channel -> if (channel.isLive) onWatch(channel.login) else onChannel(channel.login) })
                }
            }
        }
    }
    VoiceDialog(voice, onCancel = { onEvent(SearchEvent.CancelVoice) })
}

/** Swallows every touch before the children see it: nothing under this takes a tap (or scrolls) while it is applied. */
private fun Modifier.blockTouches(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
    }
}

/** True once [active] has been true for [STALE_AFTER_MS] without a break: a short wait shows nothing, a long one shows the dim and the progress line. */
@Composable
private fun rememberAfterDelay(active: Boolean): Boolean {
    var slow by remember { mutableStateOf(false) }
    LaunchedEffect(active) {
        if (active) {
            delay(STALE_AFTER_MS)
            slow = true
        } else {
            slow = false
        }
    }
    return slow
}

/** The pill of the header, grown: back arrow outside it on the left, the search glyph, the text, a clear button and the microphone. */
@Composable
private fun SearchField(
    text: androidx.compose.foundation.text.input.TextFieldState,
    focus: FocusRequester,
    morphKey: String,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onMic: () -> Unit,
    onClear: () -> Unit,
) {
    val hint = stringResource(R.string.phone_search_hint)
    Row(Modifier.fillMaxWidth().height(56.dp).padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back)) }
        BasicTextField(
            state = text,
            lineLimits = TextFieldLineLimits.SingleLine,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            onKeyboardAction = { onSearch() },
            modifier = Modifier.weight(1f).searchMorph(morphKey).height(PillHeight).focusRequester(focus).semantics {
                // Named while empty; once something is typed, the typed text is what the screen reader reads (a description would replace it).
                if (text.text.isEmpty()) contentDescription = hint
            },
            decorator = { inner ->
                Row(
                    Modifier.fillMaxSize().clip(RoundedCornerShape(PillHeight / 2)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (text.text.isEmpty()) {
                            Text(
                                stringResource(R.string.phone_search_hint),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        inner()
                    }
                    if (text.text.isNotEmpty()) {
                        IconButton(onClick = onClear, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.phone_search_clear_field), modifier = Modifier.size(18.dp))
                        }
                    }
                    IconButton(onClick = onMic, modifier = Modifier.size(40.dp)) {
                        Icon(PhoneIcons.Mic, contentDescription = stringResource(R.string.voice_dialog), modifier = Modifier.size(20.dp))
                    }
                }
            },
        )
    }
}

@Composable
private fun FilterRow(selected: SearchFilter, onSelect: (SearchFilter) -> Unit) {
    val labels = mapOf(
        SearchFilter.All to stringResource(R.string.phone_search_filter_all),
        SearchFilter.Channels to stringResource(R.string.sec_channels),
        SearchFilter.Categories to stringResource(R.string.sec_categories),
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)) {
        items(SearchFilter.entries.toList(), key = { it.name }) { option -> FilterPill(labels.getValue(option), option == selected, { onSelect(option) }) }
    }
}

/** Nothing typed: what was searched before, and who is live. */
private fun LazyListScope.suggestions(state: SearchUiState, onEvent: (SearchEvent) -> Unit, onPickRecent: (String) -> Unit, onOpen: (ChannelRef) -> Unit) {
    if (state.recents.isNotEmpty()) {
        item(key = "recents-header") {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.phone_search_recent), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
                TextButton(onClick = { onEvent(SearchEvent.ClearRecents) }) { Text(stringResource(R.string.phone_search_clear_recents)) }
            }
        }
        items(state.recents, key = { "recent-$it" }) { recent -> RecentRow(recent, onPick = { onPickRecent(recent) }, onRemove = { onEvent(SearchEvent.RemoveRecent(recent)) }, modifier = Modifier.itemMotion(this)) }
    }
    val live = (state.results as? ResultsUi.Trends)?.channels.orEmpty()
    if (live.isNotEmpty()) {
        item(key = "live-header") { SectionTitle(stringResource(R.string.phone_search_live_now)) }
        items(live, key = { "live-${it.id}" }) { channel -> ChannelResultRow(channel, onClick = { onOpen(channel) }, modifier = Modifier.itemMotion(this)) }
    }
    if (state.recents.isEmpty() && live.isEmpty()) {
        item(key = "hint") {
            Text(
                stringResource(R.string.phone_search_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            )
        }
    }
}

/** Something typed: loading, failed, nothing found, or the results the filter asks for. */
private fun LazyListScope.results(
    state: SearchUiState,
    filter: SearchFilter,
    onWatch: (String) -> Unit,
    onChannel: (String) -> Unit,
    onCategory: (Category) -> Unit,
    onRetry: () -> Unit,
    onOpened: () -> Unit,
) {
    val found = state.results as? ResultsUi.Found
    val noResult = (state.results as? ResultsUi.Trends)?.noResult == true
    when {
        state.failed -> item(key = "failed") {
            MessageState(
                title = stringResource(R.string.state_error),
                modifier = Modifier.fillMaxWidth().height(320.dp),
                actionLabel = stringResource(R.string.state_retry),
                onAction = onRetry,
            )
        }

        // The skeleton is only for a first load with nothing to show: later queries keep the previous answer on screen until theirs arrives.
        found == null && !noResult -> {
            items(SKELETON_ROWS, key = { "skeleton-$it" }) { SkeletonRow() }
        }

        found == null -> item(key = "no-result") { NoResults(state.query) }

        else -> foundResults(found, state.query, filter, onWatch, onChannel, onCategory, onOpened)
    }
}

/** Nothing found: the query echoed back (so a typo is visible), a hint and a symbol, not one bare line. */
@Composable
private fun NoResults(query: String) {
    MessageState(
        title = stringResource(R.string.phone_no_results_for, query.trim()),
        body = stringResource(R.string.phone_no_results_hint),
        icon = androidx.compose.material.icons.Icons.Filled.Search,
        modifier = Modifier.fillMaxWidth().height(320.dp),
    )
}

private fun LazyListScope.foundResults(
    found: ResultsUi.Found,
    query: String,
    filter: SearchFilter,
    onWatch: (String) -> Unit,
    onChannel: (String) -> Unit,
    onCategory: (Category) -> Unit,
    onOpened: () -> Unit,
) {
    val showChannels = filter != SearchFilter.Categories && found.channels.isNotEmpty()
    val showCategories = filter != SearchFilter.Channels && found.categories.isNotEmpty()
    if (!showChannels && !showCategories) {
        item(key = "filtered-empty") { NoResults(query) }
    }
    if (showChannels) {
        if (filter == SearchFilter.All) item(key = "channels-header") { SectionTitle(stringResource(R.string.sec_channels)) }
        items(found.channels, key = { "channel-${it.id}" }) { channel ->
            ChannelResultRow(channel, onClick = {
                onOpened()
                if (channel.isLive) onWatch(channel.login) else onChannel(channel.login)
            }, modifier = Modifier.itemMotion(this))
        }
    }
    if (showCategories) {
        if (filter == SearchFilter.All) item(key = "categories-header") { SectionTitle(stringResource(R.string.sec_categories)) }
        items(found.categories.chunked(CATEGORY_COLUMNS), key = { row -> "categories-${row.first().id}" }) { row ->
            Row(Modifier.itemMotion(this).fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { category ->
                    CategoryCard(category, onClick = {
                        onOpened()
                        onCategory(category)
                    }, modifier = Modifier.weight(1f))
                }
                repeat(CATEGORY_COLUMNS - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp).semantics { heading() })
}

/** One recent search: the clock, the text (a tap searches it again), and an x that forgets only this one. */
@Composable
private fun RecentRow(query: String, onPick: () -> Unit, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    val remove = stringResource(R.string.phone_search_remove_recent, query)
    Row(modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onPick).padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(PhoneIcons.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Text(query, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 14.dp))
        IconButton(onClick = onRemove) { Icon(Icons.Filled.Clear, contentDescription = remove, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)) }
    }
}

/**
 * A channel: its picture (ringed in the live colour while it streams, nothing drawn over the face), the name, and under it the LIVE mark
 * with the viewers, or that it is offline.
 */
@Composable
private fun ChannelResultRow(channel: ChannelRef, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val viewers = remember(channel.viewers) { formatViewers(channel.viewers) }
    val description = if (channel.isLive) stringResource(R.string.cd_live_channel, channel.name) else stringResource(R.string.cd_offline, channel.name)
    Row(
        modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = description }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val ring = if (channel.isLive) Modifier.border(2.dp, PhoneColors.Live, CircleShape).padding(3.dp) else Modifier
        Box(ring) { Avatar(channel.avatarUrl, channel.name, 48.dp) }
        Column(Modifier.weight(1f)) {
            Text(channel.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (channel.isLive) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // The mark keeps its size at any font size: it is a label, not reading text.
                    CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 1f)) { LiveBadge() }
                    Text(stringResource(R.string.phone_search_viewers, viewers), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Text(stringResource(R.string.phone_channel_offline), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SkeletonRow() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.width(140.dp).height(12.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh))
            Box(Modifier.width(90.dp).height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh))
        }
    }
}

/** Listening, what was heard, or "not recognized": one small dialog for the three voice states. */
@Composable
private fun VoiceDialog(voice: VoiceState, onCancel: () -> Unit) {
    if (voice == VoiceState.Idle) return
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.voice_dialog)) },
        text = {
            when (voice) {
                VoiceState.Listening -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(R.string.chat_listening))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                is VoiceState.Heard -> Text(voice.text, style = MaterialTheme.typography.titleMedium)

                VoiceState.Failed -> Text(stringResource(R.string.voice_not_heard), color = MaterialTheme.colorScheme.error)

                VoiceState.Idle -> Unit
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.voice_close)) } },
    )
}
