package com.twitchplayer.tv.feature.browse.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.twitchplayer.tv.R
import com.twitchplayer.tv.core.data.model.Category
import com.twitchplayer.tv.core.designsystem.component.BottomFade
import com.twitchplayer.tv.core.designsystem.component.CategoryCard
import com.twitchplayer.tv.core.designsystem.component.Chip
import com.twitchplayer.tv.core.designsystem.component.SkeletonGrid
import com.twitchplayer.tv.core.designsystem.component.TopBar
import com.twitchplayer.tv.core.designsystem.icon.TvIcons
import com.twitchplayer.tv.core.designsystem.theme.TvColors
import com.twitchplayer.tv.core.designsystem.theme.TvDimens
import com.twitchplayer.tv.core.ui.LoadGate
import com.twitchplayer.tv.core.ui.itemMotion
import com.twitchplayer.tv.core.ui.jumpOnKey
import com.twitchplayer.tv.core.ui.redirectFocusOnExit
import com.twitchplayer.tv.core.ui.rememberFocusMemory
import com.twitchplayer.tv.core.ui.staggerIn
import com.twitchplayer.tv.feature.browse.presentation.BrowseEvent
import com.twitchplayer.tv.feature.browse.presentation.BrowseFilter
import com.twitchplayer.tv.feature.browse.presentation.BrowseSort
import com.twitchplayer.tv.feature.browse.presentation.BrowseUiState
import com.twitchplayer.tv.feature.browse.presentation.BrowseViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun BrowseRoute(onCategory: (Category) -> Unit, viewModel: BrowseViewModel = koinViewModel()) {
    val presenter = viewModel.presenter
    val load by presenter.state.collectAsState()
    Box(Modifier.fillMaxSize()) {
        LoadGate(load, onRetry = { presenter.onEvent(BrowseEvent.Retry) }, skeleton = { SkeletonGrid(7, category = true, chips = true) }) { ui ->
            BrowseScreen(ui, { presenter.onEvent(BrowseEvent.SelectFilter(it)) }, { presenter.onEvent(BrowseEvent.ToggleSort) }, onCategory)
        }
        TopBar(Modifier.align(Alignment.TopStart))
    }
}

@Composable
private fun BrowseScreen(
    ui: BrowseUiState,
    onFilter: (BrowseFilter) -> Unit,
    onSort: () -> Unit,
    onCategory: (Category) -> Unit,
) {
    val memory = rememberFocusMemory()
    memory.RequestFocusWhen(ready = ui.categories.isNotEmpty())
    val activeChip = remember { FocusRequester() }
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = rememberLazyGridState(),
            columns = GridCells.Fixed(7),
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 8.dp, end = TvDimens.ContentEnd - 12.dp)
                .redirectFocusOnExit(FocusDirection.Up) { activeChip }
                .focusGroup(),
            contentPadding = PaddingValues(start = 16.dp, end = 12.dp, top = TvDimens.TopBar + 8.dp + 36.dp + 32.dp, bottom = TvDimens.SafeBottom),
            horizontalArrangement = Arrangement.spacedBy(TvDimens.RowGap),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            itemsIndexed(ui.categories, key = { _, it -> it.id }, contentType = { _, _ -> "category" }) { index, category ->
                with(memory) {
                    CategoryCard(
                        category,
                        { onCategory(category) },
                        Modifier.staggerIn(index).itemMotion(this@itemsIndexed).track("b:${category.id}", default = category === ui.categories.first()),
                        width = null,
                    )
                }
            }
        }
        // Sticky: composed after the grid so scrolled posters pass under the chips instead of over them (their edges showed
        // between the cards), on a solid band with a short fade.
        Box(
            Modifier.align(Alignment.TopStart).fillMaxWidth()
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(0.82f to TvColors.Body, 1f to androidx.compose.ui.graphics.Color.Transparent))
                .padding(bottom = 14.dp),
        ) {
            Row(
                Modifier
                    .padding(start = TvDimens.ContentStart, top = TvDimens.TopBar + 8.dp, end = TvDimens.ContentEnd)
                    .fillMaxWidth()
                    .jumpOnKey(Key.DirectionDown) { memory.focusRequester }
                    .focusGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BrowseFilter.entries.forEach { filter ->
                    Chip(
                        label = stringResource(filter.labelRes()),
                        selected = ui.filter == filter,
                        onClick = { onFilter(filter) },
                        modifier = if (ui.filter == filter) Modifier.focusRequester(activeChip) else Modifier,
                    )
                }
                Box(Modifier.weight(1f))
                val sortLabel = stringResource(if (ui.sort == BrowseSort.Alphabetical) R.string.sort_alpha else R.string.sort_popularity)
                // Says the current order in words: OK switches it.
                Chip(
                    label = sortLabel,
                    leadingIcon = if (ui.sort == BrowseSort.Alphabetical) TvIcons.SortAlpha else TvIcons.SortPopular,
                    onClick = onSort,
                )
            }
        }
        BottomFade(Modifier.align(Alignment.BottomStart))
    }
}

private fun BrowseFilter.labelRes(): Int = when (this) {
    BrowseFilter.All -> R.string.filter_all
    BrowseFilter.Games -> R.string.filter_games
    BrowseFilter.Irl -> R.string.filter_irl
    BrowseFilter.Music -> R.string.filter_music
    BrowseFilter.Creative -> R.string.filter_creative
}
