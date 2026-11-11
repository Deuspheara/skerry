package com.twitchplayer.phone.feature.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twitchplayer.phone.R
import com.twitchplayer.phone.feature.home.CardSkeletonGrid
import com.twitchplayer.phone.navigation.CategoryKey
import com.twitchplayer.phone.ui.components.GridSpacing
import com.twitchplayer.phone.ui.components.LoadHost
import com.twitchplayer.phone.ui.components.MessageState
import com.twitchplayer.phone.ui.components.RefreshBox
import com.twitchplayer.phone.ui.components.ScreenPadding
import com.twitchplayer.phone.ui.components.StreamCard
import com.twitchplayer.phone.ui.components.StreamColumnMin
import com.twitchplayer.phone.ui.components.fullSpanItem
import com.twitchplayer.phone.ui.components.streamTitleLines
import com.twitchplayer.tv.core.presentation.Load
import com.twitchplayer.tv.feature.category.presentation.CategoryEvent
import com.twitchplayer.tv.feature.category.presentation.CategoryViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** A category: its live streams, narrowed to the viewer's language on request, and the local favourite toggle. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryRoute(
    key: CategoryKey,
    onBack: () -> Unit,
    onChannel: (String) -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CategoryViewModel = koinViewModel(key = "category-${key.id}") { parametersOf(key.id) },
) {
    val state by viewModel.presenter.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val favorite = (state.load as? Load.Ready)?.data?.favorite == true
    val titleLines = streamTitleLines()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(key.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back)) }
                },
                actions = {
                    if (state.load is Load.Ready) {
                        IconToggleButton(checked = favorite, onCheckedChange = { viewModel.presenter.onEvent(CategoryEvent.ToggleFavorite) }) {
                            Icon(
                                if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = stringResource(R.string.favorite),
                                tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        LoadHost(
            state.load,
            onRetry = { viewModel.presenter.onEvent(CategoryEvent.Retry) },
            onSignIn = onSignIn,
            modifier = Modifier.padding(padding),
            skeleton = { CardSkeletonGrid() },
        ) { ui ->
            RefreshBox(onRefresh = { viewModel.presenter.onEvent(CategoryEvent.Retry) }) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(StreamColumnMin),
                    contentPadding = ScreenPadding,
                    horizontalArrangement = GridSpacing,
                    verticalArrangement = GridSpacing,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    fullSpanItem("language") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = state.languageOnly,
                                onClick = { viewModel.presenter.onEvent(CategoryEvent.ToggleLanguage) },
                                label = {
                                    Text(if (state.languageOnly) state.language.uppercase() else stringResource(R.string.filter_all_languages))
                                },
                                leadingIcon = if (state.languageOnly) {
                                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                    if (ui.streams.isEmpty()) {
                        fullSpanItem("empty") { MessageState(title = stringResource(R.string.state_no_live), modifier = Modifier.padding(top = 48.dp)) }
                    }
                    items(ui.streams, key = { it.channelId }, contentType = { "stream" }) { stream ->
                        StreamCard(stream, onClick = { onChannel(stream.login) }, minTitleLines = titleLines)
                    }
                }
            }
        }
    }
}
