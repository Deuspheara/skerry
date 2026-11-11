package com.twitchplayer.phone.feature.search

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twitchplayer.phone.R
import com.twitchplayer.phone.feature.home.CHANNEL_DIVIDER_INSET
import com.twitchplayer.phone.ui.components.CategoryCard
import com.twitchplayer.phone.ui.components.ChannelListItem
import com.twitchplayer.phone.ui.components.PhoneIcons
import com.twitchplayer.phone.ui.components.SectionHeader
import com.twitchplayer.tv.core.data.model.Category
import com.twitchplayer.tv.core.data.model.ChannelRef
import com.twitchplayer.tv.feature.search.presentation.ResultsUi
import com.twitchplayer.tv.feature.search.presentation.SearchEvent
import com.twitchplayer.tv.feature.search.presentation.SearchViewModel
import com.twitchplayer.tv.feature.search.presentation.VoiceState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import org.koin.compose.viewmodel.koinViewModel

/** Search: a field with voice input, recent searches, trending channels, and channel and category results. */
@Composable
fun SearchRoute(onChannel: (String) -> Unit, onCategory: (Category) -> Unit, modifier: Modifier = Modifier, viewModel: SearchViewModel = koinViewModel()) {
    val state by viewModel.presenter.state.collectAsStateWithLifecycle()
    val onEvent = viewModel.presenter::onEvent
    val text = rememberTextFieldState(state.query)
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val mic = remember { MicPermissionFlow() }

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

    Scaffold(modifier = modifier, containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "field") {
                OutlinedTextField(
                    state = text,
                    lineLimits = TextFieldLineLimits.SingleLine,
                    placeholder = { Text(stringResource(R.string.search_field)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        Row {
                            if (text.text.isNotEmpty()) {
                                IconButton(onClick = { text.setTextAndPlaceCursorAtEnd("") }) {
                                    Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.key_clear))
                                }
                            }
                            IconButton(onClick = startVoice) { Icon(PhoneIcons.Mic, contentDescription = stringResource(R.string.voice_dialog)) }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    onKeyboardAction = {
                        onEvent(SearchEvent.CommitQuery)
                        keyboard?.hide()
                    },
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
            if (state.query.isBlank() && state.recents.isEmpty()) {
                item(key = "hint") {
                    Text(
                        stringResource(R.string.phone_search_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            }
            if (mic.denied) {
                item(key = "mic-denied") {
                    Text(
                        stringResource(R.string.phone_search_mic_denied),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            if (state.query.isBlank() && state.recents.isNotEmpty()) {
                item(key = "recents-header") { SectionHeader(stringResource(R.string.phone_search_recent), Modifier.padding(horizontal = 16.dp)) }
                item(key = "recents") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
                        items(state.recents, key = { it }) { recent ->
                            AssistChip(
                                onClick = {
                                    text.setTextAndPlaceCursorAtEnd(recent)
                                    onEvent(SearchEvent.CommitQuery)
                                    keyboard?.hide()
                                },
                                label = { Text(recent) },
                            )
                        }
                    }
                }
            }
            when (val results = state.results) {
                is ResultsUi.Trends -> {
                    if (results.noResult) {
                        item(key = "no-result") {
                            Text(
                                stringResource(R.string.state_no_result),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                    if (results.channels.isNotEmpty()) {
                        item(key = "trends-header") { SectionHeader(stringResource(R.string.sec_trends), Modifier.padding(horizontal = 16.dp)) }
                        items(results.channels, key = { "trend-${it.id}" }) { channel ->
                            ChannelRow(channel, onClick = { onChannel(channel.login) })
                        }
                    }
                }

                is ResultsUi.Found -> {
                    if (results.channels.isEmpty() && results.categories.isEmpty()) {
                        item(key = "found-empty") {
                            Text(
                                stringResource(R.string.state_no_result),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                    if (results.channels.isNotEmpty()) {
                        item(key = "channels-header") { SectionHeader(stringResource(R.string.sec_channels), Modifier.padding(horizontal = 16.dp)) }
                        items(results.channels, key = { "channel-${it.id}" }) { channel ->
                            ChannelRow(channel, onClick = { onChannel(channel.login) })
                        }
                    }
                    if (results.categories.isNotEmpty()) {
                        item(key = "categories-header") { SectionHeader(stringResource(R.string.sec_categories), Modifier.padding(horizontal = 16.dp)) }
                        item(key = "categories") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
                                items(results.categories, key = { it.id }) { category ->
                                    CategoryCard(category, onClick = { onCategory(category) }, modifier = Modifier.width(120.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    VoiceDialog(voice, onCancel = { onEvent(SearchEvent.CancelVoice) })
}

/** A channel row with a hairline under its text, so a long list reads as rows (and a short one does not float). */
@Composable
private fun ChannelRow(channel: ChannelRef, onClick: () -> Unit) {
    Column {
        ChannelListItem(channel, onClick = onClick, modifier = Modifier.padding(horizontal = 8.dp))
        HorizontalDivider(Modifier.padding(start = CHANNEL_DIVIDER_INSET, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
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
                VoiceState.Listening -> androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
