package com.twitchplayer.tv.core.designsystem.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.twitchplayer.tv.core.data.model.Category
import com.twitchplayer.tv.core.data.model.LiveStream
import com.twitchplayer.tv.core.designsystem.theme.TvColors
import com.twitchplayer.tv.core.designsystem.theme.TvDimens
import com.twitchplayer.tv.core.ui.FocusMemory
import com.twitchplayer.tv.core.ui.itemMotion
import com.twitchplayer.tv.core.ui.rememberRowSpec
import com.twitchplayer.tv.core.ui.staggerIn

/** Right-edge fade so rows run off-screen softly (a flat overlay: no offscreen layer). */
fun Modifier.fadeEnd(width: androidx.compose.ui.unit.Dp = 32.dp): Modifier = drawWithContent {
    drawContent()
    val w = width.toPx()
    drawRect(
        brush = Brush.horizontalGradient(listOf(Color.Transparent, TvColors.Body), startX = size.width - w, endX = size.width),
        topLeft = androidx.compose.ui.geometry.Offset(size.width - w, 0f),
        size = androidx.compose.ui.geometry.Size(w, size.height),
    )
}

/** Horizontal row with the shared geometry: 16 dp lead-in, 12 dp vertical room for ring + scale. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CardRow(
    modifier: Modifier = Modifier,
    /** Pass one to scroll the row from outside (restoring focus to a card that is not on screen). */
    state: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState(),
    /** Space before the first card; the row itself stays flush with its container, so scrolled cards are clipped at its edge. */
    startPadding: androidx.compose.ui.unit.Dp = 16.dp,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    CompositionLocalProvider(LocalBringIntoViewSpec provides rememberRowSpec()) {
        LazyRow(
            state = state,
            modifier = modifier.fillMaxWidth().fadeEnd().focusRestorer(),
            contentPadding = PaddingValues(start = startPadding, end = TvDimens.ContentEnd + 16.dp, top = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(TvDimens.RowGap),
            content = content,
        )
    }
}

@Composable
fun StreamRow(
    streams: List<LiveStream>,
    keyPrefix: String,
    memory: FocusMemory,
    defaultFirst: Boolean,
    onClick: (LiveStream) -> Unit,
    modifier: Modifier = Modifier,
) {
    CardRow(modifier) {
        itemsIndexed(streams, key = { _, it -> it.channelId }, contentType = { _, _ -> "stream" }) { index, stream ->
            with(memory) {
                StreamCard(
                    stream = stream,
                    onClick = { onClick(stream) },
                    modifier = Modifier.staggerIn(index).itemMotion(this@itemsIndexed).track("$keyPrefix:${stream.channelId}", default = defaultFirst && stream === streams.first()),
                )
            }
        }
    }
}

@Composable
fun CategoryRow(
    categories: List<Category>,
    keyPrefix: String,
    memory: FocusMemory,
    onClick: (Category) -> Unit,
    modifier: Modifier = Modifier,
) {
    CardRow(modifier) {
        itemsIndexed(categories, key = { _, it -> it.id }, contentType = { _, _ -> "category" }) { index, category ->
            with(memory) {
                CategoryCard(category, { onClick(category) }, Modifier.staggerIn(index).itemMotion(this@itemsIndexed).track("$keyPrefix:${category.id}"))
            }
        }
    }
}
