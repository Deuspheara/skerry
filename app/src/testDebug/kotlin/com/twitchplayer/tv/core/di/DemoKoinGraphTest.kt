package com.twitchplayer.tv.core.di

import com.twitchplayer.tv.demo.demoModule
import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.test.verify.verify

/** The demo overrides (debug builds only) must still fit the production graph they replace bindings in. */
@OptIn(KoinExperimentalAPI::class)
class DemoKoinGraphTest {
    @Test fun demoOverridesFitTheProductionGraph() {
        module { includes(sharedCoreModule(), coreModule, viewModelModule, demoModule) }.verify(koinVerifyExtraTypes, koinVerifyInjections())
    }
}
