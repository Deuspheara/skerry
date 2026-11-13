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
import com.twitchplayer.phone.ui.components.HeaderActions
import com.twitchplayer.phone.ui.components.HeroStreamCard
import com.twitchplayer.phone.ui.components.HomeHeader
import com.twitchplayer.phone.ui.components.LoadHost
import com.twitchplayer.phone.ui.components.MessageState
import com.twitchplayer.phone.ui.components.RefreshBox
import com.twitchplayer.phone.ui.components.ScreenPadding
import com.twitchplayer.phone.ui.components.SignedOutCopy
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

/** Home: followed channels that are live, recommendations and top categories ("Following" is its own tab). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeRoute(
    onWatch: (String) -> Unit,
    onCategory: (Category) -> Unit,
    onSignIn: () -> Unit,
    header: HeaderActions,
    modifier: Modifier = Modifier,
    homeViewModel: HomeViewModel = koinViewModel(),
) {
    val home by homeViewModel.presenter.state.collectAsStateWithLifecycle()
    // Tells the system (and the startup benchmark's timeToFullDisplay) when Home shows its data, not just its skeleton.
    ReportDrawnWhen { home !is Load.Loading }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { HomeHeader(header, scrollBehavior) },
    ) { padding ->
        LoadHost(home, onRetry = { homeViewModel.presenter.onEvent(HomeEvent.Retry) }, onSignIn = onSignIn, modifier = Modifier.padding(padding), skeleton = { CardSkeletonGrid() }, signedOut = SignedOutCopy(stringResource(R.string.phone_signedout_home_title), stringResource(R.string.phone_signedout_home_body))) { content ->
            RefreshBox(onRefresh = { homeViewModel.presenter.onEvent(HomeEvent.Retry) }) {
                ForYou(content, onWatch, onCategory)
            }
        }
    }
}

@Composable
private fun ForYou(content: HomeContent, onWatch: (String) -> Unit, onCategory: (Category) -> Unit) {
    if (content.following.isEmpty() && content.forYou.isEmpty() && content.categories.isEmpty()) {
        MessageState(title = stringResource(R.string.state_no_live), body = stringResource(R.string.phone_home_empty), modifier = Modifier.fillMaxSize())
        return
    }
    val layout = streamLayout()
    val followingTitle = stringResource(R.string.phone_home_live_following)
    val forYouTitle = stringResource(R.string.sec_for_you)
    val categoriesTitle = stringResource(R.string.sec_categories)
    // The page leads with one stream (a followed one first), then the lists without it.
    val hero = content.following.firstOrNull() ?: content.forYou.firstOrNull()
    val following = content.following.filter { it.channelId != hero?.channelId }
    val forYou = content.forYou.filter { it.channelId != hero?.channelId }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(StreamColumnMin),
        contentPadding = ScreenPadding,
        horizontalArrangement = GridSpacing,
        verticalArrangement = streamRowSpacing(layout),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (hero != null) {
            fullSpanItem("hero", contentType = "hero") { HeroStreamCard(hero, onClick = { onWatch(hero.login) }, modifier = Modifier.itemMotion(this)) }
        }
        if (following.isNotEmpty()) {
            sectionHeader("following-header", followingTitle)
            items(following, key = { "following-${it.channelId}" }, contentType = { "stream" }) { stream ->
                StreamCard(stream, onClick = { onWatch(stream.login) }, layout = layout, showLive = false, modifier = Modifier.itemMotion(this))
            }
        }
        if (forYou.isNotEmpty()) {
            sectionHeader("for-you-header", forYouTitle)
            items(forYou, key = { "for-you-${it.channelId}" }, contentType = { "stream" }) { stream ->
                StreamCard(stream, onClick = { onWatch(stream.login) }, layout = layout, showLive = false, modifier = Modifier.itemMotion(this))
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
