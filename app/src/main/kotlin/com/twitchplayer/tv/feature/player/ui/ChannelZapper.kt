package com.twitchplayer.tv.feature.player.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.twitchplayer.tv.R
import com.twitchplayer.tv.core.data.model.LiveStream
import com.twitchplayer.tv.core.designsystem.component.ViewerCount
import com.twitchplayer.tv.core.designsystem.icon.TvIcons
import com.twitchplayer.tv.core.designsystem.theme.TvColors
import com.twitchplayer.tv.core.designsystem.theme.TvDimens
import com.twitchplayer.tv.core.designsystem.theme.TvShapes
import com.twitchplayer.tv.core.designsystem.theme.TvType
import com.twitchplayer.tv.core.ui.StaggerGate
import com.twitchplayer.tv.core.ui.TvAsyncImage
import com.twitchplayer.tv.core.ui.staggerIn
import com.twitchplayer.tv.core.ui.TvFocusable
import com.twitchplayer.tv.core.ui.rememberRowSpec
import com.twitchplayer.tv.feature.player.presentation.ZapRow
import com.twitchplayer.tv.feature.player.presentation.ZapRowKind

/**
 * The rows under the player, top to bottom (followed live channels, the stream's category, recommendations). Each row
 * is limited to the video column and fades to the right; only the playing channel carries a marker. OK switches channel
 * while the rows stay open. The first row takes focus on the playing channel (else its first card) when the rows open.
 */
@Composable
fun ZapRows(
    rows: List<ZapRow>,
    playingLogin: String,
    onZap: (LiveStream) -> Unit,
    onRowFocused: (Int) -> Unit,
    onRowHeight: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // No backdrop of its own: PlayerScreen draws one continuous backdrop under the controls and these rows.
    Column(modifier) {
        rows.forEachIndexed { index, row ->
            key(row.kind) {
                ZapRowView(
                    row = row,
                    playingLogin = playingLogin,
                    onZap = onZap,
                    takeFocus = index == 0,
                    modifier = Modifier
                        .onFocusChanged { if (it.hasFocus) onRowFocused(index) }
                        .then(if (index == 0) Modifier.onSizeChanged { onRowHeight(it.height) } else Modifier),
                )
            }
        }
        Spacer(Modifier.height(TvDimens.SafeBottom))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ZapRowView(row: ZapRow, playingLogin: String, onZap: (LiveStream) -> Unit, takeFocus: Boolean, modifier: Modifier) {
    val firstRequester = remember { FocusRequester() }
    val playingIndex = row.streams.indexOfFirst { it.login.equals(playingLogin, ignoreCase = true) }
    val entryIndex = playingIndex.coerceAtLeast(0)
    val firstVisible = (entryIndex - 1).coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = firstVisible)
    // The rows were just opened: the first cards in view enter one after the other.
    val gate = remember { StaggerGate() }
    if (takeFocus) {
        LaunchedEffect(Unit) {
            repeat(6) {
                if (runCatching { firstRequester.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
                androidx.compose.runtime.withFrameNanos { }
            }
        }
    }
    val title = when (val kind = row.kind) {
        ZapRowKind.Following -> stringResource(R.string.sec_following)
        ZapRowKind.ForYou -> stringResource(R.string.sec_for_you)
        is ZapRowKind.Category -> kind.name
    }
    Column(modifier.padding(top = 14.dp)) {
        Text(
            title, style = TvType.Label, color = TvColors.TextSecondary, maxLines = 1,
            modifier = Modifier.padding(start = TvDimens.SafeHorizontal, bottom = 8.dp),
        )
        CompositionLocalProvider(LocalBringIntoViewSpec provides rememberRowSpec(start = 16.dp, peek = 80.dp)) {
            LazyRow(
                state = listState,
                modifier = Modifier.fillMaxWidth().focusRestorer(firstRequester),
                contentPadding = PaddingValues(start = TvDimens.SafeHorizontal, end = 80.dp, top = 4.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(row.streams, key = { _, it -> it.channelId }, contentType = { _, _ -> "zap" }) { i, stream ->
                    val playing = i == playingIndex
                    ZapCard(stream, playing, { onZap(stream) }, Modifier.staggerIn(i - firstVisible, gate).then(if (i == entryIndex) Modifier.focusRequester(firstRequester) else Modifier))
                }
            }
        }
    }
}

@Composable
private fun ZapCard(stream: LiveStream, playing: Boolean, onClick: () -> Unit, modifier: Modifier) {
    TvFocusable(
        onClick = onClick,
        shape = TvShapes.Dialog,
        focusScale = TvDimens.FocusScaleCard,
        description = stream.name + if (playing) ", " + stringResource(R.string.player_playing) else "",
        modifier = modifier.width(TvDimens.ZapCardWidth),
    ) { focus ->
        Column(Modifier.padding(4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(TvShapes.Card)) {
                TvAsyncImage(stream.thumbnailUrl, null, TvDimens.ZapCardWidth, 82.dp, Modifier.matchParentSize())
                if (playing) {
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp).background(TvColors.Black60, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Icon(TvIcons.Equalizer, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().size(width = 0.dp, height = 4.dp).background(TvColors.Purple))
                }
                ViewerCount(stream.viewers, Modifier.align(Alignment.BottomStart).padding(8.dp), overlay = true)
            }
            Text(stream.name, style = TvType.Title, color = TvColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
