package com.twitchplayer.phone.di

import android.content.Context
import com.twitchplayer.phone.ui.theme.ThemeMode
import com.twitchplayer.phone.ui.theme.ThemePreference
import com.twitchplayer.tv.app.AuthViewModel
import com.twitchplayer.tv.core.di.androidPlatformFakes
import com.twitchplayer.tv.core.di.koinVerifyExtraTypes
import com.twitchplayer.tv.core.di.koinVerifyInjections
import com.twitchplayer.tv.core.di.productionModules
import com.twitchplayer.tv.core.di.testPlatformConfig
import com.twitchplayer.tv.core.ui.ChannelKeyBus
import com.twitchplayer.tv.feature.browse.presentation.BrowseViewModel
import com.twitchplayer.tv.feature.category.presentation.CategoryViewModel
import com.twitchplayer.tv.feature.channel.presentation.ChannelViewModel
import com.twitchplayer.tv.feature.follows.presentation.FollowsViewModel
import com.twitchplayer.tv.feature.home.presentation.HomeViewModel
import com.twitchplayer.tv.feature.search.presentation.SearchViewModel
import com.twitchplayer.tv.feature.settings.presentation.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module
import org.koin.test.verify.verify

/**
 * The phone's real Koin start-up list ([phoneModules], the one `PhoneApp` uses): every definition resolves, and the screens'
 * ViewModels come out of it. Only the bindings that need an Android `Context` are replaced by fakes.
 */
@OptIn(ExperimentalCoroutinesApi::class, KoinExperimentalAPI::class)
class PhoneGraphTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var scope: CoroutineScope

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        scope = CoroutineScope(Job())
    }

    @After fun tearDown() {
        stopKoin()
        scope.coroutineContext[Job]?.cancel()
        Dispatchers.resetMain()
    }

    private val fakeTheme = module {
        single<ThemePreference> {
            object : ThemePreference {
                override val mode: StateFlow<ThemeMode> = MutableStateFlow(ThemeMode.Dark)
                override fun set(mode: ThemeMode) = Unit
            }
        }
    }

    @Test fun theGraphVerifies() {
        // `Context` is among the extra types: the phone's only own definition, the theme preference, needs it.
        module { includes(phoneModules()) }.verify(koinVerifyExtraTypes + Context::class, koinVerifyInjections())
    }

    @Test fun thePhoneListIsTheSharedProductionListPlusItsOwnModule() {
        assertEquals(productionModules(testPlatformConfig()).size + 1, phoneModules().size)
        assertEquals(phoneModule, phoneModules().last())
    }

    @Test fun everyScreenViewModelResolves() {
        val koin = startKoin {
            allowOverride(true)
            modules(phoneModules() + androidPlatformFakes(tmp.root, scope) + fakeTheme)
        }.koin

        assertNotNull(koin.get<AuthViewModel>().presenter)
        assertNotNull(koin.get<HomeViewModel>().presenter)
        assertNotNull(koin.get<FollowsViewModel>().presenter)
        assertNotNull(koin.get<BrowseViewModel>().presenter)
        assertNotNull(koin.get<SearchViewModel>().presenter)
        assertNotNull(koin.get<SettingsViewModel>().presenter)
        assertNotNull(koin.get<CategoryViewModel> { parametersOf("509658") }.presenter)
        assertNotNull(koin.get<ChannelViewModel> { parametersOf("camille") }.presenter)
        assertNotNull(koin.get<ThemePreference>())
        assertNotNull(koin.get<ChannelKeyBus>())
    }
}
