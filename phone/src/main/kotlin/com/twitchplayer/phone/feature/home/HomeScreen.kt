package com.twitchplayer.phone.feature.home

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
import androidx.compose.material3.TopAppBar
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
import com.twitchplayer.phone.ui.components.CategoryCard
import com.twitchplayer.phone.ui.components.ChannelListItem
import com.twitchplayer.phone.ui.components.GridSpacing
import com.twitchplayer.phone.ui.components.LoadHost
import com.twitchplayer.phone.ui.components.MessageState
import com.twitchplayer.phone.ui.components.RefreshBox
import com.twitchplayer.phone.ui.components.ScreenPadding
import com.twitchplayer.phone.ui.components.StreamCard
import com.twitchplayer.phone.ui.components.StreamCardSkeleton
import com.twitchplayer.phone.ui.components.StreamColumnMin
import com.twitchplayer.phone.ui.components.fullSpanItem
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

/** Home: "For you" (followed channels that are live, recommendations, top categories) and "Following" (live and offline). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeRoute(
    onChannel: (String) -> Unit,
    onCategory: (Category) -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
    homeViewModel: HomeViewModel = koinViewModel(),
    followsViewModel: FollowsViewModel = koinViewModel(),
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val home by homeViewModel.presenter.state.collectAsStateWithLifecycle()
    val follows by followsViewModel.presenter.state.collectAsStateWithLifecycle()
    // Tells the system (and the startup benchmark's timeToFullDisplay) when Home shows its data, not just its skeleton.
    ReportDrawnWhen { home !is Load.Loading }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_name)) },
                    expandedHeight = TOP_BAR_HEIGHT,
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background, scrolledContainerColor = MaterialTheme.colorScheme.background),
                )
                SecondaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.sec_for_you)) })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.sec_following)) })
                }
            }
        },
    ) { padding ->
        if (tab == 0) {
            LoadHost(home, onRetry = { homeViewModel.presenter.onEvent(HomeEvent.Retry) }, onSignIn = onSignIn, modifier = Modifier.padding(padding), skeleton = { CardSkeletonGrid() }) { content ->
                RefreshBox(onRefresh = { homeViewModel.presenter.onEvent(HomeEvent.Retry) }) {
                    ForYou(content, onChannel, onCategory)
                }
            }
        } else {
            LoadHost(follows, onRetry = { followsViewModel.presenter.onEvent(FollowsEvent.Retry) }, onSignIn = onSignIn, modifier = Modifier.padding(padding), skeleton = { CardSkeletonGrid() }) { content ->
                RefreshBox(onRefresh = { followsViewModel.presenter.onEvent(FollowsEvent.Retry) }) {
                    Following(content.live, content.offline, onChannel)
                }
            }
        }
    }
}

@Composable
private fun ForYou(content: HomeContent, onChannel: (String) -> Unit, onCategory: (Category) -> Unit) {
    if (content.following.isEmpty() && content.forYou.isEmpty() && content.categories.isEmpty()) {
        MessageState(title = stringResource(R.string.state_no_live), body = stringResource(R.string.phone_home_empty), modifier = Modifier.fillMaxSize())
        return
    }
    val layout = streamLayout()
    val followingTitle = stringResource(R.string.phone_home_live_following)
    val forYouTitle = stringResource(R.string.sec_for_you)
    val categoriesTitle = stringResource(R.string.sec_categories)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(StreamColumnMin),
        contentPadding = ScreenPadding,
        horizontalArrangement = GridSpacing,
        verticalArrangement = streamRowSpacing(layout),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (content.following.isNotEmpty()) {
            sectionHeader("following-header", followingTitle)
            items(content.following, key = { "following-${it.channelId}" }, contentType = { "stream" }) { stream ->
                StreamCard(stream, onClick = { onChannel(stream.login) }, layout = layout)
            }
        }
        if (content.forYou.isNotEmpty()) {
            sectionHeader("for-you-header", forYouTitle)
            items(content.forYou, key = { "for-you-${it.channelId}" }, contentType = { "stream" }) { stream ->
                StreamCard(stream, onClick = { onChannel(stream.login) }, layout = layout)
            }
        }
        if (content.categories.isNotEmpty()) {
            sectionHeader("categories-header", categoriesTitle)
            fullSpanItem("categories-row", contentType = "categories") {
                LazyRow(horizontalArrangement = GridSpacing, contentPadding = PaddingValues(end = 16.dp)) {
                    items(content.categories, key = { it.id }) { category ->
                        CategoryCard(category, onClick = { onCategory(category) }, modifier = Modifier.width(CATEGORY_CARD_WIDTH))
                    }
                }
            }
        }
    }
}

@Composable
private fun Following(live: List<com.twitchplayer.tv.core.data.model.LiveStream>, offline: List<com.twitchplayer.tv.core.data.model.ChannelRef>, onChannel: (String) -> Unit) {
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
                StreamCard(stream, onClick = { onChannel(stream.login) }, layout = layout)
            }
        } else {
            fullSpanItem("nothing-live") { Text(nothingLive, style = MaterialTheme.typography.bodyLarge) }
        }
        if (offline.isNotEmpty()) {
            sectionHeader("offline-header", offlineTitle)
            items(offline, key = { "offline-${it.id}" }, span = { GridItemSpan(maxLineSpan) }, contentType = { "channel" }) { channel ->
                Column {
                    ChannelListItem(channel, onClick = { onChannel(channel.login) })
                    HorizontalDivider(Modifier.padding(start = CHANNEL_DIVIDER_INSET, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
internal fun CardSkeletonGrid() {
    val layout = streamLayout()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(StreamColumnMin),
        contentPadding = ScreenPadding,
        horizontalArrangement = GridSpacing,
        verticalArrangement = streamRowSpacing(layout),
        userScrollEnabled = false,
        modifier = Modifier.fillMaxSize(),
    ) {
        items(if (layout.compact) SKELETON_ROWS else SKELETON_COUNT) { StreamCardSkeleton(layout) }
    }
}

private const val SKELETON_COUNT = 6
private const val SKELETON_ROWS = 9
private val CATEGORY_CARD_WIDTH = 120.dp

/** Dividers start under the text, past the avatar. */
internal val CHANNEL_DIVIDER_INSET = 72.dp

/** A little shorter than the Material default (64 dp): the title and the tabs under it read as one header. */
private val TOP_BAR_HEIGHT = 52.dp
