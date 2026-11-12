package com.twitchplayer.phone.feature.player

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.twitchplayer.phone.R
import com.twitchplayer.phone.ui.components.PhoneIcons
import com.twitchplayer.phone.ui.theme.LocalReducedMotion
import com.twitchplayer.tv.feature.player.data.emotes.Emote
import com.twitchplayer.tv.feature.player.data.emotes.EmoteSection
import com.twitchplayer.tv.feature.player.data.emotes.EmoteSource
import com.twitchplayer.tv.feature.player.data.emotes.UserEmoteAccess
import com.twitchplayer.tv.feature.player.presentation.EmotePickerState
import com.twitchplayer.tv.feature.player.ui.EmoteLayer
import kotlin.math.roundToInt

/** Where an emote of the picker comes from, for the preview line and the screen reader. */
@Immutable
private data class Origin(val source: EmoteSource, val section: EmoteSection)

/** One thing in the picker's grid: a heading, or an emote (with the list it is from). */
private sealed interface Entry {
    val key: String

    data class Header(val label: Int) : Entry {
        override val key = "h:$label"
    }

    data class Cell(val emote: Emote, val origin: Origin, val index: Int) : Entry {
        override val key = "c:${origin.source}:${origin.section}:$index:${emote.name}"
    }
}

/**
 * The emote picker that takes the keyboard's place: a search field, a tab per source (the viewer's recents first, then Twitch, 7TV,
 * BTTV and FFZ), and a grid of stills. A tap puts the emote in the message and the panel stays open for the next one; a long press
 * shows the emote large (animated when animations are allowed) with its name and where it comes from, with a button to insert it.
 * Emotes the viewer cannot use are listed apart and dimmed, and cannot be inserted.
 */
@Composable
fun EmotePanel(
    picker: EmotePickerState,
    animate: Boolean,
    onPick: (Emote) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var chosenTab by rememberSaveable { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<Pair<Emote, Origin>?>(null) }
    // The tab that was first shown stays: picking an emote makes the recents appear, which must not move the viewer to another tab.
    SideEffect { if (chosenTab == null && !picker.isEmpty) chosenTab = EmotePanelSpec.defaultTab(picker) }
    val tab = EmotePanelSpec.effectiveTab(chosenTab, picker)
    val searching = query.isNotBlank()
    val entries = remember(picker, tab, query) { entriesFor(picker, tab, query) }

    Column(modifier.background(MaterialTheme.colorScheme.surface).testTag(EMOTE_PANEL_TAG)) {
        SearchField(query, { query = it }, Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
        if (!searching && !picker.isEmpty) {
            Tabs(picker, tab) { chosenTab = it }
        }
        preview?.let { (emote, origin) ->
            PreviewStrip(emote, origin, animate, onInsert = { onPick(emote) }, onClose = { preview = null })
        }
        when {
            picker.isEmpty -> PanelMessage(stringResource(R.string.phone_emote_unavailable))

            entries.isEmpty() -> PanelMessage(stringResource(R.string.phone_emote_empty))

            else -> {
                if (!searching && tab == EmoteSource.Twitch.name && picker.access == UserEmoteAccess.NeedsSignIn) {
                    Text(
                        stringResource(R.string.phone_emote_signin_again),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    )
                }
                EmoteGrid(entries, onPick, onPreview = { emote, origin -> preview = emote to origin })
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EmoteGrid(entries: List<Entry>, onPick: (Emote) -> Unit, onPreview: (Emote, Origin) -> Unit) {
    val haptics = LocalHapticFeedback.current
    LazyVerticalGrid(
        columns = GridCells.Adaptive(EmotePanelSpec.CELL),
        modifier = Modifier.fillMaxSize().testTag(EMOTE_GRID_TAG),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
    ) {
        items(entries, key = { it.key }, span = { if (it is Entry.Header) GridItemSpan(maxLineSpan) else GridItemSpan(1) }) { entry ->
            when (entry) {
                is Entry.Header -> SectionHeader(entry)

                is Entry.Cell -> {
                    val locked = entry.origin.section == EmoteSection.Locked
                    val label = stringResource(if (locked) R.string.phone_emote_locked_description else R.string.phone_emote_description, entry.emote.name)
                    EmoteCell(
                        emote = entry.emote,
                        locked = locked,
                        description = label,
                        onClick = { if (!locked) onPick(entry.emote) },
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onPreview(entry.emote, entry.origin)
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EmoteCell(emote: Emote, locked: Boolean, description: String, onClick: () -> Unit, onLongClick: () -> Unit) {
    val density = LocalDensity.current
    val artPx = with(density) { EmotePanelSpec.ART.toPx().roundToInt() }
    Box(
        Modifier.size(EmotePanelSpec.CELL)
            .clip(RoundedCornerShape(CELL_RADIUS))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, role = Role.Button)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(EmotePanelSpec.ART).alpha(if (locked) LOCKED_ALPHA else 1f)) {
            EmoteLayer(emote, animate = false, widthPx = artPx, heightPx = artPx)
        }
        if (locked) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.BottomEnd).padding(3.dp).size(12.dp),
            )
        }
    }
}

@Composable
private fun SectionHeader(header: Entry.Header) {
    Text(
        stringResource(header.label),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 10.dp, bottom = 4.dp),
    )
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit, modifier: Modifier = Modifier) {
    val hint = stringResource(R.string.phone_emote_search_hint)
    BasicTextField(
        value = query,
        onValueChange = { onQuery(it.take(MAX_QUERY)) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = modifier.fillMaxWidth().heightIn(min = EmotePanelSpec.SEARCH_HEIGHT).semantics { if (query.isEmpty()) contentDescription = hint }.testTag(EMOTE_SEARCH_TAG),
        decorationBox = { field ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = EmotePanelSpec.SEARCH_HEIGHT).clip(RoundedCornerShape(EmotePanelSpec.SEARCH_HEIGHT / 2))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    field()
                }
                if (query.isNotEmpty()) {
                    Box(
                        Modifier.size(EmotePanelSpec.SEARCH_HEIGHT).clip(CircleShape)
                            .combinedClickable(onClick = { onQuery("") }, role = Role.Button),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.phone_emote_search_clear), modifier = Modifier.size(18.dp))
                    }
                }
            }
        },
    )
}

@Composable
private fun Tabs(picker: EmotePickerState, selected: String?, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (picker.recent.isNotEmpty()) {
            PanelTab(stringResource(R.string.phone_emote_tab_recent), selected == EmotePanelSpec.RECENT_TAB) { onSelect(EmotePanelSpec.RECENT_TAB) }
        }
        picker.sources.forEach { source ->
            PanelTab(sourceLabel(source), selected == source.name) { onSelect(source.name) }
        }
    }
}

