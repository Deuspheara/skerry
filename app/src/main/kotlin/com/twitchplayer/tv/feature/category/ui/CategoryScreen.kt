package com.twitchplayer.tv.feature.category.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import com.twitchplayer.tv.core.ui.staggerIn
import com.twitchplayer.tv.core.ui.itemMotion
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import com.twitchplayer.tv.core.ui.jumpOnKey
import com.twitchplayer.tv.core.ui.redirectFocusOnExit
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.twitchplayer.tv.R
import com.twitchplayer.tv.core.data.model.LiveStream
import com.twitchplayer.tv.core.designsystem.component.BottomFade
import com.twitchplayer.tv.core.designsystem.component.EmptyState
import com.twitchplayer.tv.core.designsystem.component.SkeletonGrid
import com.twitchplayer.tv.core.designsystem.component.StreamCard
import com.twitchplayer.tv.core.designsystem.component.TvIconButton
import com.twitchplayer.tv.core.designsystem.icon.TvIcons
import com.twitchplayer.tv.core.designsystem.theme.TvColors
import com.twitchplayer.tv.core.designsystem.theme.TvDimens
import com.twitchplayer.tv.core.designsystem.theme.TvType
import com.twitchplayer.tv.core.designsystem.component.Chip
import androidx.compose.ui.draw.clip
import com.twitchplayer.tv.core.designsystem.theme.TvShapes
import com.twitchplayer.tv.core.ui.LoadGate
import com.twitchplayer.tv.core.ui.sharedArt
import com.twitchplayer.tv.core.ui.TvAsyncImage
import com.twitchplayer.tv.core.ui.rememberFocusMemory
import com.twitchplayer.tv.feature.category.presentation.CategoryUiState
import com.twitchplayer.tv.feature.category.presentation.CategoryViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun CategoryRoute(
    categoryId: String,
    name: String,
    artUrl: String,
    onBack: () -> Unit,
    onWatch: (LiveStream) -> Unit,
    viewModel: CategoryViewModel = koinViewModel(key = categoryId) { parametersOf(categoryId) },
) {
    val load by viewModel.state.collectAsState()
    val (language, languageOnly) = viewModel.language.collectAsState().value
    val languageFilter = LanguageFilter(language, languageOnly, viewModel::toggleLanguage)
    Box(Modifier.fillMaxSize()) {
        // The header stays visible in every state so Back (focus fallback) always exists.
        LoadGate(
            load, onRetry = viewModel::retry, modifier = Modifier.padding(top = HEADER_HEIGHT),
            skeleton = { SkeletonGrid(4, category = false, top = 0.dp) },
        ) { ui ->
            CategoryContent("cat:$categoryId", name, artUrl, ui, languageFilter, viewModel::toggleFavorite, onBack, onWatch)
        }
        if (load !is com.twitchplayer.tv.core.presentation.Load.Ready) {
            CategoryHeader("cat:$categoryId", name, artUrl, favorite = false, languageFilter, onBack = onBack, onToggleFavorite = viewModel::toggleFavorite, modifier = Modifier.align(Alignment.TopStart))
        }
    }
}

/** Language chip state: the viewer's language code and whether the list is narrowed to it. */
private class LanguageFilter(val code: String, val only: Boolean, val onToggle: () -> Unit)

private val HEADER_HEIGHT = TvDimens.TopBar + 8.dp + 80.dp + 32.dp

@Composable
private fun CategoryContent(
    artKey: String,
    name: String,
    artUrl: String,
    ui: CategoryUiState,
    languageFilter: LanguageFilter,
    onToggleFavorite: () -> Unit,
    onBack: () -> Unit,
    onWatch: (LiveStream) -> Unit,
) {
    val memory = rememberFocusMemory()
    val back = remember { FocusRequester() }
    memory.RequestFocusWhen(ready = true)
    Box(Modifier.fillMaxSize()) {
        if (ui.streams.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(top = HEADER_HEIGHT), contentAlignment = Alignment.Center) {
                EmptyState(TvIcons.OffAir, stringResource(R.string.state_no_live))
            }
            LaunchedBackFocus(back)
        } else {
            LazyVerticalGrid(
                state = rememberLazyGridState(),
                columns = GridCells.Fixed(4),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 8.dp, end = TvDimens.ContentEnd - 12.dp)
                    .redirectFocusOnExit(FocusDirection.Up) { back }
                    .focusGroup(),
                contentPadding = PaddingValues(start = 16.dp, end = 12.dp, top = HEADER_HEIGHT, bottom = TvDimens.SafeBottom),
                horizontalArrangement = Arrangement.spacedBy(TvDimens.RowGap),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                itemsIndexed(ui.streams, key = { _, it -> it.channelId }, contentType = { _, _ -> "stream" }) { index, stream ->
                    with(memory) {
                        StreamCard(stream, { onWatch(stream) }, Modifier.staggerIn(index).itemMotion(this@itemsIndexed).track("c:${stream.channelId}", default = stream === ui.streams.first()), width = null)
                    }
                }
            }
        }
        CategoryHeader(
            artKey, name, artUrl, ui.favorite, languageFilter, onBack, onToggleFavorite,
            modifier = Modifier
                .align(Alignment.TopStart)
                .then(if (ui.streams.isNotEmpty()) Modifier.jumpOnKey(Key.DirectionDown) { memory.focusRequester } else Modifier)
                .focusGroup(),
            backModifier = Modifier.focusRequester(back),
        )
        BottomFade(Modifier.align(Alignment.BottomStart))
    }
}

@Composable
private fun LaunchedBackFocus(back: FocusRequester) {
    androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { back.requestFocus() } }
}

@Composable
private fun CategoryHeader(
    artKey: String,
    name: String,
    artUrl: String,
    favorite: Boolean,
    languageFilter: LanguageFilter,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    backModifier: Modifier = Modifier,
) {
    Row(
        modifier
            .background(TvColors.Body)
            .padding(start = TvDimens.ContentStart, top = TvDimens.TopBar + 8.dp, end = TvDimens.ContentEnd, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TvIconButton(TvIcons.ChevronLeft, stringResource(R.string.cd_back), onBack, backModifier, size = 40.dp, iconSize = 20.dp, container = TvColors.Alt)
        TvAsyncImage(artUrl, null, 60.dp, 80.dp, Modifier.size(60.dp, 80.dp).sharedArt(artKey).clip(TvShapes.Card))
        Text(name, style = TvType.Headline, color = TvColors.Text, maxLines = 1)
        Box(Modifier.weight(1f))
        // Language: the chip names what is listed now; OK switches between the viewer's language and all languages.
        val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
        val languageName = remember(languageFilter.code, locale) {
            java.util.Locale.forLanguageTag(languageFilter.code).getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
        }
        Chip(
            label = if (languageFilter.only) languageName else stringResource(R.string.filter_all_languages),
            leadingIcon = TvIcons.Globe,
            onClick = languageFilter.onToggle,
            selected = languageFilter.only,
        )
        // A local favourite (Twitch has no category follow API): labelled so it never reads as a Twitch follow.
        Chip(
            label = stringResource(R.string.favorite),
            leadingIcon = if (favorite) TvIcons.HeartFilled else TvIcons.Heart,
            onClick = onToggleFavorite,
            selected = favorite,
        )
    }
}
