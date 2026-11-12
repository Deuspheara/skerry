package com.twitchplayer.phone.feature.player

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.twitchplayer.tv.feature.player.presentation.EmotePickerState

/** Sizes of the emote picker and the suggestion strip, as plain values a test can pin. */
object EmotePanelSpec {
    val CELL = 44.dp
    val ART = 34.dp
    val PREVIEW_ART = 56.dp
    val SEARCH_HEIGHT = 36.dp
    val TAB_HEIGHT = 40.dp
    val SUGGESTION_HEIGHT = 40.dp
    val SUGGESTION_ART = 24.dp
    val DEFAULT_HEIGHT = 300.dp
    val MIN_HEIGHT = 240.dp
    val MAX_HEIGHT = 380.dp
    const val SUGGESTIONS = 12
    const val RECENT_TAB = "recent"

    /** The tab to show when the viewer has not picked one: their recents when they have some, otherwise the first source with emotes. */
    fun defaultTab(picker: EmotePickerState): String? = if (picker.recent.isNotEmpty()) RECENT_TAB else picker.sources.firstOrNull()?.name

    /** [chosen] when it still exists (a source can disappear when a provider fails on the next channel), otherwise the default. */
    fun effectiveTab(chosen: String?, picker: EmotePickerState): String? = when {
        chosen == RECENT_TAB && picker.recent.isNotEmpty() -> chosen
        chosen != null && chosen != RECENT_TAB && picker.sources.any { it.name == chosen } -> chosen
        else -> defaultTab(picker)
    }

    /** Height of the panel that replaces the keyboard: the keyboard's own height when it was seen, kept within sensible bounds. */
    fun panelHeight(keyboard: Dp?): Dp = (keyboard ?: DEFAULT_HEIGHT).coerceIn(MIN_HEIGHT, MAX_HEIGHT)
}
