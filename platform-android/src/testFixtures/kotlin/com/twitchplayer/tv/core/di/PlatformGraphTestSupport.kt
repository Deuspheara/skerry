package com.twitchplayer.tv.core.di

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.twitchplayer.tv.core.data.model.PlayerEngine
import com.twitchplayer.tv.feature.player.data.emotes.EmoteRepository
import com.twitchplayer.tv.feature.player.ui.embed.TwitchPlayerConfig
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.ParameterTypeInjection
import org.koin.test.verify.definition
import org.koin.test.verify.injectedParameters
import kotlin.coroutines.CoroutineContext

/** A config for graph tests: only its shape matters (blank player host = no WebView embed). */
fun testPlatformConfig() = PlatformConfig(twitchClientId = "test-client", defaultPlayerEngine = PlayerEngine.Native, playerHost = "", playerParent = "")

/** Types Koin injects itself or that are passed as parameters, not declared as definitions. */
val koinVerifyExtraTypes = listOf(
    Context::class,
    TwitchPlayerConfig::class, // built inline (and nullable) in coreModule
    PlayerEngine::class, // PlayerPresenter / SettingsPresenter get AppConfig.defaultPlayerEngine passed explicitly
    String::class,
    Long::class,
    Int::class,
    Boolean::class,
    Function0::class,
    Function1::class,
    Function2::class,
    CoroutineContext::class,
    SavedStateHandle::class,
)

/** `EmoteRepository(providers)`: the list is built inline in sharedChatModule. */
@OptIn(KoinExperimentalAPI::class)
fun koinVerifyInjections(): List<ParameterTypeInjection> = injectedParameters(definition<EmoteRepository>(List::class))
