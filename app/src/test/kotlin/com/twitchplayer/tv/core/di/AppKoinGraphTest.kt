package com.twitchplayer.tv.core.di

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.twitchplayer.tv.feature.player.data.emotes.EmoteRepository
import com.twitchplayer.tv.feature.player.ui.embed.TwitchPlayerConfig
import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.test.verify.ParameterTypeInjection
import org.koin.test.verify.definition
import org.koin.test.verify.injectedParameters
import org.koin.test.verify.verify
import kotlin.coroutines.CoroutineContext

/** Types Koin injects itself or that are passed as parameters, not declared as definitions. */
internal val koinVerifyExtraTypes = listOf(
    Context::class,
    TwitchPlayerConfig::class, // built inline (and nullable) in coreModule
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

/** `EmoteRepository(providers)`: the list is built inline in coreModule. */
@OptIn(KoinExperimentalAPI::class)
internal fun koinVerifyInjections(): List<ParameterTypeInjection> = injectedParameters(definition<EmoteRepository>(List::class))

/**
 * Every constructor dependency of every definition must be declared by some module of the production graph
 * (`sharedCoreModule` + the Android `coreModule` + the view models); a missing platform binding fails here, not at launch.
 */
@OptIn(KoinExperimentalAPI::class)
class AppKoinGraphTest {
    @Test fun productionGraphDeclaresEveryDependency() {
        module { includes(sharedCoreModule(), coreModule, viewModelModule) }.verify(koinVerifyExtraTypes, koinVerifyInjections())
    }
}
