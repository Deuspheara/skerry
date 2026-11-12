package com.twitchplayer.tv.core.di

import com.twitchplayer.tv.core.platform.AppConfig
import com.twitchplayer.tv.core.ui.ChannelKeyBus
import com.twitchplayer.tv.feature.auth.presentation.AuthPresenter
import com.twitchplayer.tv.feature.browse.presentation.BrowsePresenter
import com.twitchplayer.tv.feature.category.presentation.CategoryPresenter
import com.twitchplayer.tv.feature.channel.presentation.ChannelPresenter
import com.twitchplayer.tv.feature.follows.presentation.ContinueWatchingPresenter
import com.twitchplayer.tv.feature.follows.presentation.FollowsPresenter
import com.twitchplayer.tv.feature.home.presentation.HomePresenter
import com.twitchplayer.tv.feature.player.presentation.PlayerPresenter
import com.twitchplayer.tv.feature.search.presentation.SearchPresenter
import com.twitchplayer.tv.feature.settings.presentation.SettingsPresenter
import kotlinx.coroutines.CoroutineScope
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Presenter factories. Presenters are not singletons: the UI layer creates one per screen and owns its lifecycle by
 * passing the [CoroutineScope] it cancels (Android: a holder `ViewModel` passes `viewModelScope`).
 * `koin.get<HomePresenter> { parametersOf(scope) }`; `CategoryPresenter` takes `parametersOf(categoryId, scope)` and
 * `ChannelPresenter` and `PlayerPresenter` `parametersOf(login, scope)`.
 *
 * Besides the bindings of `sharedCoreModule()` these platform bindings must be provided (a test resolves every
 * presenter against fakes of exactly this list):
 *
 * - `DeviceProfile` (the interface language)
 * - `VoiceInput` (speech recognition for search)
 *
 * The player presenter also needs `ChatSource` and `ChatAssetsSource` from `sharedChatModule()`, and `AppConfig` from the
 * platform (the engine a fresh install starts with). `ChannelKeyBus` (hardware channel keys the activity forwards) is a
 * singleton of this module.
 */
fun sharedPresentersModule(): Module = module {
    single { ChannelKeyBus() }
    factory { params -> AuthPresenter(get(), params.get<CoroutineScope>()) }
    factory { params -> HomePresenter(get(), get(), get(), params.get<CoroutineScope>(), foreground = get()) }
    factory { params -> FollowsPresenter(get(), get(), params.get<CoroutineScope>(), snapshots = get(), foreground = get()) }
    factory { params -> ContinueWatchingPresenter(get(), get(), params.get<CoroutineScope>()) }
    factory { params -> BrowsePresenter(get(), get(), params.get<CoroutineScope>(), snapshots = get(), foreground = get()) }
    factory { params -> CategoryPresenter(params.get<String>(), get(), get(), get(), get(), params.get<CoroutineScope>(), snapshots = get(), foreground = get()) }
    factory { params -> ChannelPresenter(params.get<String>(), get(), get(), get(), params.get<CoroutineScope>()) }
    factory { params -> SearchPresenter(get(), get(), get(), params.get<CoroutineScope>()) }
    factory { params -> SettingsPresenter(get(), get(), params.get<CoroutineScope>(), get<AppConfig>().defaultPlayerEngine) }
    factory { params -> PlayerPresenter(params.get<String>(), get(), get(), get(), get(), get(), get(), params.get<CoroutineScope>(), get<AppConfig>().defaultPlayerEngine) }
}
