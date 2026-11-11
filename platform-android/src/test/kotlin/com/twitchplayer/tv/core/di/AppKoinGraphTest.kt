package com.twitchplayer.tv.core.di

import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.test.verify.verify

/**
 * Every constructor dependency of every definition must be declared by some module of the production graph
 * ([productionModules], the very list `TwitchPlayerApp` starts Koin with); a missing platform binding fails here, not at launch.
 */
@OptIn(KoinExperimentalAPI::class)
class AppKoinGraphTest {
    @Test fun productionGraphDeclaresEveryDependency() {
        module { includes(productionModules(testPlatformConfig())) }.verify(koinVerifyExtraTypes, koinVerifyInjections())
    }
}
