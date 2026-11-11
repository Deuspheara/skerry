package com.twitchplayer.tv.feature.home.ui

import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.twitchplayer.tv.R
import com.twitchplayer.tv.core.data.model.Category
import com.twitchplayer.tv.core.data.model.LiveStream
import com.twitchplayer.tv.core.designsystem.component.BottomFade
import com.twitchplayer.tv.core.designsystem.component.CategoryRow
import com.twitchplayer.tv.core.designsystem.component.EmptyState
import com.twitchplayer.tv.core.designsystem.component.SectionHeader
import com.twitchplayer.tv.core.designsystem.component.SkeletonHome
import com.twitchplayer.tv.core.designsystem.component.StreamRow
import com.twitchplayer.tv.core.designsystem.component.TopBar
import com.twitchplayer.tv.core.designsystem.icon.TvIcons
import com.twitchplayer.tv.core.designsystem.theme.TvDimens
import com.twitchplayer.tv.core.presentation.Load
import com.twitchplayer.tv.core.ui.FocusMemory
import com.twitchplayer.tv.core.ui.LoadGate
import com.twitchplayer.tv.core.ui.rememberAnchoredSpec
import com.twitchplayer.tv.core.ui.rememberFocusMemory
import com.twitchplayer.tv.feature.home.presentation.HomeContent
import com.twitchplayer.tv.feature.home.presentation.HomeViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HomeRoute(
    onWatch: (LiveStream) -> Unit,
    onCategory: (Category) -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val load by viewModel.state.collectAsState()
    // "Fully drawn" for startup metrics (logcat, Macrobenchmark's timeToFullDisplay): the first real Home state is shown.
    ReportDrawnWhen { load !is Load.Loading }
    Box(Modifier.fillMaxSize()) {
        LoadGate(load, onRetry = viewModel::retry, skeleton = { SkeletonHome() }) { content ->
            HomeScreen(content, onWatch, onCategory)
        }
        TopBar(Modifier.align(Alignment.TopStart))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeScreen(
    content: HomeContent,
    onWatch: (LiveStream) -> Unit,
    onCategory: (Category) -> Unit,
) {
    val memory: FocusMemory = rememberFocusMemory()
    val hasFollowing = content.following.isNotEmpty()
    memory.RequestFocusWhen(ready = true)
    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalBringIntoViewSpec provides rememberAnchoredSpec(ANCHOR)) {
            LazyColumn(
                state = rememberLazyListState(),
                modifier = Modifier.fillMaxSize().padding(start = 8.dp),
                contentPadding = PaddingValues(top = TvDimens.TopBar, bottom = TvDimens.SafeBottom),
            ) {
                item(key = "following", contentType = "section") {
                    Section(TvIcons.Heart, stringResource(R.string.sec_following), liveCount = content.following.size) {
                        if (hasFollowing) {
                            StreamRow(content.following, "f", memory, defaultFirst = true, onClick = onWatch)
                        } else {
                            EmptyState(TvIcons.OffAir, stringResource(R.string.state_no_live))
                        }
                    }
                }
                item(key = "forYou", contentType = "section") {
                    Section(TvIcons.Flame, stringResource(R.string.sec_for_you)) {
                        StreamRow(content.forYou, "r", memory, defaultFirst = !hasFollowing, onClick = onWatch)
                    }
                }
                item(key = "categories", contentType = "section") {
                    Section(TvIcons.Grid, stringResource(R.string.sec_categories)) {
                        CategoryRow(content.categories, "c", memory, onCategory)
                    }
                }
            }
        }
        BottomFade(Modifier.align(Alignment.BottomStart))
    }
}

@Composable
private fun Section(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, liveCount: Int? = null, body: @Composable () -> Unit) {
    SectionHeader(icon, description, Modifier.padding(start = 16.dp, top = 12.dp), liveCount = liveCount)
    body()
}

/** Focused row top sits 50 dp below the top bar: icon header (38 dp) + row padding (12 dp). */
private val ANCHOR = TvDimens.TopBar + 50.dp
