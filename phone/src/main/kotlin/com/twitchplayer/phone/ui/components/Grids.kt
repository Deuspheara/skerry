package com.twitchplayer.phone.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Stream cards: one column on a phone, more as the window grows (a 600 dp tablet gets two, a foldable or tablet three or four). */
val StreamColumnMin = 320.dp

/** Gap between the rows of a one-column list of compact stream rows (cards in a grid use [GridSpacing]). */
val CompactRowSpacing = 12.dp

/** Category posters. */
val PosterColumnMin = 112.dp

val ScreenPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
val GridSpacing = Arrangement.spacedBy(16.dp)

/** A full-width row inside a grid (a section header, a list row, a horizontal strip). */
fun LazyGridScope.fullSpanItem(key: Any, contentType: Any? = null, content: @Composable LazyGridItemScope.() -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }, contentType = contentType, content = content)
}

fun LazyGridScope.sectionHeader(key: Any, text: String) {
    fullSpanItem(key, contentType = "header") { SectionHeader(text) }
}

/** Vertical gap between rows: tighter between compact stream rows than between the cards of a grid. */
fun streamRowSpacing(layout: StreamLayout) = if (layout.compact) Arrangement.spacedBy(CompactRowSpacing) else GridSpacing