@Composable
private fun PanelTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier.heightIn(min = EmotePanelSpec.TAB_HEIGHT).clip(RoundedCornerShape(CELL_RADIUS))
            .combinedClickable(onClick = onClick, role = Role.Tab)
            .semantics { this.selected = selected }
            .padding(horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = color, maxLines = 1)
        Box(Modifier.padding(top = 4.dp).height(2.dp).fillMaxWidth().background(if (selected) color else androidx.compose.ui.graphics.Color.Transparent, CircleShape))
    }
}

@Composable
private fun PreviewStrip(emote: Emote, origin: Origin, animate: Boolean, onInsert: () -> Unit, onClose: () -> Unit) {
    val density = LocalDensity.current
    val artPx = with(density) { EmotePanelSpec.PREVIEW_ART.toPx().roundToInt() }
    val locked = origin.section == EmoteSection.Locked
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).clip(RoundedCornerShape(PREVIEW_RADIUS))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(EmotePanelSpec.PREVIEW_ART)) {
            EmoteLayer(emote, animate = animate && !LocalReducedMotion.current, widthPx = artPx, heightPx = artPx)
        }
        Column(Modifier.weight(1f)) {
            Text(emote.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val section = when (origin.section) {
                EmoteSection.Channel -> R.string.phone_emote_section_channel
                EmoteSection.Global -> R.string.phone_emote_section_global
                EmoteSection.OtherChannels -> R.string.phone_emote_section_other
                EmoteSection.Locked -> R.string.phone_emote_section_locked
            }
            val animated = if (emote.animated) " · " + stringResource(R.string.phone_emote_animated) else ""
            Text(
                stringResource(R.string.phone_emote_origin, sourceLabel(origin.source), stringResource(section)) + animated,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!locked) {
            Box(
                Modifier.heightIn(min = 40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                    .combinedClickable(onClick = onInsert, role = Role.Button).padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.phone_emote_insert), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
            }
        }
        Box(
            Modifier.size(40.dp).clip(CircleShape).combinedClickable(onClick = onClose, role = Role.Button),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.phone_emote_preview_close), modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun PanelMessage(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** What the viewer sees as a source's name: the providers' own brand names are the same in every language. */
@Composable
@ReadOnlyComposable
private fun sourceLabel(source: EmoteSource): String = when (source) {
    EmoteSource.Twitch -> "Twitch"
    EmoteSource.SevenTv -> "7TV"
    EmoteSource.Bttv -> "BTTV"
    EmoteSource.Ffz -> "FFZ"
    EmoteSource.Other -> stringResource(R.string.phone_emote_source_other)
}

/**
 * The strip above the message box while a `:name` is being typed: the matching emotes as small chips, one tap completes the name.
 * Nothing is drawn (and no room is kept) when there is no match.
 */
@Composable
fun EmoteSuggestionStrip(suggestions: List<Emote>, onPick: (Emote) -> Unit, modifier: Modifier = Modifier) {
    if (suggestions.isEmpty()) return
    val density = LocalDensity.current
    val artPx = with(density) { EmotePanelSpec.SUGGESTION_ART.toPx().roundToInt() }
    val title = stringResource(R.string.phone_emote_suggestions)
    LazyRow(
        modifier.fillMaxWidth().heightIn(min = EmotePanelSpec.SUGGESTION_HEIGHT).semantics { contentDescription = title }.testTag(EMOTE_SUGGESTIONS_TAG),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(suggestions, key = { it.name }) { emote ->
            val label = stringResource(R.string.phone_emote_description, emote.name)
            Row(
                Modifier.heightIn(min = 32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .combinedClickable(onClick = { onPick(emote) }, role = Role.Button).semantics { contentDescription = label }
                    .padding(start = 8.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.size(EmotePanelSpec.SUGGESTION_ART)) { EmoteLayer(emote, animate = false, widthPx = artPx, heightPx = artPx) }
                Text(emote.name, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}

private fun entriesFor(picker: EmotePickerState, tab: String?, query: String): List<Entry> {
    val out = ArrayList<Entry>()
    fun cells(list: List<Emote>, origin: Origin) = list.forEachIndexed { index, emote -> out += Entry.Cell(emote, origin, index) }
    when {
        query.isNotBlank() -> {
            val origins = originsOf(picker)
            picker.search(query).forEachIndexed { index, emote -> out += Entry.Cell(emote, origins[emote.name] ?: Origin(EmoteSource.Other, EmoteSection.Global), index) }
        }

        tab == EmotePanelSpec.RECENT_TAB -> {
            val origins = originsOf(picker)
            picker.recent.forEachIndexed { index, emote -> out += Entry.Cell(emote, origins[emote.name] ?: Origin(EmoteSource.Other, EmoteSection.Global), index) }
        }

        tab != null -> {
            val source = EmoteSource.valueOf(tab)
            val groups = picker.groupsOf(source)
            groups.forEach { group ->
                if (groups.size > 1) out += Entry.Header(sectionLabel(group.section))
                cells(group.emotes, Origin(group.source, group.section))
            }
        }
    }
    return out
}

private fun sectionLabel(section: EmoteSection): Int = when (section) {
    EmoteSection.Channel -> R.string.phone_emote_section_channel
    EmoteSection.Global -> R.string.phone_emote_section_global
    EmoteSection.OtherChannels -> R.string.phone_emote_section_other
    EmoteSection.Locked -> R.string.phone_emote_section_locked
}

/** The list each name is shown from first (the one chat uses); locked lists never win a name. */
private fun originsOf(picker: EmotePickerState): Map<String, Origin> {
    val out = HashMap<String, Origin>()
    picker.groups.filter { it.section != EmoteSection.Locked }.forEach { group -> group.emotes.forEach { out.putIfAbsent(it.name, Origin(group.source, group.section)) } }
    picker.groups.filter { it.section == EmoteSection.Locked }.forEach { group -> group.emotes.forEach { out.putIfAbsent(it.name, Origin(group.source, group.section)) } }
    return out
}

const val EMOTE_PANEL_TAG = "emote-panel"
const val EMOTE_GRID_TAG = "emote-grid"
const val EMOTE_SEARCH_TAG = "emote-search"
const val EMOTE_SUGGESTIONS_TAG = "emote-suggestions"
private const val MAX_QUERY = 40
private const val LOCKED_ALPHA = 0.35f
private val CELL_RADIUS = 8.dp
private val PREVIEW_RADIUS = 16.dp
