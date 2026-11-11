package com.twitchplayer.tv.feature.follows.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import com.twitchplayer.tv.core.ui.sharedArt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.twitchplayer.tv.R
import com.twitchplayer.tv.core.data.model.ChannelRef
import com.twitchplayer.tv.core.data.model.LiveStream
import com.twitchplayer.tv.core.designsystem.component.Avatar
import com.twitchplayer.tv.core.designsystem.component.BottomFade
import com.twitchplayer.tv.core.designsystem.component.CardRow
import com.twitchplayer.tv.core.designsystem.component.EmptyState
import com.twitchplayer.tv.core.designsystem.component.SectionHeader
import com.twitchplayer.tv.core.designsystem.component.SkeletonGrid
import com.twitchplayer.tv.core.designsystem.component.StreamCard
import com.twitchplayer.tv.core.designsystem.component.TopBar
import com.twitchplayer.tv.core.designsystem.icon.TvIcons
import com.twitchplayer.tv.core.designsystem.theme.TvColors
import com.twitchplayer.tv.core.designsystem.theme.TvDimens
import com.twitchplayer.tv.core.designsystem.theme.TvShapes
import com.twitchplayer.tv.core.designsystem.theme.TvType
import com.twitchplayer.tv.core.ui.LoadGate
import com.twitchplayer.tv.core.ui.TvFocusable
import com.twitchplayer.tv.core.ui.rememberAnchoredSpec
import com.twitchplayer.tv.core.ui.rememberFocusMemory
import com.twitchplayer.tv.feature.follows.presentation.FollowsContent
import com.twitchplayer.tv.feature.follows.presentation.FollowsViewModel
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.itemsIndexed
import com.twitchplayer.tv.core.ui.staggerIn
import com.twitchplayer.tv.core.ui.itemMotion
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.tv.material3.Text
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun FollowsRoute(
    onWatch: (LiveStream) -> Unit,
    onChannel: (ChannelRef) -> Unit,
    viewModel: FollowsViewModel = koinViewModel(),
) {
    val load by viewModel.state.collectAsState()
    Box(Modifier.fillMaxSize()) {
        LoadGate(load, onRetry = viewModel::retry, skeleton = { SkeletonGrid(4, category = false, showHeader = true) }) { content -> FollowsScreen(content, onWatch, onChannel) }
        TopBar(Modifier.align(Alignment.TopStart))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FollowsScreen(content: FollowsContent, onWatch: (LiveStream) -> Unit, onChannel: (ChannelRef) -> Unit) {
    val memory = rememberFocusMemory()
    memory.RequestFocusWhen(ready = true)
    val hasLive = content.live.isNotEmpty()
    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalBringIntoViewSpec provides rememberAnchoredSpec(TvDimens.TopBar + 50.dp)) {
            LazyVerticalGrid(
                state = rememberLazyGridState(),
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxSize().padding(start = 8.dp, end = TvDimens.ContentEnd - 12.dp),
                contentPadding = PaddingValues(start = 16.dp, top = TvDimens.TopBar, bottom = TvDimens.SafeBottom, end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(TvDimens.RowGap),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(key = "h-live", span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                    SectionHeader(TvIcons.Heart, stringResource(R.string.sec_following), Modifier.padding(top = 12.dp), liveCount = content.live.size)
                }
                if (hasLive) {
                    itemsIndexed(content.live, key = { _, it -> it.channelId }, contentType = { _, _ -> "stream" }) { index, stream ->
                        with(memory) {
                            StreamCard(
                                stream, { onWatch(stream) },
                                Modifier.staggerIn(index).itemMotion(this@itemsIndexed).track("l:${stream.channelId}", default = stream === content.live.first()),
                                width = null,
                            )
                        }
                    }
                } else {
                    item(key = "empty", span = { GridItemSpan(maxLineSpan) }, contentType = "empty") {
                        EmptyState(TvIcons.OffAir, stringResource(R.string.state_no_live))
                    }
                }
                if (content.offline.isNotEmpty()) {
                    item(key = "h-off", span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                        SectionHeader(TvIcons.OffAir, stringResource(R.string.sec_offline), Modifier.padding(top = 12.dp))
                    }
                    item(key = "offline", span = { GridItemSpan(maxLineSpan) }, contentType = "offline") {
                        // CardRow pads 16 dp for focus room; pull it back so the first tile sits on the grid keyline.
                        CardRow(Modifier.offset(x = (-16).dp)) {
                            lazyItems(content.offline, key = { it.id }, contentType = { "channel" }) { channel ->
                                with(memory) {
                                    OfflineChannel(channel, { onChannel(channel) }, Modifier.track("o:${channel.id}", default = !hasLive && channel === content.offline.first()))
                                }
                            }
                        }
                    }
                }
            }
        }
        BottomFade(Modifier.align(Alignment.BottomStart))
    }
}

@Composable
private fun OfflineChannel(channel: ChannelRef, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TvFocusable(
        onClick = onClick,
        shape = TvShapes.Card,
        description = stringResource(R.string.cd_offline, channel.name),
        modifier = modifier.width(80.dp),
    ) { focus ->
        Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Dimmed until focused: offline never looks like a live channel.
            Box(Modifier.graphicsLayer { alpha = 0.6f + 0.4f * focus.value }) { Avatar(channel.name, channel.avatarUrl, Modifier.sharedArt("av:${channel.login}"), size = 48.dp, background = TvColors.Base) }
            Text(channel.name, style = TvType.Meta.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), color = TvColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
    }
}
