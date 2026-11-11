package com.twitchplayer.tv.core.di

import com.twitchplayer.tv.core.data.model.PlayerEngine
import com.twitchplayer.tv.core.data.repository.SessionRepository
import com.twitchplayer.tv.core.data.repository.SettingsRepository
import com.twitchplayer.tv.core.data.repository.SnapshotCache
import com.twitchplayer.tv.core.data.repository.TwitchRepository
import com.twitchplayer.tv.core.platform.AppConfig
import com.twitchplayer.tv.core.platform.DeviceProfile
import com.twitchplayer.tv.core.platform.SnapshotStorage
import com.twitchplayer.tv.feature.auth.presentation.AuthPresenter
import com.twitchplayer.tv.feature.browse.presentation.BrowsePresenter
import com.twitchplayer.tv.feature.category.presentation.CategoryPresenter
import com.twitchplayer.tv.feature.channel.presentation.ChannelPresenter
import com.twitchplayer.tv.feature.follows.presentation.FollowsPresenter
import com.twitchplayer.tv.feature.home.presentation.HomePresenter
import com.twitchplayer.tv.feature.search.presentation.SearchPresenter
import com.twitchplayer.tv.feature.search.presentation.VoiceInput
import com.twitchplayer.tv.feature.settings.presentation.SettingsPresenter
import com.twitchplayer.tv.feature.vod.data.VodProgress
import com.twitchplayer.tv.feature.vod.data.VodProgressStore
import com.twitchplayer.tv.testing.FakeRepository
import com.twitchplayer.tv.testing.FakeSession
import com.twitchplayer.tv.testing.FakeSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame

/**
 * What `sharedCoreModule()` provides (here: fakes of the repositories it binds) plus the two platform bindings listed
 * in [sharedPresentersModule]'s KDoc.
 */
private val fakePlatform = module {
    single<TwitchRepository> { FakeRepository() }
    single<SessionRepository> { FakeSession() }
    single<SettingsRepository> { FakeSettings() }
    single<VodProgressStore> {
        object : VodProgressStore {
            override val progress = MutableStateFlow(emptyMap<String, VodProgress>())
            override suspend fun save(vodId: String, positionMs: Long, durationMs: Long) = Unit
            override suspend fun clear(vodId: String) = Unit
        }
    }
    single {
        SnapshotCache(
            object : SnapshotStorage {
                override suspend fun read(name: String): String? = null
                override suspend fun write(name: String, text: String) = Unit
                override suspend fun clear() = Unit
            },
        )
    }
    single { AppConfig(clientId = "cid", defaultPlayerEngine = PlayerEngine.WebView) }
    single<DeviceProfile> {
        object : DeviceProfile {
            override val languageTag = "fr-FR"
        }
    }
    single<VoiceInput> {
        object : VoiceInput {
            override fun isAvailable() = false
            override suspend fun listen(): String? = null
        }
    }
}

class SharedPresentersModuleTest {
    @Test fun everyPresenterResolvesAgainstTheDocumentedPlatformBindings() {
        val koin = startKoin { modules(sharedPresentersModule(), fakePlatform) }.koin
        val scope = CoroutineScope(Job())
        try {
            koin.get<AuthPresenter> { parametersOf(scope) }
            koin.get<HomePresenter> { parametersOf(scope) }
            koin.get<FollowsPresenter> { parametersOf(scope) }
            koin.get<BrowsePresenter> { parametersOf(scope) }
            koin.get<CategoryPresenter> { parametersOf("cat", scope) }
            koin.get<ChannelPresenter> { parametersOf("camille", scope) }
            koin.get<SearchPresenter> { parametersOf(scope) }
            koin.get<SettingsPresenter> { parametersOf(scope) }
        } finally {
            scope.cancel()
            stopKoin()
        }
    }

    @Test fun presentersAreNotSingletonsAndUseTheScopeTheyAreGiven() {
        val koin = startKoin { modules(sharedPresentersModule(), fakePlatform) }.koin
        val scope = CoroutineScope(Job())
        try {
            val a = koin.get<HomePresenter> { parametersOf(scope) }
            val b = koin.get<HomePresenter> { parametersOf(scope) }
            assertNotSame(a, b)
        } finally {
            scope.cancel()
            stopKoin()
        }
    }

    @Test fun theSettingsPresenterStartsOnTheBuildsPlayerEngine() {
        val koin = startKoin { modules(sharedPresentersModule(), fakePlatform) }.koin
        val scope = CoroutineScope(Job())
        try {
            assertEquals(PlayerEngine.WebView, koin.get<SettingsPresenter> { parametersOf(scope) }.state.value.playerEngine)
        } finally {
            scope.cancel()
            stopKoin()
        }
    }

    @Test fun theCategoryPresenterReadsTheDeviceLanguage() {
        val koin = startKoin { modules(sharedPresentersModule(), fakePlatform) }.koin
        val scope = CoroutineScope(Job())
        try {
            assertEquals("fr", koin.get<CategoryPresenter> { parametersOf("cat", scope) }.state.value.language)
        } finally {
            scope.cancel()
            stopKoin()
        }
    }
}
