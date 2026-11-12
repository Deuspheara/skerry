package com.twitchplayer.tv.core.di

import com.twitchplayer.tv.app.AuthViewModel
import com.twitchplayer.tv.feature.auth.presentation.AuthPresenter
import com.twitchplayer.tv.feature.browse.presentation.BrowsePresenter
import com.twitchplayer.tv.feature.browse.presentation.BrowseViewModel
import com.twitchplayer.tv.feature.category.presentation.CategoryPresenter
import com.twitchplayer.tv.feature.category.presentation.CategoryViewModel
import com.twitchplayer.tv.feature.channel.presentation.ChannelPresenter
import com.twitchplayer.tv.feature.channel.presentation.ChannelViewModel
import com.twitchplayer.tv.feature.feed.presentation.ClipsFeedPresenter
import com.twitchplayer.tv.feature.feed.presentation.ClipsFeedViewModel
import com.twitchplayer.tv.feature.feed.presentation.FeedPresenter
import com.twitchplayer.tv.feature.feed.presentation.FeedViewModel
import com.twitchplayer.tv.feature.follows.presentation.ContinueWatchingPresenter
import com.twitchplayer.tv.feature.follows.presentation.ContinueWatchingViewModel
import com.twitchplayer.tv.feature.follows.presentation.FollowsPresenter
import com.twitchplayer.tv.feature.follows.presentation.FollowsViewModel
import com.twitchplayer.tv.feature.home.presentation.HomePresenter
import com.twitchplayer.tv.feature.home.presentation.HomeViewModel
import com.twitchplayer.tv.feature.player.presentation.PlayerPresenter
import com.twitchplayer.tv.feature.player.presentation.PlayerViewModel
import com.twitchplayer.tv.feature.search.data.PlatformVoiceRecognizer
import com.twitchplayer.tv.feature.search.presentation.SearchPresenter
import com.twitchplayer.tv.feature.search.presentation.SearchViewModel
import com.twitchplayer.tv.feature.search.presentation.VoiceInput
import com.twitchplayer.tv.feature.settings.presentation.SettingsPresenter
import com.twitchplayer.tv.feature.settings.presentation.SettingsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module

val viewModelModule = module {
    factory<VoiceInput> { PlatformVoiceRecognizer(androidContext()) }

    // The presenters live in :shared:presenters (sharedPresentersModule); these holders give them a ViewModel scope.
    viewModel { AuthViewModel { scope -> get<AuthPresenter> { parametersOf(scope) } } }
    viewModel { HomeViewModel { scope -> get<HomePresenter> { parametersOf(scope) } } }
    viewModel { FollowsViewModel { scope -> get<FollowsPresenter> { parametersOf(scope) } } }
    viewModel { ClipsFeedViewModel { scope -> get<ClipsFeedPresenter> { parametersOf(scope) } } }
    viewModel { FeedViewModel { scope -> get<FeedPresenter> { parametersOf(scope) } } }
    viewModel { ContinueWatchingViewModel { scope -> get<ContinueWatchingPresenter> { parametersOf(scope) } } }
    viewModel { BrowseViewModel { scope -> get<BrowsePresenter> { parametersOf(scope) } } }
    viewModel { params ->
        val categoryId = params.get<String>()
        CategoryViewModel { scope -> get<CategoryPresenter> { parametersOf(categoryId, scope) } }
    }
    viewModel { SearchViewModel { scope -> get<SearchPresenter> { parametersOf(scope) } } }
    viewModel { SettingsViewModel { scope -> get<SettingsPresenter> { parametersOf(scope) } } }
    viewModel { params ->
        val login = params.get<String>()
        ChannelViewModel { scope -> get<ChannelPresenter> { parametersOf(login, scope) } }
    }
    viewModel { params ->
        val login = params.get<String>()
        PlayerViewModel { scope -> get<PlayerPresenter> { parametersOf(login, scope) } }
    }
}
