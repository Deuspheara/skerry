package com.twitchplayer.phone.feature.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twitchplayer.phone.R
import com.twitchplayer.phone.ui.components.CategoryCard
import com.twitchplayer.phone.ui.components.GridSpacing
import com.twitchplayer.phone.ui.components.HeaderActions
import com.twitchplayer.phone.ui.components.HomeHeader
import com.twitchplayer.phone.ui.components.LoadHost
import com.twitchplayer.phone.ui.components.MessageState
import com.twitchplayer.phone.ui.components.POSTER_RATIO
import com.twitchplayer.phone.ui.components.PosterColumnMin
import com.twitchplayer.phone.ui.components.RefreshBox
import com.twitchplayer.phone.ui.components.ScreenPadding
import com.twitchplayer.phone.ui.components.SkeletonBlock
import com.twitchplayer.phone.ui.components.itemMotion
import com.twitchplayer.tv.core.data.model.Category
import com.twitchplayer.tv.feature.browse.presentation.BrowseEvent
import com.twitchplayer.tv.feature.browse.presentation.BrowseFilter
import com.twitchplayer.tv.feature.browse.presentation.BrowseSort
import com.twitchplayer.tv.feature.browse.presentation.BrowseViewModel
import org.koin.compose.viewmodel.koinViewModel

/** Browse: top categories as a poster wall, with the group filter and the sort order the TV has. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseRoute(
    onCategory: (Category) -> Unit,
    onSignIn: () -> Unit,
    header: HeaderActions,
    modifier: Modifier = Modifier,
    viewModel: BrowseViewModel = koinViewModel(),
) {
    val state by viewModel.presenter.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            HomeHeader(header, scrollBehavior)
        },
    ) { padding ->
        LoadHost(
            state,
            onRetry = { viewModel.presenter.onEvent(BrowseEvent.Retry) },
            onSignIn = onSignIn,
            modifier = Modifier.padding(padding),
            skeleton = { PosterSkeletonGrid() },
        ) { ui ->
            RefreshBox(onRefresh = { viewModel.presenter.onEvent(BrowseEvent.Retry) }) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(PosterColumnMin),
                    contentPadding = ScreenPadding,
                    horizontalArrangement = GridSpacing,
                    verticalArrangement = GridSpacing,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(key = "title", span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                        Text(
                            stringResource(R.string.nav_browse),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.semantics { heading() },
                        )
                    }
                    // The chips scroll away with the wall.
                    item(key = "filters", span = { GridItemSpan(maxLineSpan) }) {
                        FilterRow(
                            filter = ui.filter,
                            sort = ui.sort,
                            onFilter = { viewModel.presenter.onEvent(BrowseEvent.SelectFilter(it)) },
                            onSort = { viewModel.presenter.onEvent(BrowseEvent.ToggleSort) },
                        )
                    }
                    if (ui.categories.isEmpty()) {
                        item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                            MessageState(title = stringResource(R.string.state_no_result), modifier = Modifier.padding(top = 48.dp))
                        }
                    }
                    items(ui.categories, key = { it.id }) { category -> CategoryCard(category, onClick = { onCategory(category) }, modifier = Modifier.itemMotion(this)) }
                }
            }
        }
    }
}

@Composable
private fun FilterRow(filter: BrowseFilter, sort: BrowseSort, onFilter: (BrowseFilter) -> Unit, onSort: () -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(BrowseFilter.entries) { option ->
            FilterChip(selected = option == filter, onClick = { onFilter(option) }, label = { Text(stringResource(option.label())) })
        }
        item {
            AssistChip(
                onClick = onSort,
                label = { Text(stringResource(if (sort == BrowseSort.Popularity) R.string.sort_popularity else R.string.sort_alpha)) },
            )
        }
    }
}

private fun BrowseFilter.label(): Int = when (this) {
    BrowseFilter.All -> R.string.filter_all
    BrowseFilter.Games -> R.string.filter_games
    BrowseFilter.Irl -> R.string.filter_irl
    BrowseFilter.Music -> R.string.filter_music
    BrowseFilter.Creative -> R.string.filter_creative
}

@Composable
internal fun PosterSkeletonGrid() {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(PosterColumnMin),
        contentPadding = ScreenPadding,
        horizontalArrangement = GridSpacing,
        verticalArrangement = GridSpacing,
        userScrollEnabled = false,
        modifier = Modifier.fillMaxSize(),
    ) {
        items(SKELETON_COUNT) { SkeletonBlock(Modifier.fillMaxWidth().aspectRatio(POSTER_RATIO)) }
    }
}

private const val SKELETON_COUNT = 12
