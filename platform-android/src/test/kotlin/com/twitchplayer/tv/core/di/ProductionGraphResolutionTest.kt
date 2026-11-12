package com.twitchplayer.tv.core.di

import com.twitchplayer.tv.app.AuthViewModel
import com.twitchplayer.tv.core.platform.NumberFormatter
import com.twitchplayer.tv.core.ui.ChannelKeyBus
import com.twitchplayer.tv.feature.browse.presentation.BrowseViewModel
import com.twitchplayer.tv.feature.category.presentation.CategoryViewModel
import com.twitchplayer.tv.feature.channel.presentation.ChannelViewModel
import com.twitchplayer.tv.feature.follows.presentation.ContinueWatchingViewModel
import com.twitchplayer.tv.feature.follows.presentation.FollowsViewModel
import com.twitchplayer.tv.feature.home.presentation.HomeViewModel
import com.twitchplayer.tv.feature.player.presentation.PlayerViewModel
import com.twitchplayer.tv.feature.search.presentation.SearchViewModel
import com.twitchplayer.tv.feature.settings.presentation.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.parameter.parametersOf
import kotlin.test.assertNotNull

/**
 * Starts Koin with the real [productionModules] (the list `TwitchPlayerApp` uses) and constructs every screen's
 * ViewModel, so its presenter is resolved through the module that registers it. `verify` cannot see inside the
 * `viewModel { ... get<XPresenter>() ... }` lambdas: a presenter module missing from the list would only fail at runtime.
 * Only the bindings that need an Android `Context` are replaced by fakes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProductionGraphResolutionTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var datastoreScope: CoroutineScope

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        datastoreScope = CoroutineScope(Job())
    }

    @After fun tearDown() {
        stopKoin()
        datastoreScope.coroutineContext[Job]?.cancel()
        Dispatchers.resetMain()
    }

    @Test fun everyScreenViewModelResolvesItsPresenterFromTheProductionModules() {
        val koin = startKoin {
            allowOverride(true)
            modules(productionModules(testPlatformConfig()) + androidPlatformFakes(tmp.root, datastoreScope))
        }.koin

        assertNotNull(koin.get<AuthViewModel>().presenter)
        assertNotNull(koin.get<HomeViewModel>().presenter)
        assertNotNull(koin.get<FollowsViewModel>().presenter)
        assertNotNull(koin.get<ContinueWatchingViewModel>().presenter)
        assertNotNull(koin.get<BrowseViewModel>().presenter)
        assertNotNull(koin.get<SearchViewModel>().presenter)
        assertNotNull(koin.get<SettingsViewModel>().presenter)
        assertNotNull(koin.get<CategoryViewModel> { parametersOf("509658") }.presenter)
        assertNotNull(koin.get<ChannelViewModel> { parametersOf("camille") }.presenter)
        assertNotNull(koin.get<PlayerViewModel> { parametersOf("") }.presenter)
        assertNotNull(koin.get<PlayerViewModel> { parametersOf("camille") }.presenter)
        // Resolved by koinInject() in the UI (VodScreen), which `verify` cannot see.
        assertNotNull(koin.get<NumberFormatter>())
        assertNotNull(koin.get<ChannelKeyBus>())
    }
}
