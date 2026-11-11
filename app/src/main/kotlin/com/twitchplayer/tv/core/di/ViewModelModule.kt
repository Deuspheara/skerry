package com.twitchplayer.tv.core.di

import com.twitchplayer.tv.app.AuthViewModel
import com.twitchplayer.tv.core.ui.ChannelKeyBus
import com.twitchplayer.tv.feature.browse.presentation.BrowseViewModel
import com.twitchplayer.tv.feature.category.presentation.CategoryViewModel
import com.twitchplayer.tv.feature.channel.presentation.ChannelViewModel
import com.twitchplayer.tv.feature.follows.presentation.FollowsViewModel
import com.twitchplayer.tv.feature.home.presentation.HomeViewModel
import com.twitchplayer.tv.feature.player.data.ChatSource
import com.twitchplayer.tv.feature.player.data.EventSubChatSource
import com.twitchplayer.tv.feature.player.presentation.PlayerViewModel
import com.twitchplayer.tv.feature.search.data.PlatformVoiceRecognizer
import com.twitchplayer.tv.feature.search.data.VoiceRecognizer
import com.twitchplayer.tv.feature.search.presentation.SearchViewModel
import com.twitchplayer.tv.feature.settings.presentation.SettingsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val viewModelModule = module {
    single { ChannelKeyBus() }
    single<ChatSource> { EventSubChatSource(get()) }
    factory<VoiceRecognizer> { PlatformVoiceRecognizer(androidContext()) }

    viewModel { AuthViewModel(get()) }
    viewModel { HomeViewModel(get(), get(), get()) }
    viewModel { FollowsViewModel(get(), get()) }
    viewModel { BrowseViewModel(get(), get()) }
    viewModel { params -> CategoryViewModel(params.get(), get(), get(), get()) }
    viewModel { SearchViewModel(get(), get(), get()) }
    viewModel { SettingsViewModel(get(), get()) }
    viewModel { params -> ChannelViewModel(params.get(), get(), get(), get()) }
    viewModel { params -> PlayerViewModel(params.get(), get(), get(), get(), get(), get(), get()) }
}
