package com.twitchplayer.phone.feature.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twitchplayer.phone.R
import com.twitchplayer.phone.feature.home.CardSkeletonGrid
import com.twitchplayer.phone.navigation.CategoryKey
import com.twitchplayer.phone.ui.components.DetailHeader
import com.twitchplayer.phone.ui.components.FilterPill
import com.twitchplayer.phone.ui.components.GridSpacing
import com.twitchplayer.phone.ui.components.LoadHost
import com.twitchplayer.phone.ui.components.MessageState
import com.twitchplayer.phone.ui.components.POSTER_RATIO
import com.twitchplayer.phone.ui.components.RefreshBox
import com.twitchplayer.phone.ui.components.RemoteImage
import com.twitchplayer.phone.ui.components.ScreenPadding
import com.twitchplayer.phone.ui.components.StreamCard
import com.twitchplayer.phone.ui.components.StreamColumnMin
import com.twitchplayer.phone.ui.components.fullSpanItem
import com.twitchplayer.phone.ui.components.itemMotion
import com.twitchplayer.phone.ui.components.streamLayout
import com.twitchplayer.phone.ui.components.streamRowSpacing
import com.twitchplayer.tv.core.presentation.Load
import com.twitchplayer.tv.core.ui.formatViewers
import com.twitchplayer.tv.feature.category.presentation.CategoryEvent
import com.twitchplayer.tv.feature.category.presentation.CategoryViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** The identity of the page: the box art, the name (up to two lines) and how many people watch it now (from the streams listed). */
@Composable
private fun CategoryHeader(name: String, artUrl: String, viewers: Int, favorite: Boolean, onToggleFavorite: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        if (artUrl.isNotEmpty()) {
            RemoteImage(artUrl, Modifier.width(HEADER_ART_WIDTH).aspectRatio(POSTER_RATIO).clip(RoundedCornerShape(10.dp)))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
            if (viewers > 0) {
                Text(stringResource(R.string.phone_category_watching, remember(viewers) { formatViewers(viewers) }), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // The favourite as a labelled button (a bare heart said nothing): tonal and ticked when it is one.
            val stateText = stringResource(if (favorite) R.string.phone_favorite_on else R.string.phone_favorite_off)
            androidx.compose.material3.FilledTonalButton(
                onClick = onToggleFavorite,
                modifier = Modifier.padding(top = 4.dp).semantics { stateDescription = stateText },
            ) {
                Icon(if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(if (favorite) R.string.phone_category_favourite_on else R.string.phone_category_favourite_add), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

private val HEADER_ART_WIDTH = 72.dp

/** A category: its live streams, narrowed to the viewer's language on request, and the local favourite toggle. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CategoryRoute(
    key: CategoryKey,
    onBack: () -> Unit,
    onWatch: (String) -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CategoryViewModel = koinViewModel(key = "category-${key.id}") { parametersOf(key.id) },
) {
    val state by viewModel.presenter.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val favorite = (state.load as? Load.Ready)?.data?.favorite == true
    val layout = streamLayout()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // The name is in the page's own header below (with the picture): the bar only carries back and the favourite.
            DetailHeader(
                onBack = onBack,
                behavior = scrollBehavior,
                actions = {
                    if (state.load is Load.Ready) {
                        val label = stringResource(R.string.favorite)
                        val stateText = stringResource(if (favorite) R.string.phone_favorite_on else R.string.phone_favorite_off)
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
                            tooltip = { PlainTooltip { Text(label) } },
                            state = rememberTooltipState(),
                        ) {
                            IconToggleButton(
                                checked = favorite,
                                onCheckedChange = { viewModel.presenter.onEvent(CategoryEvent.ToggleFavorite) },
                                modifier = Modifier.semantics { stateDescription = stateText },
                            ) {
                                Icon(
                                    if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    contentDescription = label,
                                    tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                },
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
                    // No extra space under the title: the filter row starts right below it.
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    horizontalArrangement = GridSpacing,
                    verticalArrangement = streamRowSpacing(layout),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    fullSpanItem("header", contentType = "header") {
                        CategoryHeader(key.name, key.artUrl, ui.streams.sumOf { it.viewers }, favorite) { viewModel.presenter.onEvent(CategoryEvent.ToggleFavorite) }
                    }
                    fullSpanItem("language") {
                        // Wraps onto a second line with large text instead of squeezing a chip into a narrow column.
                        FlowRow(
                            Modifier.selectableGroup(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            itemVerticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Two clear choices, the viewer's own language and everything (a chip that is on tells nothing about what it filters).
                            FilterPill(
                                label = stringResource(R.string.filter_all_languages),
                                selected = !state.languageOnly,
                                onClick = { if (state.languageOnly) viewModel.presenter.onEvent(CategoryEvent.ToggleLanguage) },
                            )
                            FilterPill(
                                label = languageLabel(state.language),
                                selected = state.languageOnly,
                                onClick = { if (!state.languageOnly) viewModel.presenter.onEvent(CategoryEvent.ToggleLanguage) },
                            )
                        }
                    }
                    if (ui.streams.isEmpty()) {
                        fullSpanItem("empty") { MessageState(title = stringResource(R.string.state_no_live), modifier = Modifier.padding(top = 48.dp)) }
                    }
                    items(ui.streams, key = { it.channelId }, contentType = { "stream" }) { stream ->
                        StreamCard(stream, onClick = { onWatch(stream.login) }, layout = layout, showLive = false, modifier = Modifier.itemMotion(this))
                    }
                }
            }
        }
    }
}
