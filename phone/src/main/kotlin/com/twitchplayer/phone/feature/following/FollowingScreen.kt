package com.twitchplayer.phone.feature.following

import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twitchplayer.phone.R
import com.twitchplayer.phone.feature.home.CHANNEL_DIVIDER_INSET
import com.twitchplayer.phone.feature.home.CardSkeletonGrid
import com.twitchplayer.phone.ui.components.CategoryCard
import com.twitchplayer.phone.ui.components.ChannelListItem
import com.twitchplayer.phone.ui.components.GridSpacing
import com.twitchplayer.phone.ui.components.HeaderActions
import com.twitchplayer.phone.ui.components.HomeHeader
import com.twitchplayer.phone.ui.components.LoadHost
import com.twitchplayer.phone.ui.components.MessageState
import com.twitchplayer.phone.ui.components.RefreshBox
import com.twitchplayer.phone.ui.components.ScreenPadding
import com.twitchplayer.phone.ui.components.StreamCard
import com.twitchplayer.phone.ui.components.StreamCardSkeleton
import com.twitchplayer.phone.ui.components.StreamColumnMin
import com.twitchplayer.phone.ui.components.fullSpanItem
import com.twitchplayer.phone.ui.components.itemMotion
import com.twitchplayer.phone.ui.components.sectionHeader
import com.twitchplayer.phone.ui.components.streamLayout
import com.twitchplayer.phone.ui.components.streamRowSpacing
import com.twitchplayer.tv.core.data.model.Category
import com.twitchplayer.tv.core.presentation.Load
import com.twitchplayer.tv.feature.follows.presentation.FollowsEvent
import com.twitchplayer.tv.feature.follows.presentation.FollowsViewModel
import com.twitchplayer.tv.feature.home.presentation.HomeContent
import com.twitchplayer.tv.feature.home.presentation.HomeEvent
import com.twitchplayer.tv.feature.home.presentation.HomeViewModel
import org.koin.compose.viewmodel.koinViewModel

/** The channels the viewer follows: the ones that are live first, then the offline ones. Needs a signed-in viewer (the bar only shows the tab then). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowingRoute(
    onWatch: (String) -> Unit,
    onChannel: (String) -> Unit,
    onSignIn: () -> Unit,
    header: HeaderActions,
    modifier: Modifier = Modifier,
    viewModel: FollowsViewModel = koinViewModel(),
) {
    val follows by viewModel.presenter.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { HomeHeader(header, scrollBehavior) },
    ) { padding ->
        LoadHost(follows, onRetry = { viewModel.presenter.onEvent(FollowsEvent.Retry) }, onSignIn = onSignIn, modifier = Modifier.padding(padding), skeleton = { CardSkeletonGrid() }) { content ->
            RefreshBox(onRefresh = { viewModel.presenter.onEvent(FollowsEvent.Retry) }) {
                FollowingList(content.live, content.offline, onWatch, onChannel)
            }
        }
    }
}

@Composable
private fun FollowingList(live: List<com.twitchplayer.tv.core.data.model.LiveStream>, offline: List<com.twitchplayer.tv.core.data.model.ChannelRef>, onWatch: (String) -> Unit, onChannel: (String) -> Unit) {
    if (live.isEmpty() && offline.isEmpty()) {
        MessageState(title = stringResource(R.string.state_no_live), body = stringResource(R.string.phone_following_empty), modifier = Modifier.fillMaxSize())
        return
    }
    val layout = streamLayout()
    val nothingLive = stringResource(R.string.state_no_live)
    val offlineTitle = stringResource(R.string.sec_offline)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(StreamColumnMin),
        contentPadding = ScreenPadding,
        horizontalArrangement = GridSpacing,
        verticalArrangement = streamRowSpacing(layout),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (live.isNotEmpty()) {
            items(live, key = { "live-${it.channelId}" }, contentType = { "stream" }) { stream ->
                StreamCard(stream, onClick = { onWatch(stream.login) }, layout = layout, modifier = Modifier.itemMotion(this))
            }
        } else {
            fullSpanItem("nothing-live") { Text(nothingLive, style = MaterialTheme.typography.bodyLarge) }
        }
        if (offline.isNotEmpty()) {
            sectionHeader("offline-header", offlineTitle)
            items(offline, key = { "offline-${it.id}" }, span = { GridItemSpan(maxLineSpan) }, contentType = { "channel" }) { channel ->
                Column(Modifier.itemMotion(this)) {
                    ChannelListItem(channel, onClick = { onChannel(channel.login) })
                    HorizontalDivider(Modifier.padding(start = CHANNEL_DIVIDER_INSET, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}
